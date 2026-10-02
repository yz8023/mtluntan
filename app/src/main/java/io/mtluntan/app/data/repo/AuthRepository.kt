package io.mtluntan.app.data.repo

import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.db.entity.SignRecordEntity
import io.mtluntan.app.data.local.AppSettings
import io.mtluntan.app.data.network.CookieRepository
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
                creditsText = old?.creditsText.orEmpty(),
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
        signDays: Int = 0,
    ) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().byUsername(username) ?: return@withContext
        db.accountDao().upsert(
            cur.copy(
                uid = if (uid > 0) uid else cur.uid,
                nickname = nickname.ifEmpty { cur.nickname },
                avatarUrl = avatar.ifEmpty { cur.avatarUrl },
                creditsText = credits.ifEmpty { cur.creditsText },
                signDays = if (signDays > 0) signDays else cur.signDays,
            )
        )
    }

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
