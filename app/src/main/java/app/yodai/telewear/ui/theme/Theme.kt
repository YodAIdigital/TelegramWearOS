package app.yodai.telewear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * YodAI Digital brand palette (yodaidigital.com) on Wear Material 3:
 * signature orange #F97316 on warm near-black, cream text.
 * Watch backgrounds stay pure black (OLED battery + design guidance).
 */
object TeleWearColors {
    val accent = Color(0xFFF97316)        // --accent
    val accentDim = Color(0xFFEA580C)     // --accent2
    val accentLight = Color(0xFFFDBA74)
    val outgoingBubble = Color(0xFF6D3A10) // burnt-orange bubble for own messages
    val incomingBubble = Color(0xFF221B14) // --bg-warm, lifted for bubbles
    val onBubble = Color(0xFFFAF8F5)       // --text
    val timestamp = Color(0xB3FAF8F5)
    val recordRed = Color(0xFFEF4444)      // site error red
}

private val WearColorScheme = ColorScheme(
    primary = TeleWearColors.accent,
    primaryDim = TeleWearColors.accentDim,
    primaryContainer = Color(0xFF4A2A0E),
    onPrimary = Color(0xFF2A1200),
    onPrimaryContainer = Color(0xFFFFDCC1),
    secondary = TeleWearColors.accentLight,
    secondaryDim = Color(0xFFE39A54),
    secondaryContainer = Color(0xFF2A221A),
    onSecondary = Color(0xFF3A2410),
    onSecondaryContainer = Color(0xFFF0E0CC),
)

@Composable
fun TeleWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content,
    )
}
