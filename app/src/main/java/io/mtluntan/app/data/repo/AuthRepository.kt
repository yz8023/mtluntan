package io.mtluntan.app.data.repo

import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.local.AppSettings
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.data.network.CookieRepository
import io.mtluntan.app.data.network.Net
import io.mtluntan.app.data.network.Site
import io.mtluntan.app.domain.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Account registry + session switching. Cookies are stored per account by
 * [CookieRepository]; the "active" account also drives [SessionHolder].
 */
class AuthRepository(
    private val db: AppDatabase,
    private val cookieRepo: CookieRepository,
    private val settings: AppSettings,
) {

    /** All saved accounts, most recently used first. */
    val accounts: Flow<List<Account>> = db.accountDao().observeAll().map { list ->
        list.map { it.toAccount() }
    }

    /** The currently active account (null → guest). */
    val activeAccount: Flow<String?> = settings.activeAccount

    suspend fun activate(name: String?) = withContext(Dispatchers.IO) {
        cookieRepo.setActiveAccount(name)
        io.mtluntan.app.data.network.SessionHolder.setAccount(name)
        settings.setActiveAccount(name)
    }

    /** After a successful WebView login, persist a full session. */
    suspend fun importSession(username: String, uid: Long, cookieString: String, avatar: String = "") =
        withContext(Dispatchers.IO) {
            cookieRepo.importCookieString(cookieString, username)
            db.accountDao().upsert(
                AccountEntity(
                    username = username,
                    uid = uid,
                    avatarUrl = avatar,
                    cookieString = cookieString,
                    expired = false,
                )
            )
            activate(username)
        }

    /** Updates per-account derived info (avatar, uid, credits, sign days). */
    suspend fun updateInfo(username: String, uid: Long = 0, avatar: String = "", credits: String = "", signDays: Int = 0) =
        withContext(Dispatchers.IO) {
            val cur = db.accountDao().getAll().firstOrNull { it.username == username }
                ?: return@withContext
            db.accountDao().upsert(
                cur.copy(
                    uid = if (uid > 0) uid else cur.uid,
                    avatarUrl = avatar.ifEmpty { cur.avatarUrl },
                    creditsText = credits.ifEmpty { cur.creditsText },
                    signDays = if (signDays > 0) signDays else cur.signDays,
                )
            )
        }

    suspend fun markExpired(username: String) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().getAll().firstOrNull { it.username == username } ?: return@withContext
        db.accountDao().upsert(cur.copy(expired = true))
    }

    suspend fun removeAccount(username: String) = withContext(Dispatchers.IO) {
        db.accountDao().delete(username)
        cookieRepo.clearForAccount(username)
        if (io.mtluntan.app.data.network.SessionHolder.activeAccount.value == username) {
            activate(null)
        }
    }

    suspend fun recordSign(username: String, ok: Boolean) = withContext(Dispatchers.IO) {
        val cur = db.accountDao().getAll().firstOrNull { it.username == username } ?: return@withContext
        db.accountDao().upsert(
            cur.copy(
                signDays = if (ok) cur.signDays + 1 else cur.signDays,
                lastSignedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Checks whether the current active session still has a *_auth style login. */
    fun isLoggedIn(): Boolean {
        val cookies = cookieRepo.loadForRequest(
            "https://bbs.binmt.cc".toHttpUrl(),
            io.mtluntan.app.data.network.SessionHolder.activeAccount.value,
        )
        return cookies.any { it.name.endsWith("_auth") }
    }

    fun avatarFor(uid: Long): String = Site.avatarUrl(uid)

    private fun AccountEntity.toAccount(): Account = Account(
        username = username,
        uid = uid,
        avatarUrl = avatarUrl,
        cookieString = cookieString,
        expired = expired,
        creditsText = creditsText,
        lastCheckIn = if (lastSignedAt > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastSignedAt))
        } else "",
        isActive = username == io.mtluntan.app.data.network.SessionHolder.activeAccount.value,
    )
}