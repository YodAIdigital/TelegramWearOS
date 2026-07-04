package app.yodai.telewear.telegram

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimatedEmoji
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageBasicGroupChatCreate
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.MessageChatAddMembers
import dev.g000sha256.tdl.dto.MessageChatChangePhoto
import dev.g000sha256.tdl.dto.MessageChatChangeTitle
import dev.g000sha256.tdl.dto.MessageChatDeleteMember
import dev.g000sha256.tdl.dto.MessageChatJoinByLink
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.MessageContactRegistered
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDice
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageForumTopicCreated
import dev.g000sha256.tdl.dto.MessageGiveaway
import dev.g000sha256.tdl.dto.MessageInteractionInfo
import dev.g000sha256.tdl.dto.MessageInvoice
import dev.g000sha256.tdl.dto.MessageLocation
import dev.g000sha256.tdl.dto.MessagePaidMedia
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.MessageSupergroupChatCreate
import dev.g000sha256.tdl.dto.MessageVenue
import dev.g000sha256.tdl.dto.MessageVideoChatEnded
import dev.g000sha256.tdl.dto.MessageVideoChatStarted
import dev.g000sha256.tdl.dto.PollTypeQuiz
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.StickerFormatTgs

/**
 * UI-facing message model. TDLib's generated classes are immutable and huge;
 * mapping at the repository boundary keeps the UI stable against TDLib schema churn.
 */
data class MsgItem(
    val id: Long,
    val chatId: Long,
    val isOutgoing: Boolean,
    val date: Int,
    val senderUserId: Long?,
    val content: MsgContent,
    val pending: Boolean,
    val failed: Boolean,
    val reactions: List<ReactionChip> = emptyList(),
)

/** One emoji reaction aggregated on a message ("👍 3", highlighted if mine). */
data class ReactionChip(val emoji: String, val count: Int, val chosen: Boolean)

fun MessageInteractionInfo?.toReactionChips(): List<ReactionChip> =
    this?.reactions?.reactions
        ?.mapNotNull { r ->
            (r.type as? ReactionTypeEmoji)?.let { ReactionChip(it.emoji, r.totalCount, r.isChosen) }
        }
        ?: emptyList()

/** Best text for read-aloud: message text or media caption. */
fun MsgContent.speakableText(): String? = when (this) {
    is MsgContent.Text -> text
    is MsgContent.Photo -> caption.ifBlank { null }
    is MsgContent.Video -> caption.ifBlank { null }
    is MsgContent.Poll -> question
    is MsgContent.Contact -> "$name, $phone"
    else -> null
}

data class ChatItem(
    val id: Long,
    val title: String,
    val isChannel: Boolean,
    val isGroup: Boolean,
    val unread: Int,
    val muted: Boolean,
    val pinned: Boolean,
    val order: Long,
    val preview: String,
    val date: Int,
    val photoFileId: Int?,
)

sealed interface MsgContent {
    data class Text(val text: String) : MsgContent

    data class Photo(
        val fileId: Int,
        val width: Int,
        val height: Int,
        val caption: String,
        /** Tiny embedded JPEG shown instantly while the real photo downloads. */
        val thumb: ByteArray?,
    ) : MsgContent

    /** Voice notes and music/audio files — both play through the shared player. */
    data class Playable(val fileId: Int, val duration: Int, val title: String?) : MsgContent

    /** Videos, GIFs, and round video messages — thumbnail bubble, fullscreen playback. */
    data class Video(
        val fileId: Int,
        val thumbFileId: Int?,
        val duration: Int,
        val width: Int,
        val height: Int,
        val caption: String,
        val label: String,
        val thumb: ByteArray?,
    ) : MsgContent

    data class Sticker(
        val emoji: String,
        val fileId: Int,
        val isAnimated: Boolean,
        val thumbFileId: Int?,
    ) : MsgContent

    data class Doc(val fileId: Int, val fileName: String, val mime: String, val size: Long) : MsgContent

    data class Poll(
        val question: String,
        val options: List<PollOptionItem>,
        val totalVotes: Int,
        val isQuiz: Boolean,
        val closed: Boolean,
        val voted: Boolean,
    ) : MsgContent

    data class Location(
        val lat: Double,
        val lon: Double,
        val title: String?,
        val address: String?,
        val live: Boolean,
    ) : MsgContent

    data class Contact(val name: String, val phone: String) : MsgContent

    /** Group/system events ("X joined", "pinned a message") — centered, no bubble. */
    data class Service(val label: String) : MsgContent

    data class Other(val label: String) : MsgContent
}

data class PollOptionItem(val text: String, val percent: Int, val votes: Int, val chosen: Boolean)

fun Message.toItem(): MsgItem = MsgItem(
    id = id,
    chatId = chatId,
    isOutgoing = isOutgoing,
    date = date,
    senderUserId = (senderId as? MessageSenderUser)?.userId,
    content = content.toMsgContent(),
    pending = sendingState is MessageSendingStatePending,
    failed = sendingState is MessageSendingStateFailed,
    reactions = interactionInfo.toReactionChips(),
)

