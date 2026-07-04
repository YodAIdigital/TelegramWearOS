package app.yodai.telewear

import android.app.Application
import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.yodai.telewear.audio.TextSpeaker
import app.yodai.telewear.complication.UnreadComplicationService
import app.yodai.telewear.tile.MessagesTileService
import app.yodai.telewear.tile.TileSnapshot
import app.yodai.telewear.audio.VoicePlayer
import app.yodai.telewear.audio.VoiceRecorder
import app.yodai.telewear.notifications.MessageNotifier
import app.yodai.telewear.notifications.PhoneLinkMonitor
import app.yodai.telewear.service.KeepAliveController
import app.yodai.telewear.settings.SettingsRepository
import app.yodai.telewear.telegram.ChatRepository
import app.yodai.telewear.telegram.FileRepository
import app.yodai.telewear.telegram.MessageRepository
import app.yodai.telewear.telegram.StorageCleaner
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
    val files = FileRepository(core, appScope)
    val chats = ChatRepository(core, appScope)
    val messages = MessageRepository(core)

    val voiceRecorder = VoiceRecorder(app)
    val voicePlayer = VoicePlayer(app)
    val speaker by lazy { TextSpeaker(app) }

    /** Chat currently on screen — its notifications are suppressed. */
    val activeChatId = MutableStateFlow<Long?>(null)

    /** Set when a message notification is tapped; the nav host opens the chat and clears it. */
    val pendingOpenChatId = MutableStateFlow<Long?>(null)

    /** Phone reachability — drives smart notification dedupe. */
    val phoneLink = PhoneLinkMonitor(app, appScope)

    val notifier = MessageNotifier(app, core, chats, settings, activeChatId, phoneLink, appScope)

    /** Owns the keep-alive service lifecycle (settings toggle + battery floor). */
    val keepAliveController = KeepAliveController(app, settings, appScope)

    /** Periodic media TTL cleanup, when enabled in Settings. */
    val storageCleaner = StorageCleaner(core, files, settings, appScope)

    init {
        // Keep the Tile + complication snapshot fresh and poke their renderers.
        appScope.launch {
            var lastSignature: Pair<Int, List<Long>>? = null
            chats.chatList.collect { list ->
                val signature = list.sumOf { it.unread } to list.take(3).map { it.id }
                if (signature != lastSignature) {
                    lastSignature = signature
                    TileSnapshot.write(app, list)
                    runCatching {
                        TileService.getUpdater(app).requestUpdate(MessagesTileService::class.java)
                        ComplicationDataSourceUpdateRequester
                            .create(app, ComponentName(app, UnreadComplicationService::class.java))
                            .requestUpdateAll()
                    }
                }
            }
        }
    }

    init {
        // Apply the saved default playback speed (ExoPlayer must be touched on main).
        appScope.launch(Dispatchers.Main) {
            voicePlayer.setSpeed(settings.flow.first().playbackSpeed)
        }
    }
}
