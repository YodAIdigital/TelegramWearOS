package app.yodai.telewear.notifications

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tracks whether the paired phone is currently reachable (Bluetooth/Wi-Fi via
 * the Wearable Data Layer). Used for smart notification dedupe: while the phone
 * is connected, its official Telegram app already bridges notifications to the
 * watch, so ours would be duplicates.
 */
class PhoneLinkMonitor(context: Context, scope: CoroutineScope) {

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected

    init {
        val nodeClient = Wearable.getNodeClient(context.applicationContext)
        scope.launch {
            while (isActive) {
                _phoneConnected.value = runCatching {
                    nodeClient.connectedNodes.await().any { it.isNearby }
                }.getOrDefault(false)
                delay(60_000)
            }
        }
    }
}
