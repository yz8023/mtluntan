package io.mtluntan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.db.entity.AiMessageEntity
import io.mtluntan.app.data.db.entity.AiSessionEntity
import io.mtluntan.app.data.db.entity.BlacklistEntity
import io.mtluntan.app.data.db.entity.DraftEntity
import io.mtluntan.app.data.db.entity.FavoriteEntity
import io.mtluntan.app.data.db.entity.FollowEntity
import io.mtluntan.app.data.db.entity.HistoryEntity
import io.mtluntan.app.data.db.entity.OfflinePostEntity
import io.mtluntan.app.data.db.entity.ReadMarkEntity
import io.mtluntan.app.data.db.entity.SignRecordEntity
import io.mtluntan.app.data.db.entity.ThreadCacheEntity
import io.mtluntan.app.data.db.entity.UnlockClaimEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1")
    suspend fun byUsername(username: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: AccountEntity)

    @Query("DELETE FROM accounts WHERE username = :username")
    suspend fun delete(username: String)

    @Query("UPDATE accounts SET cookieString=:cookie, expired=:expired, avatarUrl=:avatar WHERE username=:username")
    suspend fun updateCookie(username: String, cookie: String, expired: Boolean, avatar: String)

    @Query("UPDATE accounts SET enabled = :enabled WHERE username = :username")
    suspend fun setEnabled(username: String, enabled: Boolean)

    @Query("UPDATE accounts SET sortOrder = :order WHERE username = :username")
    suspend fun setSortOrder(username: String, order: Int)

    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun enabledAccounts(): List<AccountEntity>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE tid = :tid LIMIT 1")
    suspend fun byTid(tid: Long): HistoryEntity?

    @Upsert
    suspend fun upsert(h: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY readAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY readAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HistoryEntity>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("UPDATE history SET isRead=:read WHERE tid=:tid")
    suspend fun markRead(tid: Long, read: Boolean)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE key = :key LIMIT 1")
    suspend fun byKey(key: String): DraftEntity?

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Upsert
    suspend fun upsert(d: DraftEntity)

    @Delete
    suspend fun delete(d: DraftEntity)

    @Query("DELETE FROM drafts WHERE key=:key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE tid=:tid LIMIT 1")
    suspend fun byTid(tid: Long): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE tid=:tid")
    suspend fun delete(tid: Long)
}

@Dao
interface ThreadCacheDao {
    @Query("SELECT * FROM thread_cache WHERE tid=:tid LIMIT 1")
    suspend fun byTid(tid: Long): ThreadCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: ThreadCacheEntity)

    @Query("DELETE FROM thread_cache WHERE cachedAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface ReadMarkDao {
    @Query("SELECT tid FROM read_marks")
    fun allTids(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: ReadMarkEntity)

    @Query("SELECT tid FROM read_marks WHERE tid=:tid LIMIT 1")
    suspend fun isRead(tid: Long): Long?

    @Query("DELETE FROM read_marks WHERE tid=:tid")
    suspend fun delete(tid: Long)
}

@Dao
interface SignRecordDao {
    @Query("SELECT * FROM sign_records ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SignRecordEntity>>

    @Query("SELECT * FROM sign_records WHERE account = :account ORDER BY at DESC LIMIT :limit")
    fun observeForAccount(account: String, limit: Int): Flow<List<SignRecordEntity>>

    @Query("SELECT * FROM sign_records WHERE account = :account AND date = :date ORDER BY at DESC LIMIT 1")
    suspend fun todayForAccount(account: String, date: String): SignRecordEntity?

    @Upsert
    suspend fun upsert(r: SignRecordEntity)

    @Query("DELETE FROM sign_records WHERE account = :account")
    suspend fun clearForAccount(account: String)

    @Query("DELETE FROM sign_records")
    suspend fun clearAll()
}

@Dao
interface UnlockClaimDao {
    @Query("SELECT * FROM unlock_claims WHERE tid = :tid LIMIT 1")
    suspend fun byTid(tid: Long): UnlockClaimEntity?

    @Upsert
    suspend fun upsert(c: UnlockClaimEntity)

    @Query("SELECT * FROM unlock_claims ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UnlockClaimEntity>>

    @Query("DELETE FROM unlock_claims WHERE at < :before")
    suspend fun prune(before: Long)

    @Query("DELETE FROM unlock_claims")
    suspend fun clear()
}

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist ORDER BY at DESC")
    fun observeAll(): Flow<List<BlacklistEntity>>

    @Query("SELECT * FROM blacklist ORDER BY at DESC")
    suspend fun getAll(): List<BlacklistEntity>

    @Query("SELECT * FROM blacklist WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: Long): BlacklistEntity?

    @Upsert
    suspend fun upsert(b: BlacklistEntity)

    @Query("DELETE FROM blacklist WHERE uid = :uid")
    suspend fun delete(uid: Long)

    @Query("DELETE FROM blacklist")
    suspend fun clear()
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM follow_state WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: Long): FollowEntity?

    @Query("SELECT * FROM follow_state")
    fun observeAll(): Flow<List<FollowEntity>>

    @Upsert
    suspend fun upsert(f: FollowEntity)
}

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<AiSessionEntity>>

    @Query("SELECT * FROM ai_sessions WHERE id = :id LIMIT 1")
    suspend fun session(id: Long): AiSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(s: AiSessionEntity): Long

    @Query("UPDATE ai_sessions SET title = :title, updatedAt = :at WHERE id = :id")
    suspend fun renameSession(id: Long, title: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE ai_sessions SET updatedAt = :at WHERE id = :id")
    suspend fun touchSession(id: Long, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC LIMIT :limit")
    suspend fun messages(sessionId: Long, limit: Int = 40): List<AiMessageEntity>

    @Insert
    suspend fun insertMessage(m: AiMessageEntity): Long

    @Query("DELETE FROM ai_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: Long)
}

@Dao
interface OfflinePostDao {
    @Query("SELECT * FROM offline_posts ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<OfflinePostEntity>>

    @Query("SELECT * FROM offline_posts WHERE tid = :tid LIMIT 1")
    suspend fun byTid(tid: Long): OfflinePostEntity?

    @Upsert
    suspend fun upsert(p: OfflinePostEntity)

    @Query("DELETE FROM offline_posts WHERE tid = :tid")
    suspend fun delete(tid: Long)
}
