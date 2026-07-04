package app.yodai.telewear.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.yodai.telewear.telegram.MsgContent
import app.yodai.telewear.ui.theme.TeleWearColors

/**
 * Poll bubble: question, tappable options with result bars, vote count.
 * Tapping an option votes; tapping your chosen option retracts (open polls only).
 */
@Composable
fun PollContent(c: MsgContent.Poll, fontScale: Float, onVote: (index: Int, retract: Boolean) -> Unit) {
    Column(modifier = Modifier.padding(top = 2.dp)) {
        Text(
            c.question,
            fontSize = 12.sp * fontScale,
            fontWeight = FontWeight.SemiBold,
            color = TeleWearColors.onBubble,
        )
        Text(
            buildString {
                append(if (c.isQuiz) "Quiz" else "Poll")
                append(" · ${c.totalVotes} ")
                append(if (c.totalVotes == 1) "vote" else "votes")
                if (c.closed) append(" · closed")
            },
            fontSize = 9.sp,
            color = TeleWearColors.timestamp,
            modifier = Modifier.padding(top = 1.dp, bottom = 3.dp),
        )
        c.options.forEachIndexed { index, option ->
            val showResults = c.voted || c.closed
            val fillColor = if (option.chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else Color(0x26FFFFFF)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x1AFFFFFF))
                    .drawBehind {
                        // Result bar, proportional to the vote share.
                        if (showResults) {
                            drawRect(
                                color = fillColor,
                                size = Size(size.width * option.percent / 100f, size.height),
                            )
                        }
                    }
                    .clickable(enabled = !c.closed) { onVote(index, option.chosen) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    if (option.chosen) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Your vote",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 3.dp)
                                .size(11.dp),
                        )
                    }
                    Text(
                        option.text,
                        fontSize = 11.sp * fontScale,
                        color = TeleWearColors.onBubble,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    if (showResults) {
                        Text(
                            "${option.percent}%",
                            fontSize = 9.sp,
                            color = TeleWearColors.timestamp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Location/venue bubble — opens Maps on the watch, falls back to the phone. */
@Composable
fun LocationContent(c: MsgContent.Location, fontScale: Float) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { openLocation(context, c.lat, c.lon) }
            .padding(2.dp),
    ) {
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(
                (c.title ?: if (c.live) "Live location" else "Location"),
                fontSize = 11.sp * fontScale,
                color = TeleWearColors.onBubble,
            )
            Text(
                c.address ?: "%.5f, %.5f".format(c.lat, c.lon),
                fontSize = 9.sp,
                color = TeleWearColors.timestamp,
            )
        }
    }
}

@Composable
fun ContactContent(c: MsgContent.Contact, fontScale: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 2.dp)
            .padding(2.dp),
    ) {
        Icon(
            Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(c.name, fontSize = 11.sp * fontScale, color = TeleWearColors.onBubble)
            Text(c.phone, fontSize = 9.sp, color = TeleWearColors.timestamp)
        }
    }
}

private fun openLocation(context: Context, lat: Double, lon: Double) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))
    try {
        context.startActivity(geo)
    } catch (e: ActivityNotFoundException) {
        // No maps app on the watch — send it to the phone through the link path.
        openLink(context, "https://maps.google.com/?q=$lat,$lon")
    }
}
