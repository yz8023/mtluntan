package io.mtluntan.app.ai

import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.db.entity.AiMessageEntity
import io.mtluntan.app.data.db.entity.AiSessionEntity
import io.mtluntan.app.domain.model.Post
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.util.GradientText
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * AI 能力的统一入口：会话存取 + 三种用法（对话 / 帖子总结 / 自动回复文案）。
 * 会话落 Room，换设备前一直在；上下文只取最近 [HISTORY_LIMIT] 轮，
 * 免得一次请求把 token 打爆。
 */
class AiRepository(private val app: MTLuntanApp) {

    companion object {
        private const val HISTORY_LIMIT = 20
    }

    private val client = AiClient()

    // ---------------- 会话 ----------------

    val sessions: Flow<List<AiSessionEntity>> = app.db.aiDao().observeSessions()

    fun messages(sessionId: Long): Flow<List<AiMessageEntity>> = app.db.aiDao().observeMessages(sessionId)

    suspend fun newSession(title: String, tid: Long = 0): Long = withContext(Dispatchers.IO) {
        val id = app.db.aiDao().insertSession(
            AiSessionEntity(
                title = title.ifBlank { "新对话" },
                account = app.auth.activeAccountName().orEmpty(),
                tid = tid,
            )
        )
        LogCenter.log(LogTag.AI, "新建 AI 会话", title)
        id
    }

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        app.db.aiDao().deleteMessages(id)
        app.db.aiDao().deleteSession(id)
    }

    suspend fun renameSession(id: Long, title: String) = withContext(Dispatchers.IO) {
        app.db.aiDao().renameSession(id, title)
    }

    // ---------------- 对话 ----------------

    suspend fun ask(
        sessionId: Long,
        userText: String,
        extraSystem: String = "",
        persistUser: Boolean = true,
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = app.aiConfig.snapshot()
        if (persistUser) {
            app.db.aiDao().insertMessage(AiMessageEntity(sessionId = sessionId, role = "user", content = userText))
        }
        val history = app.db.aiDao().messages(sessionId, HISTORY_LIMIT)

        val messages = mutableListOf<AiClient.Msg>()
        val system = buildString {
            append(config.systemPrompt)
            if (extraSystem.isNotBlank()) append("\n\n").append(extraSystem)
        }
        if (system.isNotBlank()) messages += AiClient.Msg("system", system)
        history.forEach { messages += AiClient.Msg(it.role, it.content) }
        if (history.none { it.role == "user" && it.content == userText }) {
            messages += AiClient.Msg("user", userText)
        }

        val result = client.chat(messages, config)
        result.onSuccess { reply ->
            app.db.aiDao().insertMessage(AiMessageEntity(sessionId = sessionId, role = "assistant", content = reply))
            app.db.aiDao().touchSession(sessionId)
            // 首轮回答后把问题当标题（会话列表可读性）
            val session = app.db.aiDao().session(sessionId)
            if (session != null && session.title == "新对话") {
                app.db.aiDao().renameSession(sessionId, userText.take(18))
            }
        }
        result
    }

    // ---------------- 帖子总结 ----------------

    suspend fun summarizeThread(detail: ThreadDetail): Result<String> = withContext(Dispatchers.IO) {
        val config = app.aiConfig.snapshot()
        if (!config.ready) return@withContext Result.failure(IllegalStateException("AI 未配置"))
        val text = threadToText(detail)
        if (text.isBlank()) return@withContext Result.failure(IllegalStateException("帖子内容为空"))
        val prompt = """
            请用中文总结下面这个论坛帖子：
            1. 主题在讲什么（一句话）
            2. 关键信息 / 结论 / 步骤（要点列出）
            3. 有没有值得注意的坑或风险
            4. 如果帖子里有代码或命令，指出它的作用

            === 帖子标题 ===
            ${detail.title}

            === 帖子内容 ===
            $text
        """.trimIndent()
        client.chat(
            listOf(
                AiClient.Msg("system", config.systemPrompt),
                AiClient.Msg("user", prompt),
            ),
            config,
        )
    }

    // ---------------- 自动回复文案 ----------------

    suspend fun generateReply(threadContext: String, template: String, extraPrompt: String): String =
        withContext(Dispatchers.IO) {
            val config = app.aiConfig.snapshot()
            if (!config.ready) return@withContext template
            val prompt = buildString {
                appendLine("这是一个需要回复才能看到隐藏内容的论坛帖子。请写一条**简短、自然、不重复**的中文回复（30 字以内）。")
                appendLine("要求：像真实论坛用户，不要客套堆砌，不要表情符号，不要提到 AI。")
                if (extraPrompt.isNotBlank()) appendLine("额外要求：$extraPrompt")
                appendLine()
                appendLine("=== 帖子信息 ===")
                appendLine(threadContext.take(1500))
            }
            client.chat(
                listOf(
                    AiClient.Msg("system", config.systemPrompt),
                    AiClient.Msg("user", prompt),
                ),
                config,
            ).getOrNull()?.trim()?.take(120)?.takeIf { it.isNotBlank() } ?: template
        }

    private fun threadToText(detail: ThreadDetail): String {
        val sb = StringBuilder()
        detail.mainPost?.let { sb.append(postText(it)) }
        detail.posts.take(30).forEach { post ->
            sb.append("\n--- 楼层 ${post.floor} ${post.authorName} ---\n")
            sb.append(postText(post))
        }
        return sb.toString().take(12000)
    }

    private fun postText(post: Post): String {
        val raw = post.contentBbc.ifBlank { post.contentHtml }
        return GradientText.strip(raw)
            .replace(Regex("\\[/?[a-z0-9=,#]+\\]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
