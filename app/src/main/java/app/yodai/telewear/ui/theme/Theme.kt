package app.yodai.telewear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Telegram-flavored dark palette on Wear Material 3.
 * Watch backgrounds stay pure black (OLED battery + design guidance).
 */
object TeleWearColors {
    val telegramBlue = Color(0xFF4FAEF8)
    val telegramBlueDim = Color(0xFF2E7CC0)
    val outgoingBubble = Color(0xFF2D5D82)
    val incomingBubble = Color(0xFF232C33)
    val onBubble = Color(0xFFF2F6F9)
    val timestamp = Color(0xB3FFFFFF)
    val recordRed = Color(0xFFE85C5C)
}

private val WearColorScheme = ColorScheme(
    primary = TeleWearColors.telegramBlue,
    primaryDim = TeleWearColors.telegramBlueDim,
    primaryContainer = Color(0xFF1C4A6B),
    onPrimary = Color(0xFF00344E),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFF9CCBEF),
    secondaryDim = Color(0xFF6FA3C8),
    secondaryContainer = Color(0xFF243845),
    onSecondary = Color(0xFF0B3349),
    onSecondaryContainer = Color(0xFFD3E9F9),
)

@Composable
fun TeleWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content,
    )
}
