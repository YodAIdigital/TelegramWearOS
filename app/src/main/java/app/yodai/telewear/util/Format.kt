package app.yodai.telewear.util

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE")
private val dateFmt = DateTimeFormatter.ofPattern("dd.MM")

/** "14:32" for a unix timestamp, device timezone. */
fun formatTime(unixSeconds: Int): String {
    if (unixSeconds <= 0) return ""
    return Instant.ofEpochSecond(unixSeconds.toLong()).atZone(ZoneId.systemDefault()).format(timeFmt)
}

/** Chat-list style: time today, weekday this week, date otherwise. */
fun formatChatDate(unixSeconds: Int): String {
    if (unixSeconds <= 0) return ""
    val zdt = Instant.ofEpochSecond(unixSeconds.toLong()).atZone(ZoneId.systemDefault())
    val today = LocalDate.now(ZoneId.systemDefault())
    val date = zdt.toLocalDate()
    return when {
        date == today -> zdt.format(timeFmt)
        date.isAfter(today.minusDays(7)) -> zdt.format(dayFmt)
        else -> zdt.format(dateFmt)
    }
}

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun initials(title: String): String {
    val parts = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Telegram's avatar placeholder palette, keyed by peer id. */
private val avatarColors = listOf(
    Color(0xFFE17076), // red
    Color(0xFF7BC862), // green
    Color(0xFFE5CA77), // yellow
    Color(0xFF65AADD), // blue
    Color(0xFFA695E7), // purple
    Color(0xFFEE7AAE), // pink
    Color(0xFF6EC9CB), // cyan
    Color(0xFFFAA774), // orange
)

fun avatarColor(seed: Long): Color = avatarColors[(abs(seed) % avatarColors.size).toInt()]
