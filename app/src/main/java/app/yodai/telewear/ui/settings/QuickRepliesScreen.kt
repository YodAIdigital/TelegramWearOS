package app.yodai.telewear.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import app.yodai.telewear.AppGraph
import app.yodai.telewear.settings.AppSettings
import app.yodai.telewear.settings.quickReplyList
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.rememberTextInputLauncher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_REPLIES = 6

class QuickRepliesViewModel(private val graph: AppGraph) : ViewModel() {
    val settings = graph.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setEnabled(v: Boolean) = viewModelScope.launch { graph.settings.setQuickRepliesEnabled(v) }

    fun add(text: String) = mutate { it + text }

    fun edit(index: Int, text: String) = mutate { list ->
        list.mapIndexed { i, old -> if (i == index) text else old }
    }

    fun delete(index: Int) = mutate { it.filterIndexed { i, _ -> i != index } }

    private fun mutate(transform: (List<String>) -> List<String>) = viewModelScope.launch {
        val current = settings.value.quickReplyList()
        val next = transform(current)
            .map { it.trim().replace(";", ",") } // ";" is the storage separator
            .filter { it.isNotEmpty() }
            .take(MAX_REPLIES)
        graph.settings.setQuickReplies(next.joinToString(";"))
    }
}

/** Editor for one-tap replies: toggle, edit/delete each, add new. */
@Composable
fun QuickRepliesScreen() {
    val graph = LocalAppGraph.current
    val vm: QuickRepliesViewModel = viewModel { QuickRepliesViewModel(graph) }
    val settings by vm.settings.collectAsState()
    val replies = settings.quickReplyList()
    val listState = rememberScalingLazyListState()

    val addLauncher = rememberTextInputLauncher("New quick reply") { vm.add(it) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("Quick replies") } }

            item {
                SwitchButton(
                    checked = settings.quickRepliesEnabled,
                    onCheckedChange = { vm.setEnabled(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Show quick replies", fontSize = 13.sp) },
                    secondaryLabel = { Text("In chats and notifications", fontSize = 10.sp) },
                )
            }

            items(replies.size) { index ->
                ReplyRow(
                    text = replies[index],
                    enabled = settings.quickRepliesEnabled,
                    onEdit = { new -> vm.edit(index, new) },
                    onDelete = { vm.delete(index) },
                )
            }

            if (replies.size < MAX_REPLIES) {
                item {
                    Button(
                        onClick = addLauncher,
                        enabled = settings.quickRepliesEnabled,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Add reply", fontSize = 13.sp) },
                    )
                }
            }

            item {
                Text(
                    "Tap a reply to rewrite it.\nUp to $MAX_REPLIES replies.",
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ReplyRow(
    text: String,
    enabled: Boolean,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val editLauncher = rememberTextInputLauncher("Rewrite: $text") { onEdit(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Button(
            onClick = editLauncher,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.weight(1f),
            label = {
                Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = enabled, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Delete reply",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
