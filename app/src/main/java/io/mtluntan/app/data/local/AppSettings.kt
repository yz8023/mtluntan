package io.mtluntan.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 全部应用设置项。分组与设置页一一对应：
 *  账号与自动化 / 外观与阅读 / 网络与下载 / AI 与自动化 / 记录中心 / 关于
 */
class AppSettings(private val context: Context) {

    companion object {
        // ---- 外观 ----
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")          // null = 跟随系统
        val KEY_THEME_COLOR = intPreferencesKey("theme_color")          // 0..11 + 12=自定义
        /** 1.0 的 6 套配色 → 12 色板的就近映射（0 MT蓝 1 森野绿 2 暖阳橙 3 葡萄紫 4 樱花粉 5 青碧）。 */
        val LEGACY_PALETTE_MAP = intArrayOf(0, 6, 2, 5, 4, 10)
        /** 1.0 的 6 套配色是否已经映射到新的 12 色调色板。 */
        val KEY_PALETTE_MIGRATED = booleanPreferencesKey("theme_palette_migrated")
        val KEY_OPACITY = intPreferencesKey("ui_opacity")               // 0 半透明 / 1 标准 / 2 不透明
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_FONT_SCALE = intPreferencesKey("font_scale")            // 0..4 档
        val KEY_BOTTOM_AUTO_HIDE = booleanPreferencesKey("bottom_auto_hide")
        val KEY_IMAGE_MODE = stringPreferencesKey("image_mode")         // inline | gallery
        val KEY_HIDE_BLACKLIST = booleanPreferencesKey("hide_blacklist") // 隐藏黑名单用户的发言
        val KEY_THEME_SHADE = intPreferencesKey("theme_shade")          // 0..100 背景着色浓度
        val KEY_GLASS_LEVEL = intPreferencesKey("glass_level")          // 0 关 / 1 轻 / 2 强
        val KEY_MOTION_ENABLED = booleanPreferencesKey("motion_enabled")
        val KEY_MOTION_DAMPING = intPreferencesKey("motion_damping")    // 0..100
        val KEY_CUSTOM_SEED = longPreferencesKey("custom_seed")         // 自定义种子色 ARGB
        val KEY_AUTO_PAGINATION = booleanPreferencesKey("auto_pagination") // 评论自动下一页
        val KEY_TOOLBAR_STYLE = stringPreferencesKey("bbc_toolbar_style")   // text 文字 | icon 图标
        val KEY_TOOLBAR_ROWS = intPreferencesKey("bbc_toolbar_rows")        // 工具条显示行数，默认 2
        val KEY_REPLY_PREVIEW = booleanPreferencesKey("bbc_reply_preview")  // 回复时实时预览

        // ---- 网络与阅读 ----
        val KEY_DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val KEY_DOWNLOAD_MODE = stringPreferencesKey("download_mode")   // inapp | browser
        val KEY_DEFAULT_VIEW = stringPreferencesKey("default_guide_view")

        // ---- 账号与签到 ----
        val KEY_ACTIVE_ACCOUNT = stringPreferencesKey("active_account")
        val KEY_AUTO_SIGN = booleanPreferencesKey("auto_sign")
        val KEY_SIGN_HOUR = intPreferencesKey("sign_hour")
        val KEY_SIGN_MINUTE = intPreferencesKey("sign_minute")
        val KEY_SIGN_SPACING = intPreferencesKey("sign_spacing")        // 账号之间间隔秒
        val KEY_LAST_SIGN_RUN = longPreferencesKey("last_sign_run")
        val KEY_LAST_SIGN_SUMMARY = stringPreferencesKey("last_sign_summary")
        val KEY_NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")

        // ---- AI ----
        val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_AI_SYSTEM = stringPreferencesKey("ai_system_prompt")
        val KEY_AI_TEMPERATURE = stringPreferencesKey("ai_temperature")
        val KEY_AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val KEY_AI_SUMMARY_BUTTON = booleanPreferencesKey("ai_summary_button")
        val KEY_AI_DRY_RUN = booleanPreferencesKey("ai_dry_run")
        // 自动回复
        val KEY_AUTO_REPLY = booleanPreferencesKey("auto_reply")
        val KEY_AUTO_REPLY_ON_VIEW = booleanPreferencesKey("auto_reply_on_view")
        val KEY_AUTO_REPLY_TEMPLATE = stringPreferencesKey("auto_reply_template")
        val KEY_AUTO_REPLY_PROMPT = stringPreferencesKey("auto_reply_prompt")
        val KEY_AUTO_REPLY_DAILY = intPreferencesKey("auto_reply_daily")
        val KEY_AUTO_REPLY_AI = booleanPreferencesKey("auto_reply_ai")   // 用 AI 生成回复内容

        // ---- 编辑器 ----
        val KEY_QUICK_REPLIES = stringPreferencesKey("quick_replies")

        // ---- 记录中心 ----
        val KEY_RECORD_DETAIL = booleanPreferencesKey("record_detail_mode")

        const val DEFAULT_AI_BASE = "https://api.openai.com/v1"
        const val DEFAULT_AI_MODEL = "gpt-4o-mini"
        const val DEFAULT_SYSTEM_PROMPT =
            "你是 MT 论坛（bbs.binmt.cc，一个 Android 逆向与技术交流社区）的助理。回答简洁、用中文、给可操作的步骤。"
        const val DEFAULT_UNLOCK_TEMPLATE = "感谢分享，正好需要，学习了。"
        const val DEFAULT_QUICK_REPLIES =
            "感谢楼主分享\n支持一下\n学习了，谢谢\n看看隐藏内容\n感谢分享，收藏了"
    }

