package app.yodai.telewear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.yodai.telewear.TeleWearApp
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import kotlinx.coroutines.launch

/**
 * Developer self-test hook (not exported; reachable only from the adb shell):
 *
 *   adb shell am broadcast -n app.yodai.telewear/.debug.DebugSendReceiver --es text "ping"
 *
 * Sends a text message to the user's own Saved Messages chat and logs the
 * full round trip — proves out TDLib text sending and the update stream
 * without messaging anyone else.
 */
class DebugSendReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as TeleWearApp).graph
        val text = intent.getStringExtra("text") ?: "YodChat self-test"
        val pending = goAsync()
        graph.appScope.launch {
            try {
                val client = graph.core.client
                val me = when (val r = client.getMe()) {
                    is TdlResult.Success -> r.result
                    is TdlResult.Failure -> {
                        Log.e(TAG, "getMe FAILED: ${r.code} ${r.message}")
                        return@launch
                    }
                }
                val chat = when (val r = client.createPrivateChat(userId = me.id, force = true)) {
                    is TdlResult.Success -> r.result
                    is TdlResult.Failure -> {
                        Log.e(TAG, "createPrivateChat FAILED: ${r.code} ${r.message}")
                        return@launch
                    }
                }
                when (val r = client.sendMessage(
                    chatId = chat.id,
                    inputMessageContent = InputMessageText(
                        text = FormattedText(text = text, entities = emptyArray()),
                        linkPreviewOptions = null,
                        clearDraft = false,
                    ),
                )) {
                    is TdlResult.Success ->
                        Log.i(TAG, "sendMessage OK: chat=${chat.id} tempId=${r.result.id} state=${r.result.sendingState?.let { it::class.simpleName }}")
                    is TdlResult.Failure ->
                        Log.e(TAG, "sendMessage FAILED: ${r.code} ${r.message}")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugSend"
    }
}
