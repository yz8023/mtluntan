package io.mtluntan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.db.entity.DraftEntity
import io.mtluntan.app.data.db.entity.FavoriteEntity
import io.mtluntan.app.data.db.entity.HistoryEntity
import io.mtluntan.app.data.db.entity.ReadMarkEntity
import io.mtluntan.app.data.db.entity.ThreadCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun getAll(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: AccountEntity)

    @Query("DELETE FROM accounts WHERE username = :username")
    suspend fun delete(username: String)

    @Query("UPDATE accounts SET cookieString=:cookie, expired=:expired, avatarUrl=:avatar WHERE username=:username")
    suspend fun updateCookie(username: String, cookie: String, expired: Boolean, avatar: String)
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