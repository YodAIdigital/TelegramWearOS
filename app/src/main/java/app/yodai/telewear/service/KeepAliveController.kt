package app.yodai.telewear.service

import android.content.Context
import android.util.Log
import app.yodai.telewear.settings.SettingsRepository
import app.yodai.telewear.util.batteryFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Single owner of the keep-alive service lifecycle: runs it only while the
 * Settings toggle is on AND the battery is above the configured floor
 * (or charging). Recovers automatically when the level climbs back or the
 * watch goes on the charger.
 */
class KeepAliveController(
    context: Context,
    settings: SettingsRepository,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            combine(settings.flow, batteryFlow(context)) { s, battery ->
                s.keepAlive &&
                    (s.keepAliveMinBattery == 0 || battery.charging || battery.percent > s.keepAliveMinBattery)
            }
                .distinctUntilChanged()
                .collect { shouldRun ->
                    Log.i("KeepAliveController", "keep-alive service running=$shouldRun")
                    KeepAliveService.setEnabled(context, shouldRun)
                }
        }
    }
}
