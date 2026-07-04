package app.yodai.telewear.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

const val DEFAULT_QUICK_REPLIES = "👍;On my way;Can't talk right now"

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val showPreview: Boolean = true,
    val fontScale: Float = 1.0f,
    val keepAlive: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    /** Suppress own notifications while the phone is connected (bridged ones arrive instead). */
    val smartDedupe: Boolean = true,
    /** Auto-delete downloaded media older than N days; 0 = off. */
    val autoCleanupDays: Int = 0,
    /** Suspend "Stay connected" below this battery %; 0 = never suspend. */
    val keepAliveMinBattery: Int = 20,
    /** Semicolon-separated one-tap replies. */
    val quickReplies: String = DEFAULT_QUICK_REPLIES,
    /** Master switch for quick-reply chips (composer + notification choices). */
    val quickRepliesEnabled: Boolean = true,
)

fun AppSettings.quickReplyList(): List<String> =
    quickReplies.split(';').map { it.trim() }.filter { it.isNotEmpty() }.take(6)

/** Font size steps offered in Settings; scale multiplies message/chat text sizes. */
val FONT_SCALE_STEPS = listOf(0.85f to "Small", 1.0f to "Default", 1.15f to "Large", 1.3f to "Extra large")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val notifications = booleanPreferencesKey("notifications_enabled")
        val preview = booleanPreferencesKey("show_preview")
        val fontScale = floatPreferencesKey("font_scale")
        val keepAlive = booleanPreferencesKey("keep_alive")
        val playbackSpeed = floatPreferencesKey("playback_speed")
        val smartDedupe = booleanPreferencesKey("smart_dedupe")
        val autoCleanupDays = intPreferencesKey("auto_cleanup_days")
        val keepAliveMinBattery = intPreferencesKey("keep_alive_min_battery")
        val quickReplies = stringPreferencesKey("quick_replies")
        val quickRepliesEnabled = booleanPreferencesKey("quick_replies_enabled")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            notificationsEnabled = p[Keys.notifications] ?: true,
            showPreview = p[Keys.preview] ?: true,
            fontScale = p[Keys.fontScale] ?: 1.0f,
            keepAlive = p[Keys.keepAlive] ?: false,
            playbackSpeed = p[Keys.playbackSpeed] ?: 1.0f,
            smartDedupe = p[Keys.smartDedupe] ?: true,
            autoCleanupDays = p[Keys.autoCleanupDays] ?: 0,
            keepAliveMinBattery = p[Keys.keepAliveMinBattery] ?: 20,
            quickReplies = p[Keys.quickReplies] ?: DEFAULT_QUICK_REPLIES,
            quickRepliesEnabled = p[Keys.quickRepliesEnabled] ?: true,
        )
    }

    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.notifications] = v }
    suspend fun setShowPreview(v: Boolean) = context.dataStore.edit { it[Keys.preview] = v }
    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[Keys.fontScale] = v }
    suspend fun setKeepAlive(v: Boolean) = context.dataStore.edit { it[Keys.keepAlive] = v }
    suspend fun setPlaybackSpeed(v: Float) = context.dataStore.edit { it[Keys.playbackSpeed] = v }
    suspend fun setSmartDedupe(v: Boolean) = context.dataStore.edit { it[Keys.smartDedupe] = v }
    suspend fun setAutoCleanupDays(v: Int) = context.dataStore.edit { it[Keys.autoCleanupDays] = v }
    suspend fun setKeepAliveMinBattery(v: Int) = context.dataStore.edit { it[Keys.keepAliveMinBattery] = v }
    suspend fun setQuickReplies(v: String) = context.dataStore.edit { it[Keys.quickReplies] = v }
    suspend fun setQuickRepliesEnabled(v: Boolean) = context.dataStore.edit { it[Keys.quickRepliesEnabled] = v }
}
