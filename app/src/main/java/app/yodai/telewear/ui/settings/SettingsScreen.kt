package app.yodai.telewear.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import app.yodai.telewear.AppGraph
import app.yodai.telewear.BuildConfig
import app.yodai.telewear.settings.AppSettings
import app.yodai.telewear.settings.FONT_SCALE_STEPS
import app.yodai.telewear.telegram.getOrNull
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.theme.TeleWearColors
import dev.g000sha256.tdl.dto.FileType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {

    val settings = graph.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setNotifications(v: Boolean) = viewModelScope.launch { graph.settings.setNotificationsEnabled(v) }
    fun setShowPreview(v: Boolean) = viewModelScope.launch { graph.settings.setShowPreview(v) }
    fun setKeepAlive(v: Boolean) = viewModelScope.launch { graph.settings.setKeepAlive(v) }

    fun cycleFontScale() = viewModelScope.launch {
        val current = settings.value.fontScale
        val idx = FONT_SCALE_STEPS.indexOfFirst { kotlin.math.abs(it.first - current) < 0.01f }
        val next = FONT_SCALE_STEPS[(idx + 1).mod(FONT_SCALE_STEPS.size)]
        graph.settings.setFontScale(next.first)
    }

    /** Persists the default and applies it to the live player immediately. */
    fun setPlaybackSpeed(v: Float) = viewModelScope.launch {
        graph.settings.setPlaybackSpeed(v)
        graph.voicePlayer.setSpeed(v)
    }

    // ---- storage ----

    val mediaCacheBytes = MutableStateFlow<Long?>(null)
    val clearingCache = MutableStateFlow(false)

    init {
        refreshStorage()
    }

    fun refreshStorage() = viewModelScope.launch {
        mediaCacheBytes.value = graph.core.client.getStorageStatisticsFast().getOrNull()?.filesSize
    }

    /**
     * Deletes all downloaded media (photos, voice/audio, videos, documents)
     * through TDLib's storage optimizer, which keeps its database consistent —
     * anything still needed simply re-downloads on view.
     */
    fun clearDownloads() = viewModelScope.launch {
        if (clearingCache.value) return@launch
        clearingCache.value = true
        try {
            graph.core.client.optimizeStorage(
                size = 0,
                ttl = 0,
                count = 0,
                immunityDelay = 0,
                fileTypes = emptyArray<FileType>(),
                chatIds = LongArray(0),
                excludeChatIds = LongArray(0),
                returnDeletedFileStatistics = false,
                chatLimit = 100,
            )
            graph.files.clearCache()
            // Our own outgoing voice recordings live in the app cache dir.
            graph.appContext.cacheDir.listFiles()
                ?.filter { it.name.startsWith("voice_") }
                ?.forEach { it.delete() }
            refreshStorage()
        } finally {
            clearingCache.value = false
        }
    }

    fun logout() = graph.core.logout()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824f)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun formatSpeedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}×" else "${"%.2f".format(s).trimEnd('0').trimEnd('.')}×"

@Composable
fun SettingsScreen() {
    val graph = LocalAppGraph.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(graph) }
    val settings by vm.settings.collectAsState()
    val listState = rememberScalingLazyListState()

    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(confirmLogout) {
        if (confirmLogout) {
            delay(3000)
            confirmLogout = false
        }
    }

    val fontLabel = FONT_SCALE_STEPS
        .firstOrNull { kotlin.math.abs(it.first - settings.fontScale) < 0.01f }?.second
        ?: "Default"

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("Settings") } }

            item {
                SwitchButton(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { vm.setNotifications(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notifications", fontSize = 13.sp) },
                    secondaryLabel = { Text("From this app", fontSize = 10.sp) },
                )
            }

            item {
                SwitchButton(
                    checked = settings.showPreview,
                    onCheckedChange = { vm.setShowPreview(it) },
                    enabled = settings.notificationsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Show preview", fontSize = 13.sp) },
                    secondaryLabel = { Text("Message text in alerts", fontSize = 10.sp) },
                )
            }

            item {
                Button(
                    onClick = { vm.cycleFontScale() },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Font size: $fontLabel", fontSize = 13.sp) },
                    secondaryLabel = {
                        Text("Aa preview", fontSize = 12.sp * settings.fontScale)
                    },
                )
            }

            item {
                SwitchButton(
                    checked = settings.keepAlive,
                    onCheckedChange = { vm.setKeepAlive(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stay connected", fontSize = 13.sp) },
                    secondaryLabel = { Text("Background sync — uses battery", fontSize = 10.sp) },
                )
            }

            item { ListHeader { Text("Playback") } }

            item {
                Slider(
                    value = settings.playbackSpeed,
                    onValueChange = { vm.setPlaybackSpeed(it) },
                    steps = 7,
                    valueRange = 0.5f..2.5f,
                    segmented = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Default speed ${formatSpeedLabel(settings.playbackSpeed)}",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = TeleWearColors.accentLight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { ListHeader { Text("Storage") } }

            item {
                val cacheBytes by vm.mediaCacheBytes.collectAsState()
                val clearing by vm.clearingCache.collectAsState()
                Button(
                    onClick = { vm.clearDownloads() },
                    enabled = !clearing,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when {
                                clearing -> "Clearing…"
                                cacheBytes != null -> "Media cache: ${formatBytes(cacheBytes!!)}"
                                else -> "Media cache"
                            },
                            fontSize = 13.sp,
                        )
                    },
                    secondaryLabel = { Text("Tap to clear downloads", fontSize = 10.sp) },
                )
            }

            item {
                Button(
                    onClick = {
                        if (confirmLogout) {
                            confirmLogout = false
                            vm.logout()
                        } else {
                            confirmLogout = true
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    label = {
                        Text(
                            if (confirmLogout) "Tap again to confirm" else "Log out",
                            fontSize = 13.sp,
                            color = TeleWearColors.recordRed,
                        )
                    },
                )
            }

            item {
                Text(
                    "TeleWear ${BuildConfig.VERSION_NAME}\nUnofficial Telegram client",
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
