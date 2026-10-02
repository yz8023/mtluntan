package io.mtluntan.app.ai

import io.mtluntan.app.data.local.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** AI 配置快照（保存进请求时用，避免每处都去读 DataStore）。 */
data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val temperature: Double,
    val maxTokens: Int,
) {
    val ready: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /** 用户可能填 `https://api.x.com` 或 `https://api.x.com/v1`，统一补全到 /chat/completions。 */
    fun endpoint(): String {
        val base = baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }
}

/** AI 配置的读写入口（全部落在 DataStore，与设置页共用同一份）。 */
class AiConfigStore(private val settings: AppSettings) {

    val baseUrl: Flow<String> = settings.aiBaseUrl
    val apiKey: Flow<String> = settings.aiApiKey
    val model: Flow<String> = settings.aiModel
    val systemPrompt: Flow<String> = settings.aiSystemPrompt
    val enabled: Flow<Boolean> = settings.aiSummaryButton
    val dryRun: Flow<Boolean> = settings.aiDryRun

    suspend fun snapshot(): AiConfig {
        val temp = settings.aiTemperature.first().toDoubleOrNull() ?: 0.7
        return AiConfig(
            baseUrl = settings.aiBaseUrl.first(),
            apiKey = settings.aiApiKey.first(),
            model = settings.aiModel.first(),
            systemPrompt = settings.aiSystemPrompt.first(),
            temperature = temp,
            maxTokens = settings.aiMaxTokens.first(),
        )
    }
}
