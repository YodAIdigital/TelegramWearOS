package app.yodai.telewear.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val showPreview: Boolean = true,
    val fontScale: Float = 1.0f,
    val keepAlive: Boolean = false,
    val playbackSpeed: Float = 1.0f,
)

/** Font size steps offered in Settings; scale multiplies message/chat text sizes. */
val FONT_SCALE_STEPS = listOf(0.85f to "Small", 1.0f to "Default", 1.15f to "Large", 1.3f to "Extra large")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val notifications = booleanPreferencesKey("notifications_enabled")
        val preview = booleanPreferencesKey("show_preview")
        val fontScale = floatPreferencesKey("font_scale")
        val keepAlive = booleanPreferencesKey("keep_alive")
        val playbackSpeed = floatPreferencesKey("playback_speed")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            notificationsEnabled = p[Keys.notifications] ?: true,
            showPreview = p[Keys.preview] ?: true,
            fontScale = p[Keys.fontScale] ?: 1.0f,
            keepAlive = p[Keys.keepAlive] ?: false,
            playbackSpeed = p[Keys.playbackSpeed] ?: 1.0f,
        )
    }

    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.notifications] = v }
    suspend fun setShowPreview(v: Boolean) = context.dataStore.edit { it[Keys.preview] = v }
    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[Keys.fontScale] = v }
    suspend fun setKeepAlive(v: Boolean) = context.dataStore.edit { it[Keys.keepAlive] = v }
    suspend fun setPlaybackSpeed(v: Float) = context.dataStore.edit { it[Keys.playbackSpeed] = v }
}
