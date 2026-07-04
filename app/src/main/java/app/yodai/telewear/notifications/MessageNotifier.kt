package app.yodai.telewear.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.yodai.telewear.R
import app.yodai.telewear.TeleWearApp
import app.yodai.telewear.settings.SettingsRepository
import app.yodai.telewear.settings.quickReplyList
import app.yodai.telewear.telegram.ChatRepository
import app.yodai.telewear.telegram.TelegramCore
import app.yodai.telewear.telegram.previewText
import app.yodai.telewear.telegram.toItem
import app.yodai.telewear.ui.MainActivity
import dev.g000sha256.tdl.dto.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Posts message notifications while the app process is alive (foreground use or the
 * optional keep-alive service). When this app is not running, the phone's official
 * Telegram app still bridges its notifications to the watch — see README.
 */
class MessageNotifier(
    private val context: Context,
    private val core: TelegramCore,
    private val chats: ChatRepository,
    private val settings: SettingsRepository,
    private val activeChatId: StateFlow<Long?>,
    private val phoneLink: PhoneLinkMonitor,
    scope: CoroutineScope,
) {

    init {
        scope.launch {
            core.updates { it.newMessageUpdates }.collect { u ->
                android.util.Log.d("MessageNotifier", "UpdateNewMessage chat=${u.message.chatId} id=${u.message.id} out=${u.message.isOutgoing}")
                runCatching { onMessage(u.message) }
            }
        }
    }

    @SuppressLint("MissingPermission") // checked via ContextCompat below
    private suspend fun onMessage(m: Message) {
        if (m.isOutgoing) return
        if (activeChatId.value == m.chatId) return
        val s = settings.flow.first()
        if (!s.notificationsEnabled) return
        // Smart dedupe: while the phone is connected, its Telegram app already
        // bridges this message's notification to the watch.
        if (s.smartDedupe && phoneLink.phoneConnected.value) return
        val chat = chats.chatItem(m.chatId) ?: return
        if (chat.muted) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val item = m.toItem()
        val senderName = if (chat.isGroup && item.senderUserId != null) {
            chats.displayName(item.senderUserId)
        } else {
            chat.title
        }
        val text = if (s.showPreview) item.content.previewText() else "New message"

        val sender = Person.Builder().setName(senderName).setKey(m.chatId.toString()).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .setConversationTitle(if (chat.isGroup) chat.title else null)
            .addMessage(text, item.date * 1000L, sender)

        val openIntent = PendingIntent.getActivity(
            context,
            requestCode(m.chatId, 0),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MainActivity.EXTRA_CHAT_ID, m.chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Reply",
            PendingIntent.getBroadcast(
                context,
                requestCode(m.chatId, 1),
                NotificationActionReceiver.reply(context, m.chatId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
            .addRemoteInput(
                RemoteInput.Builder(KEY_REPLY)
                    .setLabel("Reply")
                    // One-tap chips on the notification, straight from Settings.
                    .setChoices(
                        if (s.quickRepliesEnabled) s.quickReplyList().toTypedArray() else emptyArray()
                    )
                    .build()
            )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
            .build()

        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Mark read",
            PendingIntent.getBroadcast(
                context,
                requestCode(m.chatId, 2),
                NotificationActionReceiver.markRead(context, m.chatId, m.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()

        val notification = NotificationCompat.Builder(context, TeleWearApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentIntent(openIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(m.chatId), notification)
    }

    companion object {
        const val KEY_REPLY = "key_reply"

        fun notificationId(chatId: Long): Int = (chatId xor (chatId ushr 32)).toInt()

        private fun requestCode(chatId: Long, slot: Int): Int = notificationId(chatId) * 4 + slot

        fun cancel(context: Context, chatId: Long) {
            NotificationManagerCompat.from(context).cancel(notificationId(chatId))
        }
    }
}
