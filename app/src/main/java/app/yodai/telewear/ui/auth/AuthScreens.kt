package app.yodai.telewear.ui.auth

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.yodai.telewear.telegram.AuthUi
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.rememberTextInputLauncher
import app.yodai.telewear.ui.theme.TeleWearColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun AuthFlow(state: AuthUi) {
    val graph = LocalAppGraph.current
    val error by graph.core.authError.collectAsState()

    when (state) {
        AuthUi.Starting, AuthUi.Connecting -> LoadingScreen()

        AuthUi.MissingApiCreds -> InfoScreen(
            title = "API keys missing",
            body = "Add telegram.api.id and telegram.api.hash to local.properties and rebuild.\n\nGet them free at my.telegram.org → API development tools.",
        )

        is AuthUi.Qr -> QrScreen(
            link = state.link,
            onUsePhone = { graph.core.usePhoneLogin() },
            onRefresh = { graph.core.refreshQr() },
        )

        AuthUi.PhoneEntry -> PromptScreen(
            title = "Phone number",
            hint = "International format, e.g. +49…",
            error = error,
            inputLabel = "Phone number",
            buttonText = "Enter number",
            onSubmit = { graph.core.submitPhone(it) },
            secondaryText = "Use QR instead",
            onSecondary = { graph.core.useQrLogin() },
        )

        is AuthUi.CodeEntry -> PromptScreen(
            title = "Login code",
            hint = "Sent to ${state.sentTo} — check Telegram on your phone",
            error = error,
            inputLabel = "Code",
            buttonText = "Enter code",
            onSubmit = { graph.core.submitCode(it) },
        )

        is AuthUi.PasswordEntry -> PromptScreen(
            title = "2FA password",
            hint = if (state.hint.isNotBlank()) "Hint: ${state.hint}" else "Your two-step verification password",
            error = error,
            inputLabel = "Password",
            buttonText = "Enter password",
            onSubmit = { graph.core.submitPassword(it) },
        )

        is AuthUi.Unsupported -> InfoScreen(title = "Can't continue", body = state.reason)

        AuthUi.Ready -> Unit
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InfoScreen(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * QR login: scan from the official Telegram app on the paired phone
 * (Settings → Devices → Link Desktop Device).
 *
 * QR tokens expire in ~30 s, and Wear OS suspends the app the moment the
 * screen sleeps — so this screen keeps the display awake, silently requests
 * a fresh token every time it resumes, and refreshes on tap. The new link
 * arrives as an auth-state update and the QR redraws in place.
 */
@Composable
private fun QrScreen(link: String, onUsePhone: () -> Unit, onRefresh: () -> Unit) {
    val qr = remember(link) { generateQr(link, 320) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Link with your phone", fontSize = 12.sp, textAlign = TextAlign.Center)
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .clickable(onClick = onRefresh)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = qr.asImageBitmap(), contentDescription = "Login QR code — tap to refresh", modifier = Modifier.fillMaxSize())
        }
        Text(
            "Telegram → Settings → Devices → Link Desktop Device",
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onUsePhone,
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            label = { Text("Use phone number", fontSize = 12.sp) },
        )
    }
}

@Composable
private fun PromptScreen(
    title: String,
    hint: String,
    error: String?,
    inputLabel: String,
    buttonText: String,
    onSubmit: (String) -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val launchInput = rememberTextInputLauncher(inputLabel) { onSubmit(it.trim()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            hint,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        if (error != null) {
            Text(
                error,
                fontSize = 10.sp,
                color = TeleWearColors.recordRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Button(
            onClick = launchInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(buttonText, fontSize = 12.sp) },
        )
        if (secondaryText != null && onSecondary != null) {
            Button(
                onClick = onSecondary,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text(secondaryText, fontSize = 12.sp) },
            )
        }
    }
}

private fun generateQr(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 0)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
}
