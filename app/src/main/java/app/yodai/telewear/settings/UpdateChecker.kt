package app.yodai.telewear.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import app.yodai.telewear.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * In-app updates from GitHub Releases (built by the repo's CI on every push).
 * Uses the unauthenticated API, so it requires the repo to be public; on a
 * private repo the check fails gracefully with "can't reach updates".
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
     * Downloads the APK to the app cache and hands it to the system package
     * installer (needs the "install unknown apps" grant on first use).
     */
    suspend fun downloadAndInstall(apkUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "update.apk")
            (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 60_000
            }.inputStream.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.onFailure { Log.w(TAG, "update install failed: $it") }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "UpdateChecker"
        const val REPO = "YodAIdigital/TelegramWearOS"
    }
}
