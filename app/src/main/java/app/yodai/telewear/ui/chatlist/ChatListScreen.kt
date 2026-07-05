package app.yodai.telewear.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
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
import androidx.lifecycle.viewModelScope
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
import app.yodai.telewear.ui.components.rememberTextInputLauncher
import app.yodai.telewear.util.formatChatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(private val graph: AppGraph) : ViewModel() {
    val chats = graph.chats.chatList
    val isConnecting = graph.core.isConnecting
    val folders = graph.chats.folders
    val selectedFolder = graph.chats.selectedFolderId

    /** null = not searching; empty list = no matches. */
    val searchResults = MutableStateFlow<List<ChatItem>?>(null)

    fun selectFolder(id: Int?) = graph.chats.selectFolder(id)

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch { searchResults.value = graph.chats.searchChats(query.trim()) }
    }

    fun clearSearch() {
        searchResults.value = null
    }
}

@Composable
fun ChatListScreen(onOpenChat: (Long) -> Unit, onSettings: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: ChatListViewModel = viewModel { ChatListViewModel(graph) }
    val chats by vm.chats.collectAsState()
    val connecting by vm.isConnecting.collectAsState()
    val folders by vm.folders.collectAsState()
    val selectedFolder by vm.selectedFolder.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val listState = rememberScalingLazyListState()

    NotificationPermissionEffect()

    val launchSearch = rememberTextInputLauncher("Search chats") { vm.search(it) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                searchResults != null -> "Search"
                                connecting -> "Connecting…"
                                else -> "Chats"
                            }
                        )
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search chats",
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { launchSearch() },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val results = searchResults
            if (results != null) {
                item {
                    Button(
                        onClick = { vm.clearSearch() },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Back to chats", fontSize = 12.sp) },
                    )
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            "No matches",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(results, key = { it.id }) { chat ->
                    ChatRow(chat, onClick = { onOpenChat(chat.id) })
                }
            } else {
                if (folders.isNotEmpty()) {
                    item {
                        FolderChips(
                            folders = folders,
                            selected = selectedFolder,
                            onSelect = { vm.selectFolder(it) },
                        )
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
}

/** "All" + the user's Telegram folders as a horizontally scrollable chip row. */
@Composable
private fun FolderChips(
    folders: List<Pair<Int, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        FolderChip("All", selected == null) { onSelect(null) }
        folders.forEach { (id, name) ->
            FolderChip(name, selected == id) { onSelect(id) }
        }
    }
}

@Composable
private fun FolderChip(name: String, active: Boolean, onClick: () -> Unit) {
    Text(
        name,
        fontSize = 10.sp,
        maxLines = 1,
        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChatRow(chat: ChatItem, onClick: () -> Unit) {
    val fontScale = LocalFontScale.current
    // Forum groups show the last message's sender avatar + topic name in place
    // of the group photo + text preview.
    val isTopic = chat.topicName != null
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = {
            if (isTopic) {
                ChatAvatar(chat.senderPhotoFileId, chat.senderName ?: chat.title, chat.senderUserId ?: chat.id, 32.dp)
            } else {
                ChatAvatar(chat.photoFileId, chat.title, chat.id, 32.dp)
            }
        },
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
                if (isTopic) {
                    Icon(
                        Icons.Rounded.Forum,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(11.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    if (isTopic) chat.topicName!! else chat.preview.replace('\n', ' '),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp * fontScale,
                    color = if (isTopic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
