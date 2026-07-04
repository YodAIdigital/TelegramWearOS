package app.yodai.telewear.settings

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import app.yodai.telewear.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * In-app updates from GitHub Releases (built by the repo's CI on every push).
 *
 * Installs go through the PackageInstaller *session* API rather than an
 * ACTION_VIEW intent: a self-update kills this process mid-flight, and only a
 * system-owned session survives that. The user confirms once (dialog fired by
 * [InstallResultReceiver]); the system finishes the install on its own.
 */
class UpdateChecker(private val context: Context) {

    data class Release(val tag: String, val apkUrl: String?)

    /** Latest published release, or null if unreachable/none. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val url = assets.getJSONObject(i).optString("browser_download_url")
                    if (url.endsWith(".apk")) {
                        apk = url
                        break
                    }
                }
            }
            Release(tag = json.optString("tag_name"), apkUrl = apk)
        }.onFailure { Log.w(TAG, "update check failed: $it") }.getOrNull()
    }

    /** True when the release tag differs from what this build was stamped with. */
    fun isNewer(release: Release): Boolean =
        release.tag.removePrefix("v").isNotEmpty() &&
            release.tag.removePrefix("v") != BuildConfig.VERSION_NAME

    /**
     * Downloads the APK (verifying completeness) and commits it as a
     * PackageInstaller session. Returns null on success, else a short error.
     */
    suspend fun downloadAndInstall(
        apkUrl: String,
        onProgress: (Int) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        try {
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val expected = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (expected > 0) onProgress(((copied * 100) / expected).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (expected > 0 && file.length() != expected) {
                return@withContext "Download incomplete — try again"
            }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply {
                    setAppPackageName(BuildConfig.APPLICATION_ID)
                    setSize(file.length())
                }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(InstallResultReceiver.ACTION).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
            null
        } catch (t: Throwable) {
            Log.w(TAG, "update install failed", t)
            "Update failed: ${t.message?.take(60) ?: "unknown error"}"
        } finally {
            runCatching { file.delete() }
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"
        const val REPO = "YodAIdigital/TelegramWearOS"
    }
}
