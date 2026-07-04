package app.yodai.telewear.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single shared player for voice notes (TDLib stores them as Ogg/Opus, which
 * ExoPlayer decodes natively). Created on the main thread in Application.onCreate;
 * all controls are called from composables, i.e. also the main thread.
 */
class VoicePlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _playingMessageId = MutableStateFlow<Long?>(null)
    val playingMessageId: StateFlow<Long?> = _playingMessageId

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) _playingMessageId.value = null
            }
        })
    }

    /** Play the given voice note, or stop if it is already playing. */
    fun toggle(messageId: Long, path: String) {
        if (_playingMessageId.value == messageId) {
            stop()
            return
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
        player.play()
        _playingMessageId.value = messageId
    }

    fun stop() {
        player.stop()
        _playingMessageId.value = null
    }

    /** 0..1 playback progress of the current item (poll while playing). */
    fun progress(): Float {
        val duration = player.duration
        if (duration <= 0) return 0f
        return (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    // ---- full-player controls (speed, scrubbing, pause/resume) ----

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    /** Playback speed 0.5–2.5×; persists across items until changed. */
    fun setSpeed(value: Float) {
        val s = value.coerceIn(0.5f, 2.5f)
        player.setPlaybackSpeed(s)
        _speed.value = s
    }

    val isPlaying: Boolean get() = player.isPlaying

    fun positionMs(): Long = player.currentPosition.coerceAtLeast(0)

    fun durationMs(): Long = if (player.duration > 0) player.duration else 0

    /** Relative seek (rotary bezel scrubbing). */
    fun seekBy(deltaMs: Long) {
        val duration = durationMs()
        if (duration <= 0) return
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
    }

    /** Pause/resume without clearing the current item (unlike [toggle]). */
    fun playPause() {
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            else -> player.play()
        }
    }
}
