package app.yodai.telewear.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import app.yodai.telewear.R

/**
 * Receives PackageInstaller session status for self-updates.
 * PENDING_USER_ACTION fires once — we surface the system confirm dialog;
 * after the user approves, the system completes the install even though
 * this process is killed by the update.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Log.w(TAG, "can't show install confirmation", it) }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")

            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"
                Log.w(TAG, "update failed: $msg")
                Toast.makeText(context, "Update failed: $msg", Toast.LENGTH_LONG).show()
                notifyFailure(context, msg)
            }
        }
    }

    /** Toast can be missed on a sleeping watch — leave a notification too. */
    private fun notifyFailure(context: Context, msg: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.notify(
                NOTE_ID,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("TeleWear update failed")
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    companion object {
        const val ACTION = "app.yodai.telewear.INSTALL_RESULT"
        private const val TAG = "InstallResult"
        private const val CHANNEL = "updates"
        private const val NOTE_ID = 300
    }
}
