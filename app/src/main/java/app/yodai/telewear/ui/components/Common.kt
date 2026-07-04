package app.yodai.telewear.ui.components

import android.Manifest
import android.app.RemoteInput
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import app.yodai.telewear.AppGraph
import app.yodai.telewear.util.avatarColor
import app.yodai.telewear.util.initials
import coil.compose.AsyncImage
import java.io.File

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
val LocalFontScale = staticCompositionLocalOf { 1.0f }

/** Resolves a TDLib file id to a local path, downloading it if needed. */
@Composable
fun rememberFilePath(fileId: Int?): String? {
    val graph = LocalAppGraph.current
    var path by remember(fileId) { mutableStateOf<String?>(null) }
    LaunchedEffect(fileId) {
        path = fileId?.let { graph.files.path(it) }
    }
    return path
}

/** Chat/user avatar: photo if available, otherwise Telegram-style colored initials. */
@Composable
fun ChatAvatar(photoFileId: Int?, title: String, colorSeed: Long, size: Dp) {
    val path = rememberFilePath(photoFileId)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (path == null) avatarColor(colorSeed) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials(title),
                color = Color.White,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private const val KEY_TEXT_INPUT = "text_input"

/**
 * Wear OS text entry: launches the system RemoteInput sheet, which offers
 * voice dictation, the on-watch keyboard, and emoji — no custom composer needed.
 */
@Composable
fun rememberTextInputLauncher(label: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(KEY_TEXT_INPUT) }
            ?.toString()
        android.util.Log.i("TextInput", "result code=${result.resultCode} hasData=${result.data != null} textLen=${text?.length ?: -1}")
        if (!text.isNullOrBlank()) onResult(text)
    }
    return remember(label) {
        {
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            val remoteInputs = listOf(
                RemoteInput.Builder(KEY_TEXT_INPUT).setLabel(label).build()
            )
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
            try {
                launcher.launch(intent)
            } catch (e: Exception) {
                android.util.Log.e("TextInput", "RemoteInput activity not available", e)
            }
        }
    }
}

/** Asks for POST_NOTIFICATIONS once (API 33+). */
@Composable
fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
