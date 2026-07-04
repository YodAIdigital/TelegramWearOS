package app.yodai.telewear.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Records Telegram-compatible voice notes: Opus in an Ogg container
 * (the exact format official clients produce), 48 kHz mono @ 32 kbps.
 * Amplitude samples collected during recording become the message waveform.
 */
class VoiceRecorder(private val context: Context) {

    data class Recording(val path: String, val durationSec: Int, val waveform: ByteArray)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val amplitudes = ArrayList<Int>(256)
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        cancel()
        val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(32000)
            r.setAudioChannels(1)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            amplitudes.clear()
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.w(TAG, "start failed", e)
            runCatching { r.release() }
            out.delete()
            recorder = null
            file = null
            false
        }
    }

    /** Poll from the UI (~10 Hz): feeds the live level indicator and the final waveform. */
    fun pollAmplitude(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        if (recorder != null) amplitudes.add(amp)
        return (amp / 32767f).coerceIn(0f, 1f)
    }

    fun elapsedSec(): Int =
        if (recorder == null) 0 else ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()

    /** Stops and returns the finished recording, or null if it was too short/failed. */
    fun finish(): Recording? {
        val r = recorder ?: return null
        val f = file
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        file = null
        return try {
            r.stop()
            r.release()
            if (f == null || durationMs < 700) {
                f?.delete()
                null
            } else {
                Recording(f.absolutePath, (durationMs / 1000L).toInt().coerceAtLeast(1), packWaveform())
            }
        } catch (e: Exception) {
            Log.w(TAG, "finish failed", e)
            runCatching { r.release() }
            f?.delete()
            null
        }
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete()
        file = null
    }

    /** Telegram waveform: 100 samples, 5 bits each, packed LSB-first into 63 bytes. */
    private fun packWaveform(): ByteArray {
        val out = ByteArray(63)
        if (amplitudes.isEmpty()) return out
        val max = amplitudes.max().coerceAtLeast(1)
        for (i in 0 until 100) {
            val src = amplitudes[i * amplitudes.size / 100]
            val v = (src * 31 / max).coerceIn(0, 31)
            val bit = i * 5
            val byteIdx = bit / 8
            val shift = bit % 8
            out[byteIdx] = (out[byteIdx].toInt() or (v shl shift)).toByte()
            if (shift > 3 && byteIdx + 1 < out.size) {
                out[byteIdx + 1] = (out[byteIdx + 1].toInt() or (v shr (8 - shift))).toByte()
            }
        }
        return out
    }

    private companion object {
        const val TAG = "VoiceRecorder"
    }
}
