package io.mtluntan.app.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 通用 OpenAI 兼容客户端（Java 版 AiClient 的 Kotlin 版）。
 *
 * 只依赖 `/chat/completions` 这一条最通用的接口，所以 OpenAI、DeepSeek、
 * 通义、Kimi、本地 Ollama / one-api 网关都能直接填 baseUrl 用。
 */
class AiClient {

    data class Msg(val role: String, val content: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // 长回答需要更久
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun chat(messages: List<Msg>, config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!config.ready) {
            return@withContext Result.failure(IllegalStateException("AI 未配置：请先在设置里填 API Key / 模型"))
        }
        val payload = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("temperature", config.temperature)
            addProperty("max_tokens", config.maxTokens)
            addProperty("stream", false)
            add("messages", JsonArray().apply {
                messages.forEach { m ->
                    add(JsonObject().apply {
                        addProperty("role", m.role)
                        addProperty("content", m.content)
                    })
                }
            })
        }
        val request = Request.Builder()
            .url(config.endpoint())
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = when (response.code) {
                        401 -> "API Key 无效"
                        404 -> "地址或模型名不对（检查 baseUrl 是否需要 /v1）"
                        429 -> "触发限流或余额不足"
                        else -> "HTTP ${response.code}"
                    }
                    LogCenter.fail(LogTag.AI, "AI 请求失败", "$hint: ${body.take(200)}")
                    return@withContext Result.failure(IllegalStateException("$hint（${response.code}）"))
                }
                val text = extractContent(body) ?: return@withContext Result.failure(
                    IllegalStateException("响应里没有内容：${body.take(160)}")
                )
                Result.success(text)
            }
        } catch (t: Throwable) {
            LogCenter.fail(LogTag.AI, "AI 网络异常", t.message.orEmpty())
            Result.failure(t)
        }
    }

    /** 兼容 `choices[0].message.content` 与推理模型 `reasoning_content`。 */
    private fun extractContent(body: String): String? = try {
        val root = gson.fromJson(body, JsonObject::class.java)
        val choices = root.getAsJsonArray("choices")
        if (choices != null && choices.size() > 0) {
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val content = message?.get("content")?.takeIf { !it.isJsonNull }?.asString
            if (!content.isNullOrBlank()) content
            else message?.get("reasoning_content")?.takeIf { !it.isJsonNull }?.asString
        } else null
    } catch (t: Throwable) {
        null
    }
}
