package io.mtluntan.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings persisted with DataStore: UI theming, network toggles, and
 * notification preferences.
 */
class AppSettings(private val context: Context) {

    companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")     // null = follow system
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val KEY_AUTO_SIGN = booleanPreferencesKey("auto_sign")
        val KEY_NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")
        val KEY_ACTIVE_ACCOUNT = stringPreferencesKey("active_account")
        val KEY_FONT_SCALE = intPreferencesKey("font_scale")
        val KEY_DEFAULT_VIEW = stringPreferencesKey("default_guide_view")
    }

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[KEY_DARK_MODE] }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    val desktopMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DESKTOP_MODE] ?: false }
    val autoSign: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SIGN] ?: true }
    val notifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_ENABLED] ?: true }
    val activeAccount: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_ACCOUNT] }
    val fontScale: Flow<Int> = context.dataStore.data.map { it[KEY_FONT_SCALE] ?: 0 }
    val defaultView: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_VIEW] ?: "newthread" }

    suspend fun setDarkMode(v: Boolean?) {
        context.dataStore.edit { p ->
            if (v == null) p.remove(KEY_DARK_MODE) else p[KEY_DARK_MODE] = v
        }
    }

    suspend fun setDynamicColor(v: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = v }
    }

    suspend fun setDesktopMode(v: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_MODE] = v }
    }

    suspend fun setAutoSign(v: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SIGN] = v }
    }

    suspend fun setNotifyEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFY_ENABLED] = v }
    }

    suspend fun setActiveAccount(name: String?) {
        context.dataStore.edit { p ->
            if (name == null) p.remove(KEY_ACTIVE_ACCOUNT) else p[KEY_ACTIVE_ACCOUNT] = name
        }
    }

    suspend fun setFontScale(step: Int) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = step }
    }

    suspend fun setDefaultView(v: String) {
        context.dataStore.edit { it[KEY_DEFAULT_VIEW] = v }
    }

    suspend fun snapshotDesktopMode(): Boolean = desktopMode.first()
}