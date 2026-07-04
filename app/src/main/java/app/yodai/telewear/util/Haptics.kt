package app.yodai.telewear.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** Short confirmation tick (recording start/stop, send, reaction). */
fun buzz(context: Context, durationMs: Long = 40) {
    runCatching {
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
