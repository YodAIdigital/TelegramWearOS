package app.yodai.telewear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class TeleWearApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        graph = AppGraph(this)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.notification_channel_ongoing),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        // Used when the watch-face shortcut is hidden: MIN keeps the mandatory
        // foreground-service notification off the watch face and out of the
        // main notification list (collapsed into the silent section).
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING_SILENT,
                getString(R.string.notification_channel_ongoing),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_ONGOING = "ongoing"
        const val CHANNEL_ONGOING_SILENT = "ongoing_silent"
    }
}
