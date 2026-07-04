package app.yodai.telewear.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.yodai.telewear.TeleWearApp
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import kotlinx.coroutines.launch

/** Handles inline Reply and Mark-read actions from message notifications. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as TeleWearApp).graph
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L)
        if (chatId == 0L) return

        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(MessageNotifier.KEY_REPLY)
                    ?.toString()
                    ?.trim()
                if (text.isNullOrEmpty()) return
                val pending = goAsync()
                graph.appScope.launch {
                    try {
                        graph.core.client.sendMessage(
                            chatId = chatId,
                            inputMessageContent = InputMessageText(
                                text = FormattedText(text = text, entities = emptyArray()),
                                linkPreviewOptions = null,
                                clearDraft = true,
                            ),
                        )
                    } finally {
                        MessageNotifier.cancel(context, chatId)
                        pending.finish()
                    }
                }
            }

            ACTION_MARK_READ -> {
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0L)
                val pending = goAsync()
                graph.appScope.launch {
                    try {
                        graph.core.client.viewMessages(
                            chatId = chatId,
                            messageIds = longArrayOf(messageId),
                            source = null,
                            forceRead = true,
                        )
                    } finally {
                        MessageNotifier.cancel(context, chatId)
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "app.yodai.telewear.action.REPLY"
        const val ACTION_MARK_READ = "app.yodai.telewear.action.MARK_READ"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_MESSAGE_ID = "message_id"

        fun reply(context: Context, chatId: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_CHAT_ID, chatId)

        fun markRead(context: Context, chatId: Long, messageId: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
    }
}
