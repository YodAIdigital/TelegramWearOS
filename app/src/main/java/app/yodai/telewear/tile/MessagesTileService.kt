package app.yodai.telewear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.yodai.telewear.ui.MainActivity
import com.google.common.util.concurrent.ListenableFuture

/**
 * Glanceable Tile: total unread + the top chats, one bezel-swipe from the
 * watch face. Renders from [TileSnapshot] so it works even when the app
 * process was cold-started just for this request.
 */
class MessagesTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        val data = TileSnapshot.read(this)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(5 * 60_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(root(data))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        completer.set(tile)
        "tileRequest"
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        "tileResources"
    }

    private fun root(data: TileSnapshot.Data): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(clickable(openApp(chatId = null)))
            .addContent(
                text(
                    if (data.totalUnread > 0) "${data.totalUnread} unread" else "No unread",
                    sizeSp = 18f,
                    color = 0xFFFFFFFF.toInt(),
                )
            )
            .addContent(spacer(6f))

        if (data.top.isEmpty()) {
            column.addContent(text("Open YodChat", sizeSp = 12f, color = COLOR_MUTED))
        } else {
            data.top.forEach { entry ->
                val label = if (entry.unread > 0) "${entry.title}  ·  ${entry.unread}" else entry.title
                column.addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setModifiers(clickable(openApp(entry.chatId)))
                        .addContent(
                            text(
                                label.take(24),
                                sizeSp = 13f,
                                color = if (entry.unread > 0) COLOR_ACCENT else COLOR_MUTED,
                            )
                        )
                        .build()
                )
                column.addContent(spacer(4f))
            }
        }

        return column.build()
    }

    private fun openApp(chatId: Long?): ActionBuilders.LaunchAction {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (chatId != null) {
            activity.addKeyToExtraMapping(
                MainActivity.EXTRA_CHAT_ID,
                ActionBuilders.AndroidLongExtra.Builder().setValue(chatId).build(),
            )
        }
        return ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
    }

    private fun clickable(action: ActionBuilders.LaunchAction) = ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId("open")
                .setOnClick(action)
                .build()
        )
        .build()

    private fun text(value: String, sizeSp: Float, color: Int) = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setMaxLines(1)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(DimensionBuilders.sp(sizeSp))
                .setColor(argb(color))
                .build()
        )
        .build()

    private fun spacer(dp: Float) = LayoutElementBuilders.Spacer.Builder()
        .setHeight(DimensionBuilders.dp(dp))
        .build()

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val COLOR_ACCENT = 0xFF00E5A0.toInt()
        private const val COLOR_MUTED = 0xFF9AA6AD.toInt()
    }
}
