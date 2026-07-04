package app.yodai.telewear.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.yodai.telewear.telegram.MsgContent
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.rememberFilePath
import app.yodai.telewear.ui.theme.TeleWearColors
import app.yodai.telewear.util.formatDuration
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp

private val urlRegex = Regex("""https?://[^\s]+""")

/** Text with tappable links: opens on the watch browser, else on the paired phone. */
@Composable
fun LinkedText(text: String, fontScale: Float) {
    val context = LocalContext.current
    val hasLink = urlRegex.containsMatchIn(text)
    if (!hasLink) {
        Text(
            text,
            fontSize = 13.sp * fontScale,
            lineHeight = 17.sp * fontScale,
            color = TeleWearColors.onBubble,
        )
        return
    }
    val annotated = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (m in urlRegex.findAll(text)) {
                append(text.substring(last, m.range.first))
                val url = m.value.trimEnd('.', ',', ')', ']')
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "url",
                        styles = TextLinkStyles(
                            SpanStyle(
                                color = TeleWearColors.accentLight,
                                textDecoration = TextDecoration.Underline,
                            )
                        ),
                        linkInteractionListener = { openLink(context, url) },
                    )
                ) {
                    append(m.value)
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(
        annotated,
        fontSize = 13.sp * fontScale,
        lineHeight = 17.sp * fontScale,
        color = TeleWearColors.onBubble,
    )
}

private fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No browser on the watch — hand the link to the paired phone.
        runCatching {
            RemoteActivityHelper(context).startRemoteActivity(intent)
            Toast.makeText(context, "Opened on phone", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Can't open link", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Voice notes and audio files: play button + progress + optional title.
 * The small button quick-plays; tapping the rest of the row opens the
 * full player (speed control + bezel scrubbing).
 */
@Composable
fun PlayableRow(messageId: Long, c: MsgContent.Playable, fontScale: Float, onOpenPlayer: () -> Unit) {
    val graph = LocalAppGraph.current
    val path = rememberFilePath(c.fileId)
    val playingId by graph.voicePlayer.playingMessageId.collectAsState()
    val playing = playingId == messageId
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (playing) {
            while (isActive) {
                progress = graph.voicePlayer.progress()
                delay(100)
            }
        } else {
            progress = 0f
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpenPlayer),
    ) {
        if (c.title != null) {
            Text(
                c.title,
                fontSize = 11.sp * fontScale,
                color = TeleWearColors.onBubble,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = path != null) {
                        path?.let { graph.voicePlayer.toggle(messageId, it) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (path == null) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp))
                } else {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Box(
                    Modifier
                        .width(104.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    formatDuration(c.duration),
                    fontSize = 9.sp,
                    color = TeleWearColors.timestamp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Video/GIF/video-message bubble: thumbnail + play badge; tap for fullscreen. */
@Composable
fun VideoBubble(c: MsgContent.Video, fontScale: Float, onPlay: () -> Unit) {
    val thumb = rememberFilePath(c.thumbFileId)
    val ratio = if (c.height > 0) (c.width.toFloat() / c.height).coerceIn(0.6f, 1.8f) else 1f
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141B20))
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            AsyncImage(
                model = File(thumb),
                contentDescription = c.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0x99000000)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            formatDuration(c.duration),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
    if (c.caption.isNotEmpty()) {
        Text(
            c.caption,
            fontSize = 12.sp * fontScale,
            color = TeleWearColors.onBubble,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
fun StickerBubble(c: MsgContent.Sticker) {
    val thumb = rememberFilePath(c.thumbFileId)
    if (thumb != null) {
        AsyncImage(
            model = File(thumb),
            contentDescription = "${c.emoji} sticker",
            modifier = Modifier.size(84.dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(c.emoji.ifBlank { "🖼" }, fontSize = 40.sp)
    }
}

@Composable
fun DocBubble(c: MsgContent.Doc, fontScale: Float, onOpenPdf: () -> Unit) {
    val isPdf = c.mime == "application/pdf" || c.fileName.endsWith(".pdf", ignoreCase = true)
    val sizeText = when {
        c.size >= 1_048_576 -> "%.1f MB".format(c.size / 1_048_576f)
        c.size >= 1024 -> "${c.size / 1024} KB"
        else -> "${c.size} B"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isPdf, onClick = onOpenPdf)
            .padding(2.dp),
    ) {
        Icon(
            Icons.Rounded.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(
                c.fileName,
                fontSize = 11.sp * fontScale,
                color = TeleWearColors.onBubble,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (isPdf) "$sizeText · tap to view" else sizeText,
                fontSize = 9.sp,
                color = TeleWearColors.timestamp,
            )
        }
    }
}

/** Fullscreen video playback: downloads, then plays; tap toggles pause, X closes. */
@Composable
fun VideoPlayerOverlay(fileId: Int, onClose: () -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    var path by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(fileId) {
        graph.voicePlayer.stop()
        val p = graph.files.path(fileId)
        if (p == null) failed = true else path = p
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val p = path
        when {
            failed -> Text("Couldn't load video", fontSize = 11.sp)
            p == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text("Downloading…", fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
            else -> {
                val player = remember(p) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(p))))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(p) {
                    onDispose { player.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { if (player.isPlaying) player.pause() else player.play() },
                )
                LaunchedEffect(player) {
                    while (isActive) {
                        if (player.playbackState == Player.STATE_ENDED) {
                            onClose()
                            break
                        }
                        delay(300)
                    }
                }
            }
        }
        CloseChip(onClose)
    }
}

/** Fullscreen PDF viewer: renders pages on demand with the platform PdfRenderer. */
@Composable
fun PdfViewerOverlay(fileId: Int, onClose: () -> Unit) {
    val graph = LocalAppGraph.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val renderLock = remember { Mutex() }

    LaunchedEffect(fileId) {
        val p = graph.files.path(fileId)
        if (p == null) {
            failed = true
            return@LaunchedEffect
        }
        runCatching {
            val pfd = ParcelFileDescriptor.open(File(p), ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd)
        }.onSuccess {
            renderer = it
            pageCount = it.pageCount
        }.onFailure {
            failed = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { renderer?.close() } }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val r = renderer
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't open PDF", fontSize = 11.sp)
            }
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("Loading PDF…", fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items((0 until pageCount).toList()) { index ->
                    PdfPage(r, renderLock, index)
                }
            }
        }
        CloseChip(onClose)
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, lock: Mutex, index: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index) {
        value = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val page = renderer.openPage(index)
                    try {
                        val scale = 480f / page.width
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally {
                        page.close()
                    }
                }.getOrNull()
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.CloseChip(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xAA1B2228))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
