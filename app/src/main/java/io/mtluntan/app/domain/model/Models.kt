package io.mtluntan.app.domain.model

/** A single thread item shown in guide / forumdisplay / search lists. */
data class ThreadItem(
    val threadId: Long = 0,
    val title: String = "",
    val summary: String = "",
    val authorUid: Long = 0,
    val authorName: String = "",
    val avatarUrl: String = "",
    val boardId: Long = 0,
    val boardName: String = "",
    val replies: Int = 0,
    val views: Int = 0,
    val likes: Int = 0,
    val recommend: Int = 0,
    val postTime: String = "",
    val lastPostTime: String = "",
    val isDigest: Boolean = false,
    val isSticky: Boolean = false,
    val images: List<String> = emptyList(),
    val typeName: String = "",
    val isRead: Boolean = false,
    val hasHiddenContent: Boolean = false,
    val special: Int = 0,          // 0 normal, 1 poll, 2 trade, 3 reward, 4 activity
    val loopType: Int = 0,
    val heat: Int = 0,
    val lastPoster: String = "",
)

/** A forum (板块) category shown on the index / community page. */
data class Forum(
    val id: Long = 0,
    val name: String = "",
    val desc: String = "",
    val iconUrl: String = "",
    val today: Int = 0,
    val threads: Int = 0,
    val posts: Int = 0,
    val subForums: List<String> = emptyList(),
    val children: List<Forum> = emptyList(),
    val categoryId: Long = 0,
    val isCategory: Boolean = false,
)

/** A group / category of forums. */
data class ForumCategory(
    val id: Long = 0,
    val name: String = "",
    val forums: List<Forum> = emptyList(),
)

/** A post inside a thread detail page. */
data class Post(
    val pid: Long = 0,
    val uid: Long = 0,
    val authorName: String = "",
    val authorTitle: String = "",
    val avatarUrl: String = "",
    val postTime: String = "",
    val floor: Int = 0,
    val contentHtml: String = "",
    val contentBbc: String = "",
    val isMainPost: Boolean = false,
    val replyQuote: String = "",
    val images: List<String> = emptyList(),
    val editedBy: String = "",
    val signText: String = "",
    val hideFlag: Boolean = false,
    val comments: Int = 0,
    val likes: Int = 0,
)

/** Thread detail = main post + replies + page metadata. */
data class ThreadDetail(
    val tid: Long = 0,
    val fid: Long = 0,
    val title: String = "",
    val mainPost: Post? = null,
    val posts: List<Post> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val formhash: String = "",
    val likedByCurrent: Boolean = false,
    val favorited: Boolean = false,
    val viewCount: Int = 0,
    val reward: Int = 0,
    val noticeAuthorIds: Set<Long> = emptySet(),
    val loginRequired: Boolean = false,
    val errorMessage: String = "",
    val forumName: String = "",
)

/** A user profile. */
data class UserProfile(
    val uid: Long = 0,
    val username: String = "",
    val avatarUrl: String = "",
    val groupName: String = "",
    val level: String = "",
    val registerTime: String = "",
    val lastVisit: String = "",
    val posts: Int = 0,
    val threads: Int = 0,
    val credits: Int = 0,
    val goldCoin: Int = 0,
    val reputation: Int = 0,
    val onlineTime: String = "",
    val signature: String = "",
    val spaceUrl: String = "",
)

/** Account (login session). */
data class Account(
    val username: String = "",
    val uid: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookieString: String = "",
    val isActive: Boolean = false,
    var expired: Boolean = false,
    var lastCheckIn: String = "",
    var lastCheckInOk: Boolean = false,
    val creditsText: String = "",
    /** 连续签到天数（签到插件回报）。 */
    val signDays: Int = 0,
    /** 参与定时/一键签到。 */
    val enabled: Boolean = true,
    /** 手动排序，越小越靠前。 */
    val sortOrder: Int = 0,
    val lastSignRank: Int = 0,
    val lastSignReward: String = "",
)

