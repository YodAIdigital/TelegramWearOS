package app.yodai.telewear.telegram

import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ChatPosition
import dev.g000sha256.tdl.dto.ChatType
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Maintains the main chat list from TDLib's incremental updates.
 * TDLib pushes chats via UpdateNewChat then mutates aspects (title, last message,
 * position, unread count) through dedicated updates — we fold them into [ChatState].
 */
class ChatRepository(private val core: TelegramCore, private val scope: CoroutineScope) {

    private data class ChatState(
        val id: Long,
        val type: ChatType,
        val title: String,
        val photoFileId: Int?,
        val lastMessage: Message?,
        val positions: List<ChatPosition>,
        val unread: Int,
        val muted: Boolean,
    )

    private val states = MutableStateFlow<Map<Long, ChatState>>(emptyMap())
    private val users = MutableStateFlow<Map<Long, User>>(emptyMap())

    val chatList: StateFlow<List<ChatItem>> = states.map { map ->
        map.values
            .mapNotNull { it.toChatItem() }
            .sortedWith(compareByDescending<ChatItem> { it.pinned }.thenByDescending { it.order })
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            core.updates { it.newChatUpdates }.collect { u ->
                val c = u.chat
                states.value = states.value + (c.id to c.toState())
            }
        }
        scope.launch {
            core.updates { it.chatTitleUpdates }.collect { u ->
                mutate(u.chatId) { it.copy(title = u.title) }
            }
        }
        scope.launch {
            core.updates { it.chatPhotoUpdates }.collect { u ->
                mutate(u.chatId) { it.copy(photoFileId = u.photo?.small?.id) }
            }
        }
        scope.launch {
            core.updates { it.chatLastMessageUpdates }.collect { u ->
                mutate(u.chatId) {
                    it.copy(
                        lastMessage = u.lastMessage,
                        positions = if (u.positions.isNotEmpty()) u.positions.filterNotNull() else it.positions,
                    )
                }
            }
        }
        scope.launch {
            core.updates { it.chatPositionUpdates }.collect { u ->
                mutate(u.chatId) { st ->
                    st.copy(positions = st.positions.filterNot { p -> p.list::class == u.position.list::class } + u.position)
                }
            }
        }
        scope.launch {
            core.updates { it.chatReadInboxUpdates }.collect { u ->
                mutate(u.chatId) { it.copy(unread = u.unreadCount) }
            }
        }
        scope.launch {
            core.updates { it.chatNotificationSettingsUpdates }.collect { u ->
                mutate(u.chatId) { it.copy(muted = u.notificationSettings.isMuted()) }
            }
        }
        scope.launch {
            core.updates { it.userUpdates }.collect { u ->
                users.value = users.value + (u.user.id to u.user)
            }
        }
        // (Re)load the chat list every time authorization becomes Ready.
        scope.launch {
            core.authUi.collect { ui ->
                if (ui is AuthUi.Ready) loadAll()
            }
        }
    }

    private suspend fun loadAll() {
        // loadChats pages until TDLib answers 404 (= everything is loaded).
        repeat(10) {
            val r = core.client.loadChats(chatList = ChatListMain(), limit = 100)
            if (r.isNotFound()) return
        }
    }

    fun chatItem(chatId: Long): ChatItem? = states.value[chatId]?.toChatItem()

    fun cachedUser(userId: Long): User? = users.value[userId]

    suspend fun user(userId: Long): User? =
        users.value[userId]
            ?: core.client.getUser(userId).getOrNull()?.also { u ->
                users.value = users.value + (userId to u)
            }

    suspend fun displayName(userId: Long): String {
        val u = user(userId) ?: return "Unknown"
        return listOf(u.firstName, u.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown" }
    }

    private fun mutate(chatId: Long, transform: (ChatState) -> ChatState) {
        val cur = states.value[chatId] ?: return
        states.value = states.value + (chatId to transform(cur))
    }

    private fun Chat.toState() = ChatState(
        id = id,
        type = type,
        title = title,
        photoFileId = photo?.small?.id,
        lastMessage = lastMessage,
        positions = positions.filterNotNull(),
        unread = unreadCount,
        muted = notificationSettings.isMuted(),
    )

    private fun ChatState.toChatItem(): ChatItem? {
        val mainPosition = positions.firstOrNull { it.list is ChatListMain } ?: return null
        if (mainPosition.order == 0L) return null
        val supergroup = type as? ChatTypeSupergroup
        return ChatItem(
            id = id,
            title = title,
            isChannel = supergroup?.isChannel == true,
            isGroup = type is ChatTypeBasicGroup || (supergroup != null && !supergroup.isChannel),
            unread = unread,
            muted = muted,
            pinned = mainPosition.isPinned,
            order = mainPosition.order,
            preview = lastMessage?.content?.toMsgContent()?.previewText().orEmpty(),
            date = lastMessage?.date ?: 0,
            photoFileId = photoFileId,
        )
    }
}

private fun ChatNotificationSettings.isMuted(): Boolean =
    if (useDefaultMuteFor) false else muteFor > 0
