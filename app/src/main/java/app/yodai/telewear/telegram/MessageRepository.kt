package app.yodai.telewear.telegram

import android.util.Log
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class MessageRepository(private val core: TelegramCore) {
    /** One live thread per open chat screen; collectors run on the caller's scope. */
    fun openThread(chatId: Long, scope: CoroutineScope): ChatThread = ChatThread(chatId, core, scope)
}

/**
 * Live view of a single chat: newest-first message list fed by getChatHistory pages
 * and kept fresh by TDLib updates. Lifetime is tied to the ViewModel scope passed in.
 */
class ChatThread(
    val chatId: Long,
    private val core: TelegramCore,
    private val scope: CoroutineScope,
) {

    private val raw = MutableStateFlow<List<MsgItem>>(emptyList()) // sorted by id desc = newest first
    val messages: StateFlow<List<MsgItem>> = raw

    /** Peer has read everything up to this outgoing message id (drives ✓✓). */
    val lastReadOutbox = MutableStateFlow(0L)
    val loadingOlder = MutableStateFlow(false)

    /** Human-readable error of the most recent failed send (shown as a banner). */
    val lastSendError = MutableStateFlow<String?>(null)

    private var fullyLoaded = false
    private val lock = Any()

    init {
        scope.launch {
            core.client.openChat(chatId)
            core.client.getChat(chatId).getOrNull()?.let { lastReadOutbox.value = it.lastReadOutboxMessageId }
            loadMore()
            // Mark the initially visible history as read.
            raw.value.filter { !it.isOutgoing }.map { it.id }.toLongArray().let {
                if (it.isNotEmpty()) markRead(it)
            }
        }

        scope.launch {
            core.updates { it.newMessageUpdates }
                .filter { it.message.chatId == chatId }
                .collect { u ->
                    Log.d(TAG, "UpdateNewMessage chat=$chatId id=${u.message.id} out=${u.message.isOutgoing}")
                    insert(u.message.toItem())
                    if (!u.message.isOutgoing) markRead(longArrayOf(u.message.id))
                }
        }
        scope.launch {
            core.updates { it.messageSendSucceededUpdates }
                .filter { it.message.chatId == chatId }
                .collect { u ->
                    raw.value = raw.value
                        .map { if (it.id == u.oldMessageId) u.message.toItem() else it }
                        .sortedByDescending { it.id }
                }
        }
        scope.launch {
            core.updates { it.messageSendFailedUpdates }
                .filter { it.message.chatId == chatId }
                .collect { u ->
                    raw.value = raw.value.map { if (it.id == u.oldMessageId) u.message.toItem() else it }
                }
        }
        scope.launch {
            core.updates { it.messageContentUpdates }
                .filter { it.chatId == chatId }
                .collect { u ->
                    raw.value = raw.value.map {
                        if (it.id == u.messageId) it.copy(content = u.newContent.toMsgContent()) else it
                    }
                }
        }
        scope.launch {
            core.updates { it.deleteMessagesUpdates }
                .filter { it.chatId == chatId && it.isPermanent && !it.fromCache }
                .collect { u ->
                    val ids = u.messageIds.toHashSet()
                    raw.value = raw.value.filterNot { it.id in ids }
                }
        }
        scope.launch {
            core.updates { it.chatReadOutboxUpdates }
                .filter { it.chatId == chatId }
                .collect { lastReadOutbox.value = it.lastReadOutboxMessageId }
        }
        scope.launch {
            core.updates { it.messageInteractionInfoUpdates }
                .filter { it.chatId == chatId }
                .collect { u ->
                    raw.value = raw.value.map {
                        if (it.id == u.messageId) it.copy(reactions = u.interactionInfo.toReactionChips()) else it
                    }
                }
        }
    }

    /**
     * Loads an older page. TDLib may return fewer messages than requested (or only
     * what is cached locally), so we loop a few times until a useful amount arrived.
     */
    suspend fun loadMore() {
        if (fullyLoaded || loadingOlder.value) return
        loadingOlder.value = true
        try {
            var got = 0
            repeat(4) {
                val from = raw.value.lastOrNull()?.id ?: 0L
                val res = core.client
                    .getChatHistory(chatId = chatId, fromMessageId = from, offset = 0, limit = 30, onlyLocal = false)
                    .getOrNull() ?: return
                val items = res.messages.filterNotNull().map { it.toItem() }
                if (items.isEmpty()) {
                    if (from != 0L) fullyLoaded = true
                    return
                }
                items.forEach { insert(it) }
                got += items.size
                if (got >= 25) return
            }
        } finally {
            loadingOlder.value = false
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        Log.i(TAG, "sendText chat=$chatId len=${text.length}")
        send(
            label = "text",
            content = InputMessageText(
                text = FormattedText(text = text.trim(), entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        )
    }

    fun sendVoice(path: String, durationSec: Int, waveform: ByteArray) {
        Log.i(TAG, "sendVoice chat=$chatId dur=$durationSec")
        send(
            label = "voice",
            content = InputMessageVoiceNote(
                voiceNote = InputFileLocal(path = path),
                duration = durationSec,
                waveform = waveform,
                caption = null,
                selfDestructType = null,
            ),
        )
    }

    /**
     * Sends and inserts the returned pending message immediately — the message
     * appears in the thread even if the update stream is lagging; the later
     * UpdateMessageSendSucceeded swaps it for the server-confirmed copy.
     */
    private fun send(label: String, content: InputMessageContent) {
        core.async { client ->
            when (val r = client.sendMessage(chatId = chatId, inputMessageContent = content)) {
                is TdlResult.Success -> {
                    Log.i(TAG, "send $label ok: temp id=${r.result.id}")
                    insert(r.result.toItem())
                }
                is TdlResult.Failure -> {
                    Log.w(TAG, "send $label FAILED: ${r.code} ${r.message}")
                    lastSendError.value = "Send failed: ${r.message} (${r.code})"
                }
            }
        }
    }

    /** Adds the emoji reaction, or removes it if it's already ours. */
    fun toggleReaction(messageId: Long, emoji: String) {
        val mine = raw.value.firstOrNull { it.id == messageId }
            ?.reactions?.any { it.emoji == emoji && it.chosen } == true
        core.async { client ->
            if (mine) {
                client.removeMessageReaction(
                    chatId = chatId,
                    messageId = messageId,
                    reactionType = ReactionTypeEmoji(emoji),
                )
            } else {
                client.addMessageReaction(
                    chatId = chatId,
                    messageId = messageId,
                    reactionType = ReactionTypeEmoji(emoji),
                    isBig = false,
                    updateRecentReactions = false,
                ).errorOrNull()?.let { Log.w(TAG, "reaction failed: $it") }
            }
        }
    }

    fun markRead(ids: LongArray) {
        if (ids.isEmpty()) return
        core.async {
            it.viewMessages(chatId = chatId, messageIds = ids, source = null, forceRead = true)
        }
    }

    /** Called from ViewModel.onCleared — must not use [scope] (already cancelled). */
    fun dispose() {
        core.async { it.closeChat(chatId) }
    }

    private fun insert(item: MsgItem) {
        synchronized(lock) {
            val cur = raw.value
            if (cur.any { it.id == item.id }) return
            raw.value = (listOf(item) + cur).sortedByDescending { it.id }
        }
    }

    private companion object {
        const val TAG = "ChatThread"
    }
}
