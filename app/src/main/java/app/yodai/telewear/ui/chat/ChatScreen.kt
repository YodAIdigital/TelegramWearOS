package app.yodai.telewear.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.yodai.telewear.AppGraph
import app.yodai.telewear.audio.VoiceRecorder
import app.yodai.telewear.telegram.MsgContent
import app.yodai.telewear.telegram.MsgItem
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.LocalFontScale
import app.yodai.telewear.ui.components.rememberFilePath
import app.yodai.telewear.ui.components.rememberTextInputLauncher
import app.yodai.telewear.ui.theme.TeleWearColors
import app.yodai.telewear.util.avatarColor
import app.yodai.telewear.util.formatDuration
import app.yodai.telewear.util.formatTime
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatViewModel(private val graph: AppGraph, private val chatId: Long) : ViewModel() {

    val thread = graph.messages.openThread(chatId, viewModelScope)
    val chat = graph.chats.chatItem(chatId)
    val senderNames = MutableStateFlow<Map<Long, String>>(emptyMap())

    init {
        graph.activeChatId.value = chatId
        viewModelScope.launch {
            thread.messages.collect { list ->
                val missing = list.mapNotNull { it.senderUserId }.distinct()
                    .filter { it !in senderNames.value }
                if (missing.isNotEmpty()) {
                    val resolved = missing.map { id -> id to graph.chats.displayName(id) }
                    senderNames.value = senderNames.value + resolved
                }
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch { thread.loadMore() }
    }

    override fun onCleared() {
        if (graph.activeChatId.value == chatId) graph.activeChatId.value = null
        thread.dispose()
    }
}

@Composable
fun ChatScreen(chatId: Long) {
    val graph = LocalAppGraph.current
    val vm: ChatViewModel = viewModel(key = "chat_$chatId") { ChatViewModel(graph, chatId) }
    val messages by vm.thread.messages.collectAsState()
    val outboxRead by vm.thread.lastReadOutbox.collectAsState()
    val names by vm.senderNames.collectAsState()
    val sendError by vm.thread.lastSendError.collectAsState()
    val connecting by graph.core.isConnecting.collectAsState()

    // Send errors show briefly, then clear.
    LaunchedEffect(sendError) {
        if (sendError != null) {
            delay(6000)
            vm.thread.lastSendError.value = null
        }
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var showRecorder by remember { mutableStateOf(false) }
    var viewImagePath by remember { mutableStateOf<String?>(null) }
    var videoFileId by remember { mutableStateOf<Int?>(null) }
    var pdfFileId by remember { mutableStateOf<Int?>(null) }

    val launchTextInput = rememberTextInputLauncher("Message") { vm.thread.sendText(it) }

    // Follow new incoming/outgoing messages when already near the bottom.
    LaunchedEffect(messages.firstOrNull()?.id) {
        if (messages.isNotEmpty() && listState.centerItemIndex <= 2) {
            listState.animateScrollToItem(1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Index 0 (visual bottom): composer.
                item {
                    ComposerRow(
                        onKeyboard = launchTextInput,
                        onMic = { showRecorder = true },
                    )
                }

                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isGroup = vm.chat?.isGroup == true,
                        senderName = msg.senderUserId?.let { names[it] },
                        readByPeer = msg.isOutgoing && msg.id <= outboxRead,
                        onPhotoClick = { path -> viewImagePath = path },
                        onPlayVideo = { fileId -> videoFileId = fileId },
                        onOpenPdf = { fileId -> pdfFileId = fileId },
                    )
                }

                // Visual top: chat title header + pagination trigger.
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            vm.chat?.title ?: "Chat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        // Loads one older page each time this header scrolls into view.
                        LaunchedEffect(Unit) { vm.loadMore() }
                    }
                }
            }
        }

        // Status pill below the clock: connection state or last send failure.
        val statusText = when {
            sendError != null -> sendError
            connecting -> "Connecting…"
            else -> null
        }
        if (statusText != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    statusText,
                    fontSize = 9.sp,
                    color = if (sendError != null) TeleWearColors.recordRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TeleWearColors.incomingBubble)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        if (showRecorder) {
            VoiceRecordOverlay(
                onDismiss = { showRecorder = false },
                onSend = { rec ->
                    vm.thread.sendVoice(rec.path, rec.durationSec, rec.waveform)
                    showRecorder = false
                },
            )
        }

        viewImagePath?.let { path ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewImagePath = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        videoFileId?.let { fileId ->
            VideoPlayerOverlay(fileId = fileId, onClose = { videoFileId = null })
        }

        pdfFileId?.let { fileId ->
            PdfViewerOverlay(fileId = fileId, onClose = { pdfFileId = null })
        }
    }
}

