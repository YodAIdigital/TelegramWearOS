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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        // Shows the app as an ongoing activity chip on the watch face.
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(touchIntent)
            .build()
            .apply(applicationContext)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            builder.build(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 100

        fun setEnabled(context: Context, enabled: Boolean) {
            val i = Intent(context, KeepAliveService::class.java)
            if (enabled) context.startForegroundService(i) else context.stopService(i)
        }
    }
}
