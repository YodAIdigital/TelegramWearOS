package app.yodai.telewear.telegram

import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves TDLib file ids to local paths, downloading on demand.
 * `synchronous = true` makes TDLib complete the download before answering,
 * which turns media loading into a simple suspend call.
 */
class FileRepository(private val core: TelegramCore) {

    private val cache = ConcurrentHashMap<Int, String>()

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

    /** Invalidate cached paths after TDLib's storage optimizer deletes the files. */
    fun clearCache() = cache.clear()
}
