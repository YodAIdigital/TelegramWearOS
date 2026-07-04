package app.yodai.telewear.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class BatteryState(val percent: Int, val charging: Boolean)

/** Live battery level/charging flow from the sticky ACTION_BATTERY_CHANGED broadcast. */
fun batteryFlow(context: Context): Flow<BatteryState> = callbackFlow {
    fun Intent.state(): BatteryState {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryState(
            percent = if (level >= 0 && scale > 0) level * 100 / scale else 100,
            charging = charging,
        )
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent?.let { trySend(it.state()) }
        }
    }
    val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    sticky?.let { trySend(it.state()) }
    awaitClose { runCatching { context.unregisterReceiver(receiver) } }
}.distinctUntilChanged()
