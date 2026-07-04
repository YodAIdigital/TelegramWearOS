package app.yodai.telewear.telegram

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Resolves TDLib file ids to local paths, downloading on demand.
 * `synchronous = true` makes TDLib complete the download before answering,
 * which turns media loading into a simple suspend call. Live download
 * progress is exposed separately via [progress] (fed by UpdateFile).
 */
class FileRepository(private val core: TelegramCore, scope: CoroutineScope) {

    private val cache = ConcurrentHashMap<Int, String>()

    /** fileId → 0..1 fraction, present only while a download is active. */
    private val _progress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val progress: StateFlow<Map<Int, Float>> = _progress

    init {
        scope.launch {
            core.updates { it.fileUpdates }.collect { u ->
                val f = u.file
                val total = if (f.size > 0) f.size else f.expectedSize
                if (f.local.isDownloadingActive && total > 0) {
                    _progress.value = _progress.value +
                        (f.id to (f.local.downloadedSize.toFloat() / total).coerceIn(0f, 1f))
                } else if (_progress.value.containsKey(f.id)) {
                    _progress.value = _progress.value - f.id
                }
            }
        }
    }

    suspend fun path(fileId: Int): String? {
        cache[fileId]?.let { return it }
        val file = core.client
            .downloadFile(fileId = fileId, priority = 16, offset = 0, limit = 0, synchronous = true)
            .getOrNull() ?: return null
        val local = file.local
        val path = local.path.takeIf { it.isNotEmpty() && local.isDownloadingCompleted } ?: return null
        cache[fileId] = path
        return path
    }

    /** Abort an in-flight download (the pending [path] call returns null). */
    fun cancel(fileId: Int) {
        core.async { it.cancelDownloadFile(fileId = fileId, onlyIfPending = false) }
    }

    /** Invalidate cached paths after TDLib's storage optimizer deletes the files. */
    fun clearCache() = cache.clear()
}