    // ---------------- flows ----------------

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[KEY_DARK_MODE] }
    val themeColor: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_COLOR] ?: 0 }
    val opacity: Flow<Int> = context.dataStore.data.map { it[KEY_OPACITY] ?: 1 }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }
    val fontScale: Flow<Int> = context.dataStore.data.map { it[KEY_FONT_SCALE] ?: 0 }
    val bottomAutoHide: Flow<Boolean> = context.dataStore.data.map { it[KEY_BOTTOM_AUTO_HIDE] ?: true }
    val imageMode: Flow<String> = context.dataStore.data.map { it[KEY_IMAGE_MODE] ?: "inline" }
    val hideBlacklist: Flow<Boolean> = context.dataStore.data.map { it[KEY_HIDE_BLACKLIST] ?: false }
    val themeShade: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_SHADE] ?: 45 }
    val glassLevel: Flow<Int> = context.dataStore.data.map { it[KEY_GLASS_LEVEL] ?: 1 }
    val motionEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_MOTION_ENABLED] ?: true }
    val motionDamping: Flow<Int> = context.dataStore.data.map { it[KEY_MOTION_DAMPING] ?: 50 }
    val customSeed: Flow<Long> = context.dataStore.data.map { it[KEY_CUSTOM_SEED] ?: 0L }
    val autoPagination: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_PAGINATION] ?: true }
    /** 快捷工具栏形态：文字 / 图标。 */
    val toolbarStyle: Flow<String> = context.dataStore.data.map { it[KEY_TOOLBAR_STYLE] ?: "icon" }
    /** 工具条显示行数（默认 2 行）。 */
    val toolbarRows: Flow<Int> = context.dataStore.data.map { it[KEY_TOOLBAR_ROWS] ?: 2 }
    /** 回复时实时预览。 */
    val replyPreview: Flow<Boolean> = context.dataStore.data.map { it[KEY_REPLY_PREVIEW] ?: true }

    val desktopMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DESKTOP_MODE] ?: false }
    val downloadMode: Flow<String> = context.dataStore.data.map { it[KEY_DOWNLOAD_MODE] ?: "inapp" }
    val defaultView: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_VIEW] ?: "newthread" }

    val activeAccount: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_ACCOUNT] }
    val autoSign: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SIGN] ?: true }
    val signHour: Flow<Int> = context.dataStore.data.map { it[KEY_SIGN_HOUR] ?: 8 }
    val signMinute: Flow<Int> = context.dataStore.data.map { it[KEY_SIGN_MINUTE] ?: 30 }
    val signSpacing: Flow<Int> = context.dataStore.data.map { it[KEY_SIGN_SPACING] ?: 5 }
    val lastSignRun: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SIGN_RUN] ?: 0L }
    val lastSignSummary: Flow<String> = context.dataStore.data.map { it[KEY_LAST_SIGN_SUMMARY] ?: "" }
    val notifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_ENABLED] ?: true }

    val aiBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_AI_BASE_URL] ?: DEFAULT_AI_BASE }
    val aiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_AI_API_KEY] ?: "" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[KEY_AI_MODEL] ?: DEFAULT_AI_MODEL }
    val aiSystemPrompt: Flow<String> = context.dataStore.data.map { it[KEY_AI_SYSTEM] ?: DEFAULT_SYSTEM_PROMPT }
    val aiTemperature: Flow<String> = context.dataStore.data.map { it[KEY_AI_TEMPERATURE] ?: "0.7" }
    val aiMaxTokens: Flow<Int> = context.dataStore.data.map { it[KEY_AI_MAX_TOKENS] ?: 2048 }
    val aiSummaryButton: Flow<Boolean> = context.dataStore.data.map { it[KEY_AI_SUMMARY_BUTTON] ?: true }
    val aiDryRun: Flow<Boolean> = context.dataStore.data.map { it[KEY_AI_DRY_RUN] ?: false }

    val autoReply: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_REPLY] ?: false }
    /** 进帖自动解锁：默认**开**，只要用户打开这个开关就立刻生效（不再要求两个开关同时开）。 */
    val autoReplyOnView: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_REPLY_ON_VIEW] ?: true }
    val autoReplyTemplate: Flow<String> = context.dataStore.data.map { it[KEY_AUTO_REPLY_TEMPLATE] ?: DEFAULT_UNLOCK_TEMPLATE }
    val autoReplyPrompt: Flow<String> = context.dataStore.data.map { it[KEY_AUTO_REPLY_PROMPT] ?: "" }
    val autoReplyDaily: Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_REPLY_DAILY] ?: 10 }
    val autoReplyAi: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_REPLY_AI] ?: false }

    val quickReplies: Flow<String> = context.dataStore.data.map { it[KEY_QUICK_REPLIES] ?: DEFAULT_QUICK_REPLIES }
    val recordDetailMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_RECORD_DETAIL] ?: false }

    // ---------------- setters ----------------

    suspend fun setDarkMode(v: Boolean?) {
        context.dataStore.edit { p -> if (v == null) p.remove(KEY_DARK_MODE) else p[KEY_DARK_MODE] = v }
    }

    suspend fun setThemeColor(v: Int) = context.dataStore.edit { it[KEY_THEME_COLOR] = v }

    /**
     * 旧版只有 6 套配色（0..5），新调色板有 12 套 + 自定义。
     * 直接沿用旧下标会「换主题变了个色」，所以启动时按色相就近映射一次，只做一次。
     *
     * 0 MT 蓝 → 冰蓝(Aurora)  1 森野绿 → 竹青(Forest)  2 暖阳橙 → 落日(Sunset)
     * 3 葡萄紫 → 星夜(Galaxy)  4 樱花粉 → 樱花(Sakura)  5 青碧 → 深海(Ocean)
     */
    suspend fun migrateLegacyPalette() = context.dataStore.edit { prefs ->
        if (prefs[KEY_PALETTE_MIGRATED] == true) return@edit
        val legacy = prefs[KEY_THEME_COLOR]
        if (legacy != null) {
            prefs[KEY_THEME_COLOR] = LEGACY_PALETTE_MAP.getOrElse(legacy) { 0 }
        }
        prefs[KEY_PALETTE_MIGRATED] = true
    }
    suspend fun setOpacity(v: Int) = context.dataStore.edit { it[KEY_OPACITY] = v }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = v }
    suspend fun setFontScale(v: Int) = context.dataStore.edit { it[KEY_FONT_SCALE] = v }
    suspend fun setBottomAutoHide(v: Boolean) = context.dataStore.edit { it[KEY_BOTTOM_AUTO_HIDE] = v }
    suspend fun setImageMode(v: String) = context.dataStore.edit { it[KEY_IMAGE_MODE] = v }
    suspend fun setHideBlacklist(v: Boolean) = context.dataStore.edit { it[KEY_HIDE_BLACKLIST] = v }
    suspend fun setThemeShade(v: Int) = context.dataStore.edit { it[KEY_THEME_SHADE] = v.coerceIn(0, 100) }
    suspend fun setGlassLevel(v: Int) = context.dataStore.edit { it[KEY_GLASS_LEVEL] = v.coerceIn(0, 2) }
    suspend fun setMotionEnabled(v: Boolean) = context.dataStore.edit { it[KEY_MOTION_ENABLED] = v }
    suspend fun setMotionDamping(v: Int) = context.dataStore.edit { it[KEY_MOTION_DAMPING] = v.coerceIn(0, 100) }
    suspend fun setCustomSeed(v: Long) = context.dataStore.edit { it[KEY_CUSTOM_SEED] = v }
    suspend fun setAutoPagination(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_PAGINATION] = v }
    suspend fun setToolbarStyle(v: String) = context.dataStore.edit { it[KEY_TOOLBAR_STYLE] = if (v == "text") "text" else "icon" }
    suspend fun setToolbarRows(v: Int) = context.dataStore.edit { it[KEY_TOOLBAR_ROWS] = v.coerceIn(1, 4) }
    suspend fun setReplyPreview(v: Boolean) = context.dataStore.edit { it[KEY_REPLY_PREVIEW] = v }

    suspend fun setDesktopMode(v: Boolean) = context.dataStore.edit { it[KEY_DESKTOP_MODE] = v }
    suspend fun setDownloadMode(v: String) = context.dataStore.edit { it[KEY_DOWNLOAD_MODE] = v }
    suspend fun setDefaultView(v: String) = context.dataStore.edit { it[KEY_DEFAULT_VIEW] = v }

    suspend fun setActiveAccount(name: String?) {
        context.dataStore.edit { p -> if (name == null) p.remove(KEY_ACTIVE_ACCOUNT) else p[KEY_ACTIVE_ACCOUNT] = name }
    }

    suspend fun setAutoSign(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_SIGN] = v }
    suspend fun setSignTime(hour: Int, minute: Int) {
        context.dataStore.edit { it[KEY_SIGN_HOUR] = hour; it[KEY_SIGN_MINUTE] = minute }
    }

    suspend fun setSignSpacing(seconds: Int) = context.dataStore.edit { it[KEY_SIGN_SPACING] = seconds.coerceIn(0, 60) }
    suspend fun setLastSignRun(at: Long, summary: String) {
        context.dataStore.edit { it[KEY_LAST_SIGN_RUN] = at; it[KEY_LAST_SIGN_SUMMARY] = summary }
    }

    suspend fun setNotifyEnabled(v: Boolean) = context.dataStore.edit { it[KEY_NOTIFY_ENABLED] = v }

    suspend fun setAiBaseUrl(v: String) = context.dataStore.edit { it[KEY_AI_BASE_URL] = v.trim() }
    suspend fun setAiApiKey(v: String) = context.dataStore.edit { it[KEY_AI_API_KEY] = v.trim() }
    suspend fun setAiModel(v: String) = context.dataStore.edit { it[KEY_AI_MODEL] = v.trim() }
    suspend fun setAiSystemPrompt(v: String) = context.dataStore.edit { it[KEY_AI_SYSTEM] = v }
    suspend fun setAiTemperature(v: String) = context.dataStore.edit { it[KEY_AI_TEMPERATURE] = v }
    suspend fun setAiMaxTokens(v: Int) = context.dataStore.edit { it[KEY_AI_MAX_TOKENS] = v }
    suspend fun setAiSummaryButton(v: Boolean) = context.dataStore.edit { it[KEY_AI_SUMMARY_BUTTON] = v }
    suspend fun setAiDryRun(v: Boolean) = context.dataStore.edit { it[KEY_AI_DRY_RUN] = v }

    suspend fun setAutoReply(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_REPLY] = v }
    suspend fun setAutoReplyOnView(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_REPLY_ON_VIEW] = v }
    suspend fun setAutoReplyTemplate(v: String) = context.dataStore.edit { it[KEY_AUTO_REPLY_TEMPLATE] = v }
    suspend fun setAutoReplyPrompt(v: String) = context.dataStore.edit { it[KEY_AUTO_REPLY_PROMPT] = v }
    suspend fun setAutoReplyDaily(v: Int) = context.dataStore.edit { it[KEY_AUTO_REPLY_DAILY] = v.coerceIn(0, 99) }
    suspend fun setAutoReplyAi(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_REPLY_AI] = v }

    suspend fun setQuickReplies(v: String) = context.dataStore.edit { it[KEY_QUICK_REPLIES] = v }
    suspend fun setRecordDetailMode(v: Boolean) = context.dataStore.edit { it[KEY_RECORD_DETAIL] = v }

    // ---------------- 角标基线（按账号隔离，修「全部历史变新消息」） ----------------

    private fun badgeKey(account: String) = stringPreferencesKey("badge_baseline_$account")

    suspend fun badgeBaseline(account: String): String =
        context.dataStore.data.first()[badgeKey(account)] ?: ""

    suspend fun setBadgeBaseline(account: String, value: String) =
        context.dataStore.edit { it[badgeKey(account)] = value }

    // ---------------- 便捷读取 ----------------

    suspend fun snapshotDesktopMode(): Boolean = desktopMode.first()
    suspend fun snapshotSignTime(): Pair<Int, Int> = signHour.first() to signMinute.first()
    suspend fun snapshotSpacing(): Int = signSpacing.first()
    suspend fun snapshotAutoSign(): Boolean = autoSign.first()
    suspend fun snapshotNotify(): Boolean = notifyEnabled.first()
    suspend fun snapshotAutoReply(): Boolean = autoReply.first()
}
