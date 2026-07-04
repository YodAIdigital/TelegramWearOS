package app.yodai.telewear.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import app.yodai.telewear.telegram.MsgContent
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.rememberFilePath
import app.yodai.telewear.ui.theme.TeleWearColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Milliseconds of seek per rotary pixel — one bezel detent ≈ 3 s. */
internal const val ROTARY_SEEK_MS_PER_PIXEL = 25f

/**
 * Fullscreen audio player for voice notes and audio files:
 * pause/resume, 0.5–2.5× speed slider, and rotary-bezel scrubbing.
 */
@Composable
fun AudioPlayerOverlay(messageId: Long, playable: MsgContent.Playable, onClose: () -> Unit) {
    val graph = LocalAppGraph.current
    val player = graph.voicePlayer
    val path = rememberFilePath(playable.fileId)

    val playingId by player.playingMessageId.collectAsState()
    val speed by player.speed.collectAsState()
    val isCurrent = playingId == messageId

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(playable.duration * 1000L) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        if (path != null && player.playingMessageId.value != messageId) {
            player.toggle(messageId, path)
        }
    }

    LaunchedEffect(isCurrent) {
        while (isActive) {
            if (isCurrent) {
                positionMs = player.positionMs()
                player.durationMs().takeIf { it > 0 }?.let { durationMs = it }
                playing = player.isPlaying
            } else {
                playing = false
            }
            delay(100)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                if (isCurrent) {
                    player.seekBy((event.verticalScrollPixels * ROTARY_SEEK_MS_PER_PIXEL).toLong())
                    positionMs = player.positionMs()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
        ) {
            Text(
                playable.title ?: "Voice message",
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = TeleWearColors.onBubble,
            )
            PlaybackControls(
                ready = path != null,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                speed = speed,
                onPlayPause = {
                    if (!isCurrent) {
                        path?.let { player.toggle(messageId, it) }
                    } else {
                        player.playPause()
                    }
                },
                onSpeedChange = { player.setSpeed(it) },
            )
        }
        CloseChip(onClose)
    }
}

/**
 * Shared transport controls: position bar, timing, play/pause, speed slider.
 * Used by the audio player and the video player overlays.
 */
@Composable
internal fun PlaybackControls(
    ready: Boolean,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FAF8F5)),
        ) {
            val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            "${formatMs(positionMs)} / ${formatMs(durationMs)}  ·  bezel to seek",
            fontSize = 9.sp,
            color = TeleWearColors.timestamp,
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            if (!ready) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
            } else {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            steps = 7,
            valueRange = 0.5f..2.5f,
            segmented = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Speed ${formatSpeed(speed)}",
            fontSize = 10.sp,
            color = TeleWearColors.accentLight,
        )
    }
}

internal fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

internal fun formatSpeed(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}×" else "${"%.2f".format(s).trimEnd('0').trimEnd('.')}×"
