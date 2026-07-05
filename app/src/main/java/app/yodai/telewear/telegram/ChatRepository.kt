package app.yodai.telewear.telegram

import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatList
import dev.g000sha256.tdl.dto.ChatListArchive
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ChatPosition
import dev.g000sha256.tdl.dto.ChatType
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageTopicForum
import dev.g000sha256.tdl.dto.User
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /** Resolved forum topic names, keyed by (chatId, forumTopicId). */
    private val topicNames = MutableStateFlow<Map<Pair<Long, Int>, String>>(emptyMap())
    private val topicFetchInFlight = ConcurrentHashMap.newKeySet<Pair<Long, Int>>()

    /** Telegram chat folders (id → name), in the user's configured order. */
    val folders = MutableStateFlow<List<Pair<Int, String>>>(emptyList())

    /** null = the main list; otherwise a folder id from [folders]. */
    val selectedFolderId = MutableStateFlow<Int?>(null)

    // Recomputes on chat, folder, topic-name, or sender-user changes so forum
    // rows fill in their topic name and sender avatar as those resolve.
    val chatList: StateFlow<List<ChatItem>> =
        combine(states, selectedFolderId, topicNames, users) { map, folderId, _, _ ->
            map.values
                .mapNotNull { it.toChatItem(folderId) }
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
                    st.copy(positions = st.positions.filterNot { p -> sameList(p.list, u.position.list) } + u.position)
                }
            }
        }
        scope.launch {
            core.updates { it.chatFoldersUpdates }.collect { u ->
                folders.value = u.chatFolders.filterNotNull().map { it.id to it.name.text.text }
                // Drop a stale selection if the folder was deleted on another device.
                if (selectedFolderId.value != null && folders.value.none { it.first == selectedFolderId.value }) {
                    selectedFolderId.value = null
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
        // Forum topic names arrive passively as info updates…
        scope.launch {
            core.updates { it.forumTopicInfoUpdates }.collect { u ->
                topicNames.value = topicNames.value + ((u.info.chatId to u.info.forumTopicId) to u.info.name)
            }
        }
        // …and are fetched on demand for any forum last message we can't name yet.
        scope.launch {
            states.collect { map ->
                map.values.forEach { st ->
                    val tid = (st.lastMessage?.topicId as? MessageTopicForum)?.forumTopicId ?: return@forEach
                    val key = st.id to tid
                    if (topicNames.value[key] == null && topicFetchInFlight.add(key)) {
                        launch {
                            val name = core.client.getForumTopic(st.id, tid).getOrNull()?.info?.name
                            if (name != null) {
                                topicNames.value = topicNames.value + (key to name)
                            } else {
                                topicFetchInFlight.remove(key) // allow a later retry
                            }
                        }
                    }
                }
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

    /** Switches the visible list and makes sure that folder's chats are loaded. */
    fun selectFolder(folderId: Int?) {
        selectedFolderId.value = folderId
        if (folderId != null) {
            scope.launch {
                repeat(5) {
                    val r = core.client.loadChats(chatList = ChatListFolder(folderId), limit = 100)
                    if (r.isNotFound()) return@launch
                }
            }
        }
    }

    /**
     * Local chat search (title/username). Results outside the main list still
     * render — they just carry no pinned/order affinity.
     */
    suspend fun searchChats(query: String): List<ChatItem> {
        val ids = core.client.searchChats(query = query, typeFilter = null, limit = 20)
            .getOrNull()?.chatIds ?: return emptyList()
        return ids.toList().mapNotNull { id ->
            val st = states.value[id] ?: core.client.getChat(id).getOrNull()?.let { c ->
                c.toState().also { states.value = states.value + (c.id to it) }
            }
            st?.let { it.toChatItem(null) ?: it.buildItem(pinned = false, order = 0L) }
        }
    }

    fun chatItem(chatId: Long): ChatItem? = states.value[chatId]?.toChatItem(null)

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

    private fun ChatState.toChatItem(folderId: Int?): ChatItem? {
        val position = positions.firstOrNull { p ->
            val l = p.list
            if (folderId == null) l is ChatListMain else l is ChatListFolder && l.chatFolderId == folderId
        } ?: return null
        if (position.order == 0L) return null
        return buildItem(pinned = position.isPinned, order = position.order)
    }

    private fun ChatState.buildItem(pinned: Boolean, order: Long): ChatItem {
        val supergroup = type as? ChatTypeSupergroup

        // Forum groups: surface the last message's topic and its sender identity.
        val forumTopicId = (lastMessage?.topicId as? MessageTopicForum)?.forumTopicId
        val senderId = (lastMessage?.senderId as? MessageSenderUser)?.userId
        val senderUser = senderId?.let { users.value[it] }
        val senderDisplay = senderUser
            ?.let { listOf(it.firstName, it.lastName).filter { s -> s.isNotBlank() }.joinToString(" ") }
            ?.ifBlank { null }

        return ChatItem(
            id = id,
            title = title,
            isChannel = supergroup?.isChannel == true,
            isGroup = type is ChatTypeBasicGroup || (supergroup != null && !supergroup.isChannel),
            unread = unread,
            muted = muted,
            pinned = pinned,
            order = order,
            preview = lastMessage?.content?.toMsgContent()?.previewText().orEmpty(),
            date = lastMessage?.date ?: 0,
            photoFileId = photoFileId,
            topicName = forumTopicId?.let { topicNames.value[id to it] },
            senderPhotoFileId = if (forumTopicId != null) senderUser?.profilePhoto?.small?.id else null,
            senderName = if (forumTopicId != null) senderDisplay else null,
            senderUserId = if (forumTopicId != null) senderId else null,
        )
    }

    /** Reactive mute state for one chat (drives the thread-header toggle). */
    fun mutedFlow(chatId: Long): Flow<Boolean> =
        states.map { it[chatId]?.muted ?: false }.distinctUntilChanged()

    /**
     * Mute/unmute a chat. Overrides only muteFor (Int.MAX_VALUE = forever, 0 =
     * on); everything else stays on the account default. Result echoes back
     * through chatNotificationSettingsUpdates and flips [mutedFlow].
     */
    fun setMuted(chatId: Long, muted: Boolean) {
        core.async { client ->
            client.setChatNotificationSettings(
                chatId = chatId,
                notificationSettings = ChatNotificationSettings(
                    useDefaultMuteFor = false,
                    muteFor = if (muted) Int.MAX_VALUE else 0,
                    useDefaultSound = true,
                    soundId = 0,
                    useDefaultShowPreview = true,
                    showPreview = true,
                    useDefaultMuteStories = true,
                    muteStories = false,
                    useDefaultStorySound = true,
                    storySoundId = 0,
                    useDefaultShowStoryPoster = true,
                    showStoryPoster = true,
                    useDefaultDisablePinnedMessageNotifications = true,
                    disablePinnedMessageNotifications = false,
                    useDefaultDisableMentionNotifications = true,
                    disableMentionNotifications = false,
                ),
            )
        }
    }
}

/** Positions are keyed by list identity — folders differ by id, not class. */
private fun sameList(a: ChatList, b: ChatList): Boolean = when {
    a is ChatListMain && b is ChatListMain -> true
    a is ChatListArchive && b is ChatListArchive -> true
    a is ChatListFolder && b is ChatListFolder -> a.chatFolderId == b.chatFolderId
    else -> false
}

private fun ChatNotificationSettings.isMuted(): Boolean =
    if (useDefaultMuteFor) false else muteFor > 0
