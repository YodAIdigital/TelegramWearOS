package app.yodai.telewear.telegram

import android.util.Log
import app.yodai.telewear.settings.SettingsRepository
import dev.g000sha256.tdl.dto.FileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Periodic media auto-cleanup: every few hours asks TDLib's storage optimizer
 * to delete downloaded files older than the configured TTL (Settings →
 * "Auto-clean media"). Runs while the process is alive; each pass is cheap.
 */
class StorageCleaner(
    private val core: TelegramCore,
    private val files: FileRepository,
    settings: SettingsRepository,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            settings.flow.map { it.autoCleanupDays }.distinctUntilChanged().collectLatest { days ->
                if (days <= 0) return@collectLatest
                while (true) {
                    // Give TDLib time to reach Ready after process start.
                    delay(60_000)
                    runCatching {
                        core.client.optimizeStorage(
                            size = -1,
                            ttl = days * 86_400,
                            count = -1,
                            immunityDelay = 3_600,
                            fileTypes = emptyArray<FileType>(),
                            chatIds = LongArray(0),
                            excludeChatIds = LongArray(0),
                            returnDeletedFileStatistics = false,
                            chatLimit = 0,
                        )
                        files.clearCache()
                        Log.i("StorageCleaner", "auto-clean pass done (ttl=${days}d)")
                    }.onFailure { Log.w("StorageCleaner", "auto-clean failed", it) }
                    delay(6 * 60 * 60 * 1000L)
                }
            }
        }
    }
}
