package app.yodai.telewear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.yodai.telewear.R
import app.yodai.telewear.tile.TileSnapshot
import app.yodai.telewear.ui.MainActivity

/** Watch-face complication showing the Telegram unread count; tap opens the app. */
class UnreadComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build(3) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        if (request.complicationType == ComplicationType.SHORT_TEXT) {
            build(TileSnapshot.read(this).totalUnread)
        } else null

    private fun build(unread: Int): ComplicationData {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(if (unread > 99) "99+" else unread.toString()).build(),
            contentDescription = PlainComplicationText.Builder("$unread unread Telegram messages").build(),
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_notification)).build()
            )
            .setTapAction(tap)
            .build()
    }
}
