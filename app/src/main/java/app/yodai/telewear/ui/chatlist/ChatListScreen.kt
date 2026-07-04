package app.yodai.telewear.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.yodai.telewear.AppGraph
import app.yodai.telewear.telegram.ChatItem
import app.yodai.telewear.ui.components.ChatAvatar
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.LocalFontScale
import app.yodai.telewear.ui.components.NotificationPermissionEffect
import app.yodai.telewear.util.formatChatDate

class ChatListViewModel(graph: AppGraph) : ViewModel() {
    val chats = graph.chats.chatList
    val isConnecting = graph.core.isConnecting
}

@Composable
fun ChatListScreen(onOpenChat: (Long) -> Unit, onSettings: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: ChatListViewModel = viewModel { ChatListViewModel(graph) }
    val chats by vm.chats.collectAsState()
    val connecting by vm.isConnecting.collectAsState()
    val listState = rememberScalingLazyListState()

    NotificationPermissionEffect()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text(if (connecting) "Connecting…" else "Chats")
                }
            }
            if (chats.isEmpty()) {
                item {
                    Text(
                        "No chats yet.\nMessages sync once connected.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(chats, key = { it.id }) { chat ->
                ChatRow(chat, onClick = { onOpenChat(chat.id) })
            }
            item {
                Button(
                    onClick = onSettings,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Settings", fontSize = 13.sp) },
                )
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatItem, onClick: () -> Unit) {
    val fontScale = LocalFontScale.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { ChatAvatar(chat.photoFileId, chat.title, chat.id, 32.dp) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    chat.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp * fontScale,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.muted) {
                    Icon(
                        Icons.Rounded.NotificationsOff,
                        contentDescription = "Muted",
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        secondaryLabel = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    chat.preview.replace('\n', ' '),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp * fontScale,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.unread > 0) {
                    UnreadBadge(chat.unread)
                } else if (chat.date > 0) {
                    Text(
                        formatChatDate(chat.date),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99" else count.toString(),
            fontSize = 9.sp,
            color = Color.White,
        )
    }
}
