package io.mtluntan.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val username: String,
    val uid: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookieString: String = "",
    val creditsText: String = "",
    val expired: Boolean = false,
    val signDays: Int = 0,
    val lastSignedAt: Long = 0,
    /** 勾选「记住密码」时的密文（AndroidKeyStore AES-GCM 或降级混淆）。 */
    val passwordCipher: String = "",
    /** 是否参与「一键全部签到」/ 定时签到。 */
    val enabled: Boolean = true,
    /** 手动排序用，越小越靠前。 */
    val sortOrder: Int = 0,
    /** 上次签到结果摘要，用于账号卡片直接显示。 */
    val lastSignOk: Boolean = false,
    val lastSignRank: Int = 0,
    val lastSignReward: String = "",
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

/** 每个账号独立的签到记录（账号卡片 / 签到记录页）。 */
@Entity(tableName = "sign_records")
data class SignRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val account: String,
    val date: String,          // yyyy-MM-dd
    val at: Long = System.currentTimeMillis(),
    val ok: Boolean = false,
    val alreadySigned: Boolean = false,
    val rank: Int = 0,
    val reward: String = "",
    val message: String = "",
)

/** 进帖自动解锁的「认领」记录：6 小时内同一 tid 只回一次（失败也不立即释放）。 */
@Entity(tableName = "unlock_claims")
data class UnlockClaimEntity(
    @PrimaryKey val tid: Long,
    val at: Long = System.currentTimeMillis(),
    val ok: Boolean = false,
    val replyText: String = "",
)

/** 黑名单（本地名单 + 帖子页快捷拉黑）。 */
@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey val uid: Long,
    val username: String = "",
    val reason: String = "",
    val at: Long = System.currentTimeMillis(),
)

/** 关注状态本地镜像（服务端仍是权威，这里用于列表秒开与离线可见）。 */
@Entity(tableName = "follow_state")
data class FollowEntity(
    @PrimaryKey val uid: Long,
    val username: String = "",
    val following: Boolean = false,
    val at: Long = System.currentTimeMillis(),
)

/** AI 会话。 */
@Entity(tableName = "ai_sessions")
data class AiSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val account: String = "",
    val tid: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** AI 会话中的一条消息。 */
@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,      // system | user | assistant
    val content: String,
    val at: Long = System.currentTimeMillis(),
)

/** 本地离线保存的帖子（无网可看）。 */
@Entity(tableName = "offline_posts")
data class OfflinePostEntity(
    @PrimaryKey val tid: Long,
    val title: String,
    val boardName: String = "",
    val authorName: String = "",
    val html: String = "",
    val savedAt: Long = System.currentTimeMillis(),
)
