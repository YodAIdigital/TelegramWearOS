package app.yodai.telewear.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import app.yodai.telewear.R
import app.yodai.telewear.TeleWearApp
import app.yodai.telewear.ui.MainActivity

/**
 * Optional foreground service that keeps the process (and thus the TDLib connection)
 * alive so the watch receives messages while the app is in the background —
 * useful away from the phone (Wi-Fi/LTE). Toggled from Settings; costs battery.
 */
class KeepAliveService : Service() {

    // Whether a foreground notification is currently posted, and whether it
    // carried the watch-face chip. A Wear OngoingActivity chip only disappears
    // when the notification hosting it is *removed* — silently re-posting the
    // same id without the chip does not clear it — so a shown→hidden switch
    // must tear the notification down before rebuilding it plain.
    private var foregroundPosted = false
    private var chipShown = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val showShortcut = intent?.getBooleanExtra(EXTRA_SHOW_SHORTCUT, true) ?: true

        if (foregroundPosted && chipShown && !showShortcut) {
            // Drop the chip-bearing notification entirely, then re-post plain.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundPosted = false
        }

        val touchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, TeleWearApp.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Connected to Telegram")
            .setOngoing(true)
            .setContentIntent(touchIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // The foreground-service notification is mandatory, but the watch-face
        // chip is opt-out: only decorate it as an OngoingActivity when the user
        // wants the shortcut. Its icon is the app mascot.
        if (showShortcut) {
            OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_mascot)
                .setTouchIntent(touchIntent)
                .build()
                .apply(applicationContext)
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            builder.build(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        foregroundPosted = true
        chipShown = showShortcut
        // Redeliver the last intent (not a null one) after a process restart, so
        // the shortcut preference survives instead of defaulting back to shown.
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 100
        private const val EXTRA_SHOW_SHORTCUT = "show_shortcut"

        fun setEnabled(context: Context, enabled: Boolean, showShortcut: Boolean = true) {
            val i = Intent(context, KeepAliveService::class.java)
                .putExtra(EXTRA_SHOW_SHORTCUT, showShortcut)
            if (enabled) context.startForegroundService(i) else context.stopService(i)
        }
    }
}
