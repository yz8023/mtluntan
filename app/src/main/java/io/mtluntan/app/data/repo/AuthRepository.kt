package io.mtluntan.app.data.repo

import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.db.entity.SignRecordEntity
import io.mtluntan.app.data.local.AppSettings
import io.mtluntan.app.data.network.CookieRepository
import io.mtluntan.app.data.network.IsolatedClient
import io.mtluntan.app.data.network.SessionHolder
import io.mtluntan.app.data.network.Site
import io.mtluntan.app.domain.model.Account
import io.mtluntan.app.domain.model.SignOutcome
import io.mtluntan.app.domain.model.SignRecord
import io.mtluntan.app.util.KeystoreCrypto
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 账号仓库：多账号注册表 + 会话切换 + 密码托管 + 签到记录。
 *
 * 与 Java 版 AccountManager 对齐的三条铁律：
 *  1. Cookie **整串**快照存取（`xxx_auth` 必须与签发它的 `xxx_saltkey` 配套，
 *     只搬 auth 会得到一个「看起来登录了但一直 403」的假会话）
 *  2. 密码经 [KeystoreCrypto] 加密后只存本机，用于掉线自动重登
 *  3. 切换账号 = 同时切 cookie jar / 前台会话 / DataStore 里的活动账号
 */
class AuthRepository(
    private val db: AppDatabase,
    private val cookieRepo: CookieRepository,
    private val settings: AppSettings,
) {

    val accounts: Flow<List<Account>> = db.accountDao().observeAll().map { list ->
        list.map { it.toAccount() }
    }

    val activeAccount: Flow<String?> = settings.activeAccount

    val signRecords: Flow<List<SignRecord>> = db.signRecordDao().observeRecent(300).map { list ->
        list.map { it.toModel() }
    }

    fun signRecordsFor(account: String): Flow<List<SignRecord>> =
        db.signRecordDao().observeForAccount(account, 100).map { list -> list.map { it.toModel() } }

    // ---------------- 会话切换 ----------------

    suspend fun activate(name: String?) = withContext(Dispatchers.IO) {
        cookieRepo.setActiveAccount(name)
        SessionHolder.setAccount(name)
        settings.setActiveAccount(name)
        LogCenter.log(LogTag.RUN, if (name == null) "切换到游客" else "切换到账号 $name")
        // 切号必须让所有页面重新拉取：消息 / 私信 / 导读 / 我的 都跟着账号走，
        // 否则会看到上一个账号的数据（用户反馈「消息部分与账号不同步」）
        io.mtluntan.app.util.Refresh.bumpGeneration()
    }

    // ---------------- 登录 / 导入 ----------------

    /**
     * WebView 登录成功后导入整串会话。
     * @param password 勾选「记住密码」时传入明文，落库前加密
     */
    suspend fun importSession(
        username: String,
        uid: Long,
        cookieString: String,
        avatar: String = "",
        nickname: String = "",
        group: String = "",
        credits: String = "",
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        cookieRepo.importCookieString(cookieString, username)
        val old = db.accountDao().byUsername(username)
        db.accountDao().upsert(
            AccountEntity(
                username = username,
                uid = if (uid > 0) uid else old?.uid ?: 0,
                nickname = nickname.ifEmpty { old?.nickname.orEmpty() },
                avatarUrl = avatar.ifEmpty { old?.avatarUrl.orEmpty() },
                cookieString = cookieString,
                creditsText = credits.ifEmpty { old?.creditsText.orEmpty() },
                groupName = group.ifEmpty { old?.groupName.orEmpty() },
                expired = false,
                signDays = old?.signDays ?: 0,
                lastSignedAt = old?.lastSignedAt ?: 0,
                passwordCipher = password
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { KeystoreCrypto.encrypt(it) }
                    ?: old?.passwordCipher.orEmpty(),
                enabled = old?.enabled ?: true,
                sortOrder = old?.sortOrder ?: 0,
                lastSignOk = old?.lastSignOk ?: false,
                lastSignRank = old?.lastSignRank ?: 0,
                lastSignReward = old?.lastSignReward.orEmpty(),
                createdAt = old?.createdAt ?: System.currentTimeMillis(),
            )
        )
        activate(username)
        LogCenter.ok(LogTag.RUN, "登录成功：$username")
    }

    /**
     * 登录身份识别（修「账号管理处读取异常 / 加第二个账号把第一个读取了」）。
     *
     * 旧实现从 Cookie **名字**里猜用户名：Discuz 的 `xxx_auth` 前缀是随机盐
     * （形如 `a1b2_2132_auth`），猜出来的「用户名」两个账号很容易撞在一起，
     * 于是第二个账号直接覆盖了第一个 —— 表现就是「添加第二个时读到了第一个」。
     *
     * 正确做法（Java 参考版 LoginBottomSheet.doCookieLogin）：
     * 把 Cookie 先挂到一个**临时会话**上，请求个人页，由服务端告诉我们是谁。
     */
    companion object {
        /** 临时会话的账号 key：登录时先挂 Cookie，再问服务端「我是谁」。 */
        const val PENDING_HANDLE = "__pending_login__"
    }

    /** 把登录产生的整串 Cookie 挂到临时会话，供身份识别使用。 */
    suspend fun stagePendingCookies(cookieString: String) = withContext(Dispatchers.IO) {
        cookieRepo.clearForAccount(PENDING_HANDLE)
        cookieRepo.importCookieString(cookieString, PENDING_HANDLE)
    }

    fun pendingClient(): IsolatedClient = IsolatedClient(cookieRepo, PENDING_HANDLE)

    /** 身份识别失败时用户手填的名字 → 保证也能入库（名字只用于本地标识）。 */
    /**
     * 导入结果。
     *
     * 关键点：**导入失败绝不硬塞一个名字**。
     * 老实现拿「页面标题 / uid_0 / account_时间戳」当用户名，两个账号很容易同名，
     * 于是 username 主键一 REPLACE，第一个账号就被第二个覆盖了 ——
     * 这就是「加一个账号就把上一个删掉」的原因。
     */
    sealed class ImportOutcome {
        /** 服务端身份 + 用户手填的名字都拿不到，需要调用方问用户。 */
        object NeedName : ImportOutcome()
        /** 成功。isNew=false 表示这是已有账号，只刷新了会话。 */
        data class Ok(val username: String, val isNew: Boolean) : ImportOutcome()
    }

    /**
     * 用临时会话里已经确认好的身份入库。
     *
     * @param username 服务端确认的用户名（可能为空 → 用 uid 兜底）
     * @param uid      服务端确认的 uid（0 = 未知）
     */
    suspend fun importStaged(
        cookieString: String,
        username: String,
        uid: Long = 0,
        nickname: String = "",
        avatar: String = "",
        group: String = "",
        credits: String = "",
        password: String? = null,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        val cleanName = io.mtluntan.app.data.parser.UserPagesParser.sanitizeUsername(username)
        val cleanNick = io.mtluntan.app.data.parser.UserPagesParser.sanitizeUsername(nickname)
        // 连 uid 都没有 → 无法确定这是谁，交给上层问用户（绝不生成假名字）
        if (cleanName.isEmpty() && uid <= 0 && cleanNick.isEmpty()) return@withContext ImportOutcome.NeedName

        // 名字优先级：服务端用户名 → uid_<uid> → 用户填的昵称（都为空的情况上面已经拦掉）
        var handle = when {
            cleanName.isNotEmpty() -> cleanName
            uid > 0 -> "uid_$uid"
            else -> cleanNick
        }
        var existing = db.accountDao().byUsername(handle)
        // 同名但 uid 不同 = 撞名的两个账号 → 用 uid 当唯一标识，绝不覆盖
        if (existing != null && uid > 0 && existing.uid > 0 && existing.uid != uid) {
            handle = "uid_$uid"
            existing = db.accountDao().byUsername(handle)
        }
        // 已经有同 uid 的账号 → 认为就是它（比如手动导入过一次）
        if (existing == null && uid > 0) {
            val same = db.accountDao().getAll().firstOrNull { it.uid == uid && it.username != handle }
            if (same != null && cleanName.isNotEmpty() && same.username == cleanName) existing = same
        }

        val isNew = existing == null
        importSession(
            username = handle,
            uid = uid,
            cookieString = cookieString,
            avatar = avatar,
            nickname = cleanNick.ifEmpty { cleanName },
            group = group,
            credits = credits,
            password = password,
        )
        LogCenter.ok(LogTag.RUN, if (isNew) "新增账号：$handle (uid=$uid)" else "刷新账号会话：$handle")
        ImportOutcome.Ok(handle, isNew)
    }

    private fun resolveUsername(username: String, uid: Long): String {
        val trimmed = username.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("__")) return trimmed
        return if (uid > 0) "uid_$uid" else "account_${System.currentTimeMillis()}"
    }

    /** 账号密码直接登录（隔离会话，不污染前台），成功后入库。 */
    suspend fun loginWithPassword(username: String, password: String, keepPassword: Boolean): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            importSession(
                username = username,
                uid = 0,
                cookieString = cookieRepo.exportCookieString(username),
                password = if (keepPassword) password else null,
            )
            true to "已保存账号"
        }

    // ---------------- 导入去重 ----------------

    private val importing = java.util.concurrent.atomic.AtomicBoolean(false)

    /** WebView 每次 onPageFinished 都会触发导入，用一个闸门避免重复入库。 */
    fun isImporting(): Boolean = importing.get()

    fun beginImport(): Boolean = importing.compareAndSet(false, true)

    fun endImport() {
        importing.set(false)
    }

    // ---------------- 密码托管 ----------------

    suspend fun passwordFor(username: String): String = withContext(Dispatchers.IO) {
        val entity = db.accountDao().byUsername(username) ?: return@withContext ""
        KeystoreCrypto.decrypt(entity.passwordCipher)
    }

    suspend fun hasPassword(username: String): Boolean = passwordFor(username).isNotEmpty()

    /**
     * 前台会话自我修复：探测当前账号，掉线且有托管密码就静默重登。
     *
     * Java 版 v4.2 的「自动登录恢复」——用户在刷帖子时看到 403 / 未登录，
     * 不该被弹去登录页，后台自己修好再继续。
     */
    suspend fun ensureSession(): SessionGuard.Status {
        val name = SessionHolder.activeAccount.value
            ?: return SessionGuard.Status(false, message = "当前是游客模式")
        val password = passwordFor(name)
        val status = SessionGuard(cookieRepo).verify(name, password, repair = password.isNotEmpty())
        if (status.loggedIn) {
            if (status.nickname.isNotBlank()) updateInfo(name, nickname = status.nickname)
            // 重登成功后 Cookie 已经变了，同步回存快照，避免下次又用旧 _auth 判断
            if (status.repaired) refreshCookieSnapshot(name)
        }
        return status
    }

    suspend fun setPassword(username: String, password: String) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().byUsername(username) ?: return@withContext
        db.accountDao().upsert(
            cur.copy(passwordCipher = if (password.isEmpty()) "" else KeystoreCrypto.encrypt(password))
        )
    }

    // ---------------- 账号维护 ----------------

    suspend fun updateInfo(
        username: String,
        uid: Long = 0,
        nickname: String = "",
        avatar: String = "",
        credits: String = "",
        group: String = "",
        signDays: Int = 0,
    ) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().byUsername(username) ?: return@withContext
        db.accountDao().upsert(
            cur.copy(
                uid = if (uid > 0) uid else cur.uid,
                nickname = nickname.ifEmpty { cur.nickname },
                avatarUrl = avatar.ifEmpty { cur.avatarUrl },
                creditsText = credits.ifEmpty { cur.creditsText },
                groupName = group.ifEmpty { cur.groupName },
                signDays = if (signDays > 0) signDays else cur.signDays,
            )
        )
    }

    /**
     * 用户手动编辑账号资料（昵称 / UID / 头像），空值就是空值，不做「只增不减」的补全。
     */
    suspend fun updateProfile(
        username: String,
        uid: Long,
        nickname: String,
        avatar: String,
        group: String = "",
    ) = withContext(Dispatchers.IO) {
        db.accountDao().updateProfile(username, uid, nickname.trim(), avatar.trim(), group.trim())
        LogCenter.log(LogTag.RUN, "编辑账号资料：$username")
    }

    /**
     * 这串 Cookie 是不是已经加过的账号？
     *
     * 修「点添加账号又进了已登录那个号」：WebView 里还挂着上一个账号的会话时，
     * 一打开就等于「已登录」，旧逻辑直接把同一个会话又导入一遍 / 认成新账号。
     * 现在先按 `*_auth` 指纹（同一设备同一账号稳定不变）在本地账号里找，
     * 找到就认为是老账号（刷新会话即可），找不到才走「新账号」流程。
     */
    suspend fun accountForCookie(cookieString: String): Account? = withContext(Dispatchers.IO) {
        val all = db.accountDao().getAll()
        if (all.isEmpty()) return@withContext null
        val fp = cookieFingerprint(cookieString)
        if (fp.isNotEmpty()) {
            all.firstOrNull { cookieFingerprint(it.cookieString) == fp }?.let { return@withContext it.toAccount() }
        }
        val norm = normalizeCookie(cookieString)
        all.firstOrNull { normalizeCookie(it.cookieString) == norm }?.toAccount()
    }

    /** 同一账号同一设备的 `*_auth`（+ saltkey）指纹；拿不到就返回空串。 */
    private fun cookieFingerprint(cookieString: String): String {
        val parts = cookieString.split(";").map { it.trim() }.filter { it.contains("=") }
        val auth = parts.firstOrNull { it.substringBefore("=").endsWith("_auth") } ?: return ""
        val salt = parts.firstOrNull { it.substringBefore("=").endsWith("_saltkey") }
        return (auth + "|" + (salt ?: "")).lowercase(Locale.ROOT)
    }

    private fun normalizeCookie(cookieString: String): String =
        cookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .map { it.lowercase(Locale.ROOT) }
            .sorted()
            .joinToString(";")

    /** 回存最新 Cookie 快照（重登成功后调用）。 */
    suspend fun refreshCookieSnapshot(username: String) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().byUsername(username) ?: return@withContext
        val fresh = cookieRepo.exportCookieString(username)
        if (fresh.isEmpty()) return@withContext
        db.accountDao().upsert(cur.copy(cookieString = fresh, expired = false))
    }

    suspend fun markExpired(username: String, expired: Boolean = true) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().byUsername(username) ?: return@withContext
        db.accountDao().upsert(cur.copy(expired = expired))
    }

    suspend fun setEnabled(username: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.accountDao().setEnabled(username, enabled)
    }

    suspend fun move(username: String, delta: Int) = withContext(Dispatchers.IO) {
        val all = db.accountDao().getAll()
        val idx = all.indexOfFirst { it.username == username }
        if (idx < 0) return@withContext
        val target = (idx + delta).coerceIn(0, all.size - 1)
        if (target == idx) return@withContext
        val reordered = all.toMutableList().apply { add(target, removeAt(idx)) }
        reordered.forEachIndexed { i, acc -> db.accountDao().setSortOrder(acc.username, i) }
    }

    suspend fun removeAccount(username: String) = withContext(Dispatchers.IO) {
        db.accountDao().delete(username)
        cookieRepo.clearForAccount(username)
        db.signRecordDao().clearForAccount(username)
        if (SessionHolder.activeAccount.value == username) activate(null)
        LogCenter.log(LogTag.RUN, "已删除账号 $username")
    }

    fun isSessionPresent(): Boolean = cookieRepo.exportCookieString(SessionHolder.activeAccount.value).isNotEmpty()

    fun activeAccountName(): String? = SessionHolder.activeAccount.value

    fun avatarFor(uid: Long): String = Site.avatarUrl(uid)

    // ---------------- 签到记录 ----------------

    suspend fun recordSign(username: String, outcome: SignOutcome) = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val entity = SignRecordEntity(
            account = username,
            date = date,
            ok = outcome.ok || outcome.alreadySigned,
            alreadySigned = outcome.alreadySigned,
            rank = outcome.rank,
            reward = outcome.reward,
            message = outcome.message,
        )
        db.signRecordDao().upsert(entity)
        val cur = db.accountDao().byUsername(username) ?: return@withContext
        db.accountDao().upsert(
            cur.copy(
                signDays = if (outcome.days > 0) outcome.days else cur.signDays,
                lastSignedAt = System.currentTimeMillis(),
                lastSignOk = entity.ok,
                lastSignRank = outcome.rank,
                lastSignReward = outcome.reward,
                expired = !outcome.loggedOut && cur.expired,
            )
        )
    }

    suspend fun clearSignRecords() = withContext(Dispatchers.IO) {
        db.signRecordDao().clearAll()
    }

    // ---------------- 映射 ----------------

    private fun AccountEntity.toAccount(): Account = Account(
        username = username,
        uid = uid,
        avatarUrl = avatarUrl,
        cookieString = cookieString,
        expired = expired,
        creditsText = creditsText,
        groupName = groupName,
        isActive = username == SessionHolder.activeAccount.value,
        nickname = nickname,
        lastCheckIn = if (lastSignedAt > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSignedAt))
        } else "",
        lastCheckInOk = lastSignOk,
        signDays = signDays,
        enabled = enabled,
        sortOrder = sortOrder,
        lastSignRank = lastSignRank,
        lastSignReward = lastSignReward,
    )

    private fun SignRecordEntity.toModel(): SignRecord = SignRecord(
        id = id,
        account = account,
        date = date,
        at = at,
        ok = ok,
        alreadySigned = alreadySigned,
        rank = rank,
        reward = reward,
        message = message,
    )
}
