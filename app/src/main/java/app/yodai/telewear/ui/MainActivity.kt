package app.yodai.telewear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.yodai.telewear.AppGraph
import app.yodai.telewear.TeleWearApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as TeleWearApp).graph
        consumeIntent(intent, graph)
        setContent {
            TeleWearRoot(graph)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntent(intent, (application as TeleWearApp).graph)
    }

    private fun consumeIntent(intent: Intent?, graph: AppGraph) {
        val chatId = intent?.getLongExtra(EXTRA_CHAT_ID, 0L) ?: 0L
        if (chatId != 0L) graph.pendingOpenChatId.value = chatId
    }

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
    }
}
