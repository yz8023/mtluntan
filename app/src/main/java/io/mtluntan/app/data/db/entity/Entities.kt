package io.mtluntan.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val username: String,
    val uid: Long = 0,
    val avatarUrl: String = "",
    val cookieString: String = "",
    val creditsText: String = "",
    val expired: Boolean = false,
    val signDays: Int = 0,
    val lastSignedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tid: Long,
    val title: String,
    val boardName: String = "",
    val authorName: String = "",
    val readAt: Long = System.currentTimeMillis(),
    val lastFloor: Int = 0,
    val totalPages: Int = 0,
    val isRead: Boolean = false,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val type: String,
    val tid: Long = 0,
    val fid: Long = 0,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val tid: Long,
    val title: String,
    val boardName: String = "",
    val authorName: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "thread_cache")
data class ThreadCacheEntity(
    @PrimaryKey val tid: Long,
    val title: String,
    val mainHtml: String = "",
    val postsHtml: String = "",
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "read_marks")
data class ReadMarkEntity(
    @PrimaryKey val tid: Long,
    val readAt: Long = System.currentTimeMillis(),
)