/** A notification / message item. */
data class Notice(
    val type: Int = 0, // 0 post reply, 1 pm, 2 system, 3 friend
    val body: String = "",
    val fromAuthor: String = "",
    val url: String = "",
    val time: String = "",
    val isNew: Boolean = false,
)

/** Check-in plugin state. */
data class CheckInState(
    val signedToday: Boolean = false,
    val signDays: Int = 0,
    val goldCoin: Int = 0,
    val message: String = "",
)

/** A draft saved locally while composing. */
data class Draft(
    val id: Long = 0,
    val tid: Long = 0,
    val fid: Long = 0,
    val type: String = "", // newthread | reply | editpost | editreply
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A locally persisted browse history record. */
data class HistoryRecord(
    val id: Long = 0,
    val tid: Long = 0,
    val title: String = "",
    val boardName: String = "",
    val authorName: String = "",
    val readAt: Long = System.currentTimeMillis(),
)

/** Editor page metadata (formhash etc). */
data class EditorMeta(
    val formhash: String = "",
    val posttime: String = "",
    val fid: String = "",
    val tid: String = "",
    val pid: String = "",
    val uploadHash: String = "",
    val loginRequired: Boolean = false,
    val errorMessage: String = "",
    val success: Boolean = false,
)

/** Result of a submit (new thread / reply / edit). */
data class SubmitResult(
    val ok: Boolean = false,
    val error: String = "",
    val tid: Long = 0,
    val pid: Long = 0,
    val contentUrl: String = "",
)

/** 签到结果：账号、天数、排名、奖励一起带回，供记录中心与账号卡片使用。 */
data class SignOutcome(
    val ok: Boolean = false,
    val alreadySigned: Boolean = false,
    val loggedOut: Boolean = false,
    val days: Int = 0,
    val rank: Int = 0,
    val reward: String = "",
    val message: String = "",
)

/** 一条签到记录（账号维度的展示模型）。 */
data class SignRecord(
    val id: Long = 0,
    val account: String = "",
    val date: String = "",
    val at: Long = 0,
    val ok: Boolean = false,
    val alreadySigned: Boolean = false,
    val rank: Int = 0,
    val reward: String = "",
    val message: String = "",
)

/** 私信会话中的一条气泡。 */
data class ChatMessage(
    val id: Long = 0,
    val fromMe: Boolean = false,
    val authorName: String = "",
    val body: String = "",
    val time: String = "",
)

/** 私信会话条目（消息页列表）。 */
data class PmSession(
    val uid: Long = 0,
    val username: String = "",
    val avatarUrl: String = "",
    val lastMessage: String = "",
    val time: String = "",
    val unread: Int = 0,
    val url: String = "",
)

/** 用户空间里的一条动态（主题 / 回复）。 */
data class SpacePost(
    val tid: Long = 0,
    val title: String = "",
    val boardName: String = "",
    val time: String = "",
    val replies: Int = 0,
    val views: Int = 0,
)

/** 积分条目。 */
data class CreditItem(
    val name: String = "",
    val value: String = "",
    val delta: String = "",
    val time: String = "",
)

/** 帖子里的附件。 */
data class Attachment(
    val name: String = "",
    val url: String = "",
    val size: String = "",
    val cost: Int = 0,
)

/** 点赞/打赏用户。 */
data class LikeUser(
    val uid: Long = 0,
    val name: String = "",
    val avatarUrl: String = "",
    val time: String = "",
)

/** 未读角标计数。 */
data class BadgeCounts(
    val notices: Int = 0,
    val pms: Int = 0,
) {
    val total: Int get() = notices + pms
}


/** 账号显示名：昵称优先，其次用户名（昵称拿不到时不要显示空白）。 */
val Account.displayName: String get() = nickname.ifBlank { username }

/** 账号副标题：UID / 签到状态一行说明。 */
fun Account.subtitle(): String = buildString {
    if (uid > 0) append("UID $uid")
    if (signDays > 0) { if (isNotEmpty()) append(" · "); append("连续签到 $signDays 天") }
    if (lastSignReward.isNotBlank()) { if (isNotEmpty()) append(" · "); append(lastSignReward) }
}
