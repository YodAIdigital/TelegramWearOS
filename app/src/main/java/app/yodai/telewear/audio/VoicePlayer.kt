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
}
