package app.yodai.telewear

import android.app.Application
import app.yodai.telewear.audio.VoicePlayer
import app.yodai.telewear.audio.VoiceRecorder
import app.yodai.telewear.notifications.MessageNotifier
import app.yodai.telewear.settings.SettingsRepository
import app.yodai.telewear.telegram.ChatRepository
import app.yodai.telewear.telegram.FileRepository
import app.yodai.telewear.telegram.MessageRepository
import app.yodai.telewear.telegram.TelegramCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual dependency graph. One instance per process, owned by [TeleWearApp].
 * Kept deliberately simple — no DI framework needed at this scale.
 */
class AppGraph(app: Application) {

    val appContext: Application = app
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(app)
    val core = TelegramCore(app, appScope)
    val files = FileRepository(core)
    val chats = ChatRepository(core, appScope)
    val messages = MessageRepository(core)

    val voiceRecorder = VoiceRecorder(app)
    val voicePlayer = VoicePlayer(app)

    /** Chat currently on screen — its notifications are suppressed. */
    val activeChatId = MutableStateFlow<Long?>(null)

    /** Set when a message notification is tapped; the nav host opens the chat and clears it. */
    val pendingOpenChatId = MutableStateFlow<Long?>(null)

    val notifier = MessageNotifier(app, core, chats, settings, activeChatId, appScope)

    init {
        // Apply the saved default playback speed (ExoPlayer must be touched on main).
        appScope.launch(Dispatchers.Main) {
            voicePlayer.setSpeed(settings.flow.first().playbackSpeed)
        }
    }
}