fun MessageContent.toMsgContent(): MsgContent = when (this) {
    is MessageText -> MsgContent.Text(text.text)

    is MessagePhoto -> {
        val sizes = photo.sizes.filterNotNull()
        val best = sizes.firstOrNull { it.width >= 400 } ?: sizes.maxByOrNull { it.width }
        if (best != null) {
            MsgContent.Photo(best.photo.id, best.width, best.height, caption.text, photo.minithumbnail?.data)
        } else {
            MsgContent.Other("📷 Photo")
        }
    }

    is MessageVoiceNote -> MsgContent.Playable(voiceNote.voice.id, voiceNote.duration, title = null)

    is MessageAudio -> {
        val title = listOf(audio.performer, audio.title)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
            .ifBlank { audio.fileName.ifBlank { "Audio" } }
        MsgContent.Playable(audio.audio.id, audio.duration, title = title)
    }

    is MessageVideo -> MsgContent.Video(
        fileId = video.video.id,
        thumbFileId = video.thumbnail?.file?.id,
        duration = video.duration,
        width = video.width,
        height = video.height,
        caption = caption.text,
        label = "Video",
        thumb = video.minithumbnail?.data,
    )

    is MessageVideoNote -> MsgContent.Video(
        fileId = videoNote.video.id,
        thumbFileId = videoNote.thumbnail?.file?.id,
        duration = videoNote.duration,
        width = videoNote.length,
        height = videoNote.length,
        caption = "",
        label = "Video message",
        thumb = videoNote.minithumbnail?.data,
    )

    is MessageAnimation -> MsgContent.Video(
        fileId = animation.animation.id,
        thumbFileId = animation.thumbnail?.file?.id,
        duration = animation.duration,
        width = animation.width,
        height = animation.height,
        caption = caption.text,
        label = "GIF",
        thumb = animation.minithumbnail?.data,
    )

    is MessageSticker -> MsgContent.Sticker(
        emoji = sticker.emoji,
        fileId = sticker.sticker.id,
        isAnimated = sticker.format is StickerFormatTgs,
        thumbFileId = sticker.thumbnail?.file?.id,
    )

    is MessageDocument -> MsgContent.Doc(
        fileId = document.document.id,
        fileName = document.fileName.ifBlank { "File" },
        mime = document.mimeType,
        size = document.document.size,
    )

    // A lone emoji ("👍") arrives as its own type carrying a sticker — reuse the
    // sticker pipeline (animated ones loop via Lottie), else render as big text.
    is MessageAnimatedEmoji -> {
        val s = animatedEmoji.sticker
        if (s != null) {
            MsgContent.Sticker(
                emoji = emoji,
                fileId = s.sticker.id,
                isAnimated = s.format is StickerFormatTgs,
                thumbFileId = s.thumbnail?.file?.id,
            )
        } else {
            MsgContent.Text(emoji)
        }
    }

    is MessagePoll -> MsgContent.Poll(
        question = poll.question.text,
        options = poll.options.filterNotNull().map {
            PollOptionItem(it.text.text, it.votePercentage, it.voterCount, it.isChosen)
        },
        totalVotes = poll.totalVoterCount,
        isQuiz = poll.type is PollTypeQuiz,
        closed = poll.isClosed,
        voted = poll.options.filterNotNull().any { it.isChosen },
    )

    is MessageLocation -> MsgContent.Location(
        lat = location.latitude,
        lon = location.longitude,
        title = null,
        address = null,
        live = false,
    )

    is MessageVenue -> MsgContent.Location(
        lat = venue.location.latitude,
        lon = venue.location.longitude,
        title = venue.title,
        address = venue.address,
        live = false,
    )

    is MessageContact -> MsgContent.Contact(
        name = listOf(contact.firstName, contact.lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { contact.phoneNumber },
        phone = contact.phoneNumber,
    )

    is MessageDice -> MsgContent.Text("$emoji $value")

    is MessageStory -> MsgContent.Other("📖 Story — view on phone")
    is MessageInvoice -> MsgContent.Other("🧾 Invoice")
    is MessageGiveaway -> MsgContent.Other("🎁 Giveaway")
    is MessagePaidMedia -> MsgContent.Other("⭐ Paid media")
    is MessageCall -> MsgContent.Other("📞 Call")

    is MessagePinMessage -> MsgContent.Service("pinned a message")
    is MessageChatChangeTitle -> MsgContent.Service("renamed the group to \"$title\"")
    is MessageChatChangePhoto -> MsgContent.Service("changed the group photo")
    is MessageChatAddMembers -> MsgContent.Service("joined the group")
    is MessageChatDeleteMember -> MsgContent.Service("left the group")
    is MessageChatJoinByLink -> MsgContent.Service("joined via invite link")
    is MessageVideoChatStarted -> MsgContent.Service("started a video chat")
    is MessageVideoChatEnded -> MsgContent.Service("video chat ended")
    is MessageContactRegistered -> MsgContent.Service("joined Telegram")
    is MessageBasicGroupChatCreate -> MsgContent.Service("created the group")
    is MessageSupergroupChatCreate -> MsgContent.Service("created the group")
    is MessageForumTopicCreated -> MsgContent.Service("created topic \"$name\"")

    else -> MsgContent.Other("Unsupported message")
}

fun MsgContent.previewText(): String = when (this) {
    is MsgContent.Text -> text
    is MsgContent.Photo -> if (caption.isNotEmpty()) "📷 $caption" else "📷 Photo"
    is MsgContent.Playable -> if (title == null) "🎤 Voice message" else "🎵 $title"
    is MsgContent.Video -> "📹 $label"
    is MsgContent.Sticker -> "$emoji Sticker"
    is MsgContent.Doc -> "📄 $fileName"
    is MsgContent.Poll -> "📊 $question"
    is MsgContent.Location -> "📍 ${title ?: "Location"}"
    is MsgContent.Contact -> "👤 $name"
    is MsgContent.Service -> label
    is MsgContent.Other -> label
}
