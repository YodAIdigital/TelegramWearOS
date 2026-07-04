package app.yodai.telewear.tile

import android.content.Context
import app.yodai.telewear.telegram.ChatItem

/**
 * Last-known chat list summary, persisted to SharedPreferences so the Tile and
 * complication render instantly on cold process starts (TDLib takes seconds to
 * come up — services can't wait for it).
 *
 * Rows are newline-separated, fields tab-separated; both characters are
 * scrubbed from titles before writing.
 */
object TileSnapshot {

    data class Entry(val chatId: Long, val title: String, val unread: Int)
    data class Data(val totalUnread: Int, val top: List<Entry>)

    private const val PREFS = "tile_snapshot"
    private const val KEY_TOTAL = "total"
    private const val KEY_TOP = "top"
    private const val ROW_SEP = '\n'
    private const val FIELD_SEP = '\t'

    fun write(context: Context, chats: List<ChatItem>) {
        val top = chats.filter { it.unread > 0 }.take(3).ifEmpty { chats.take(3) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TOTAL, chats.sumOf { it.unread })
            .putString(
                KEY_TOP,
                top.joinToString(ROW_SEP.toString()) { c ->
                    listOf(
                        c.id.toString(),
                        c.title.replace(ROW_SEP, ' ').replace(FIELD_SEP, ' '),
                        c.unread.toString(),
                    ).joinToString(FIELD_SEP.toString())
                },
            )
            .apply()
    }

    fun read(context: Context): Data {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val top = p.getString(KEY_TOP, "").orEmpty()
            .split(ROW_SEP)
            .mapNotNull { row ->
                val f = row.split(FIELD_SEP)
                if (f.size == 3) {
                    Entry(
                        chatId = f[0].toLongOrNull() ?: return@mapNotNull null,
                        title = f[1],
                        unread = f[2].toIntOrNull() ?: 0,
                    )
                } else null
            }
        return Data(totalUnread = p.getInt(KEY_TOTAL, 0), top = top)
    }
}
