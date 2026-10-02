package io.mtluntan.app.data.repo

import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.db.entity.DraftEntity
import io.mtluntan.app.data.db.entity.FavoriteEntity
import io.mtluntan.app.data.db.entity.HistoryEntity
import io.mtluntan.app.data.db.entity.ReadMarkEntity
import io.mtluntan.app.domain.model.Draft
import io.mtluntan.app.domain.model.HistoryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** All device-local persistent data via Room. */
class LocalRepository(private val db: AppDatabase) {

    // ---------- history ----------

    val recentHistory: Flow<List<HistoryRecord>> = db.historyDao().observeRecent(200)
        .map { list -> list.map {
            HistoryRecord(it.id, it.tid, it.title, it.boardName, it.authorName, it.readAt)
        } }

    suspend fun recordVisit(
        tid: Long, title: String, boardName: String, authorName: String,
        lastFloor: Int = 0, totalPages: Int = 1,
    ) = withContext(Dispatchers.IO) {
        val old = db.historyDao().byTid(tid)
        db.historyDao().upsert(
            HistoryEntity(
                id = old?.id ?: 0,
                tid = tid,
                title = title,
                boardName = boardName,
                authorName = authorName,
                readAt = System.currentTimeMillis(),
                lastFloor = lastFloor,
                totalPages = totalPages,
                isRead = true,
            )
        )
    }

    suspend fun removeHistory(id: Long) = withContext(Dispatchers.IO) {
        db.historyDao().deleteById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        db.historyDao().clear()
    }

    // ---------- drafts ----------

    val drafts: Flow<List<Draft>> = db.draftDao().observeAll().map { list -> list.map { it.toModel() } }

    suspend fun saveDraft(d: Draft) = withContext(Dispatchers.IO) {
        val existing = db.draftDao().byKey(d.key())
        db.draftDao().upsert(
            DraftEntity(
                id = existing?.id ?: 0,
                key = d.key(),
                type = d.type,
                tid = d.tid,
                fid = d.fid,
                title = d.title,
                content = d.content,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun draftFor(key: String): Draft? = withContext(Dispatchers.IO) {
        db.draftDao().byKey(key)?.toModel()
    }

    suspend fun deleteDraft(key: String) = withContext(Dispatchers.IO) {
        db.draftDao().deleteByKey(key)
    }

    // ---------- favorites ----------

    val favorites: Flow<List<io.mtluntan.app.domain.model.ThreadItem>> =
        db.favoriteDao().observeAll().map { list -> list.map {
            io.mtluntan.app.domain.model.ThreadItem(
                threadId = it.tid,
                title = it.title,
                boardName = it.boardName,
                authorName = it.authorName,
                postTime = "",
            )
        } }

    suspend fun isFavorite(tid: Long): Boolean = withContext(Dispatchers.IO) {
        db.favoriteDao().byTid(tid) != null
    }

    suspend fun addFavorite(tid: Long, title: String, boardName: String, authorName: String) =
        withContext(Dispatchers.IO) {
            db.favoriteDao().insert(
                FavoriteEntity(tid, title, boardName, authorName)
            )
        }

    suspend fun removeFavorite(tid: Long) = withContext(Dispatchers.IO) {
        db.favoriteDao().delete(tid)
    }

    // ---------- read marks ----------

    suspend fun markRead(tid: Long) = withContext(Dispatchers.IO) {
        db.readMarkDao().insert(ReadMarkEntity(tid))
    }

    suspend fun isRead(tid: Long): Boolean = withContext(Dispatchers.IO) {
        db.readMarkDao().isRead(tid) != null
    }

    // ---------- thread cache ----------

    suspend fun cacheThread(tid: Long, title: String, mainHtml: String, postsHtml: String) =
        withContext(Dispatchers.IO) {
            db.threadCacheDao().insert(
                io.mtluntan.app.data.db.entity.ThreadCacheEntity(tid, title, mainHtml, postsHtml)
            )
        }

    // ---------- 阅读进度 ----------

    suspend fun historyFor(tid: Long): HistoryRecord? = withContext(Dispatchers.IO) {
        db.historyDao().byTid(tid)?.let {
            HistoryRecord(it.id, it.tid, it.title, it.boardName, it.authorName, it.readAt)
        }
    }

    suspend fun progressFor(tid: Long): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val h = db.historyDao().byTid(tid) ?: return@withContext 1 to 1
        h.lastFloor to h.totalPages
    }

    // ---------- 黑名单 ----------

    val blacklist: Flow<List<io.mtluntan.app.data.db.entity.BlacklistEntity>> =
        db.blacklistDao().observeAll()

    suspend fun addBlacklist(uid: Long, name: String, reason: String = "") = withContext(Dispatchers.IO) {
        db.blacklistDao().upsert(io.mtluntan.app.data.db.entity.BlacklistEntity(uid, name, reason))
    }

    suspend fun removeBlacklist(uid: Long) = withContext(Dispatchers.IO) {
        db.blacklistDao().delete(uid)
    }

    suspend fun isBlacklisted(uid: Long): Boolean = withContext(Dispatchers.IO) {
        db.blacklistDao().byUid(uid) != null
    }

    // ---------- 关注状态 ----------

    suspend fun setFollowing(uid: Long, name: String, following: Boolean) = withContext(Dispatchers.IO) {
        db.followDao().upsert(io.mtluntan.app.data.db.entity.FollowEntity(uid, name, following))
    }

    suspend fun following(uid: Long): Boolean? = withContext(Dispatchers.IO) {
        db.followDao().byUid(uid)?.following
    }

    // ---------- 离线帖子 ----------

    val offlinePosts: Flow<List<io.mtluntan.app.data.db.entity.OfflinePostEntity>> =
        db.offlinePostDao().observeAll()

    /** 保存当前帖子为离线页面（正文与评论一起存）。 */
    suspend fun saveOffline(tid: Long, title: String, boardName: String = "", authorName: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                val app = io.mtluntan.app.MTLuntanApp.instanceOrNull()
                    ?: return@withContext "应用未就绪"
                val html = app.forum.threadHtml(tid, 1)
                db.offlinePostDao().upsert(
                    io.mtluntan.app.data.db.entity.OfflinePostEntity(
                        tid = tid,
                        title = title,
                        boardName = boardName,
                        authorName = authorName,
                        html = html,
                    )
                )
                "已保存离线页面（${html.length / 1024} KB）"
            } catch (t: Throwable) {
                "保存失败：${t.message}"
            }
        }

    suspend fun removeOffline(tid: Long) = withContext(Dispatchers.IO) {
        db.offlinePostDao().delete(tid)
    }

    suspend fun offline(tid: Long) = withContext(Dispatchers.IO) {
        db.offlinePostDao().byTid(tid)
    }

    // ---------- 解锁认领 ----------

    fun unlockClaims(): Flow<List<io.mtluntan.app.data.db.entity.UnlockClaimEntity>> =
        db.unlockClaimDao().observeRecent(200)

    suspend fun clearUnlockClaims() = withContext(Dispatchers.IO) {
        db.unlockClaimDao().clear()
    }

}

private fun Draft.key(): String = "$type:${tid}:${fid}"

private fun DraftEntity.toModel(): Draft =
    Draft(id, tid, fid, type, title, content, updatedAt)