@Composable
private fun ComposerRow(onKeyboard: () -> Unit, onMic: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction(onClick = onKeyboard) {
            Icon(Icons.Rounded.Keyboard, contentDescription = "Type a message", modifier = Modifier.size(20.dp))
        }
        RoundAction(onClick = onMic, background = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Rounded.Mic, contentDescription = "Record voice message", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoundAction(
    onClick: () -> Unit,
    background: Color = TeleWearColors.incomingBubble,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MessageBubble(
    msg: MsgItem,
    isGroup: Boolean,
    senderName: String?,
    readByPeer: Boolean,
    onPhotoClick: (String) -> Unit,
    onPlayVideo: (Int) -> Unit,
    onOpenPdf: (Int) -> Unit,
) {
    val fontScale = LocalFontScale.current
    val out = msg.isOutgoing
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = if (out) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .widthIn(max = 196.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomEnd = if (out) 4.dp else 14.dp,
                        bottomStart = if (out) 14.dp else 4.dp,
                    )
                )
                .background(if (out) TeleWearColors.outgoingBubble else TeleWearColors.incomingBubble)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            if (isGroup && !out && senderName != null) {
                Text(
                    senderName,
                    color = avatarColor(msg.senderUserId ?: 0L),
                    fontSize = 10.sp * fontScale,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (val c = msg.content) {
                is MsgContent.Text -> LinkedText(c.text, fontScale)
                is MsgContent.Photo -> PhotoContent(c, fontScale, onPhotoClick)
                is MsgContent.Playable -> PlayableRow(msg.id, c, fontScale)
                is MsgContent.Video -> VideoBubble(c, fontScale, onPlay = { onPlayVideo(c.fileId) })
                is MsgContent.Sticker -> StickerBubble(c)
                is MsgContent.Doc -> DocBubble(c, fontScale, onOpenPdf = { onOpenPdf(c.fileId) })
                is MsgContent.Other -> Text(
                    c.label,
                    fontSize = 12.sp * fontScale,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(formatTime(msg.date), fontSize = 8.sp, color = TeleWearColors.timestamp)
                if (out) {
                    val (icon, tint) = when {
                        msg.failed -> Icons.Rounded.ErrorOutline to TeleWearColors.recordRed
                        msg.pending -> Icons.Rounded.Schedule to TeleWearColors.timestamp
                        readByPeer -> Icons.Rounded.DoneAll to MaterialTheme.colorScheme.primary
                        else -> Icons.Rounded.Done to TeleWearColors.timestamp
                    }
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoContent(c: MsgContent.Photo, fontScale: Float, onPhotoClick: (String) -> Unit) {
    val path = rememberFilePath(c.fileId)
    val ratio = if (c.height > 0) (c.width.toFloat() / c.height).coerceIn(0.6f, 1.8f) else 1f
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141B20)),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = "Photo",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPhotoClick(path) },
                contentScale = ContentScale.Crop,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
    }
    if (c.caption.isNotEmpty()) {
        Text(
            c.caption,
            fontSize = 12.sp * fontScale,
            lineHeight = 15.sp * fontScale,
            color = TeleWearColors.onBubble,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun VoiceRecordOverlay(
    onDismiss: () -> Unit,
    onSend: (VoiceRecorder.Recording) -> Unit,
) {
    val graph = LocalAppGraph.current
    val recorder = graph.voiceRecorder
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) started = recorder.start().also { if (!it) onDismiss() }
        else onDismiss()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(started) {
        if (started) {
            while (isActive) {
                level = recorder.pollAmplitude()
                seconds = recorder.elapsedSec()
                delay(100)
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(1f + level * 0.35f)
                    .clip(CircleShape)
                    .background(TeleWearColors.recordRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Text(
                formatDuration(seconds),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TeleWearColors.incomingBubble)
                        .clickable {
                            recorder.cancel()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = TeleWearColors.recordRed, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            val rec = recorder.finish()
                            if (rec != null) onSend(rec) else onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
