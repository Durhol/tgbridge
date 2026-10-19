package dev.vanutp.tgbridge.common.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val TGBRIDGE_CHANNEL = "tgbridge:network"

val networkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

@Serializable
sealed class TgNetworkPacket {
    fun toBytes(): ByteArray = networkJson.encodeToString(this).toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): TgNetworkPacket = try {
            val str = String(bytes, Charsets.UTF_8)
            networkJson.decodeFromString<TgNetworkPacket>(str)
        } catch (e: Exception) {
            UnknownPacket(e.message ?: "decode_error")
        }
    }
}

@Serializable
@SerialName("unknown")
data class UnknownPacket(val error: String = "") : TgNetworkPacket()


// Server to Client Handshake
@Serializable
@SerialName("s2c_handshake")
data class S2CHandshakePacket(
    val serverVersion: String,
    val features: List<String> = listOf("photos", "replies", "voice", "stickers", "mentions"),
) : TgNetworkPacket()

// Client to Server Handshake
@Serializable
@SerialName("c2s_handshake")
data class C2SHandshakePacket(
    val clientVersion: String,
) : TgNetworkPacket()

// Server to Client Telegram Message with rich metadata
@Serializable
@SerialName("s2c_message")
data class S2CTelegramMessagePacket(
    val messageId: Int,
    val senderName: String,
    val senderId: Long,
    val text: String,
    val replyToId: Int? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val hasPhoto: Boolean = false,
    val photoFileId: String? = null,
    val photoWidth: Int = 0,
    val photoHeight: Int = 0,
    val photoUrl: String? = null,
    val thumbBase64: String? = null,
    val hasVoice: Boolean = false,
    val voiceFileId: String? = null,
    val voiceDuration: Int = 0,
    val hasSticker: Boolean = false,
    val stickerEmoji: String? = null,
    val stickerFileId: String? = null,
) : TgNetworkPacket()

// Client to Server: request full photo bytes
@Serializable
@SerialName("c2s_request_photo")
data class C2SRequestPhotoPacket(
    val messageId: Int,
) : TgNetworkPacket()

// Server to Client: send photo chunk
@Serializable
@SerialName("s2c_photo_chunk")
data class S2CPhotoChunkPacket(
    val messageId: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkBase64: String,
) : TgNetworkPacket()

// Client to Server: request voice note bytes
@Serializable
@SerialName("c2s_request_voice")
data class C2SRequestVoicePacket(
    val messageId: Int,
) : TgNetworkPacket()

// Server to Client: send voice chunk
@Serializable
@SerialName("s2c_voice_chunk")
data class S2CVoiceChunkPacket(
    val messageId: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkBase64: String,
) : TgNetworkPacket()

// Client to Server Send Reply to a specific TG message
@Serializable
@SerialName("c2s_reply")
data class C2SSendReplyPacket(
    val text: String,
    val replyToMessageId: Int,
) : TgNetworkPacket()

// Client to Server Send Photo
@Serializable
@SerialName("c2s_photo")
data class C2SSendPhotoPacket(
    val photoBytesBase64: String,
    val caption: String = "",
    val replyToMessageId: Int? = null,
) : TgNetworkPacket()

@Serializable
@SerialName("c2s_photo_chunk")
data class C2SSendPhotoChunkPacket(
    val uploadId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkBase64: String,
    val caption: String = "",
    val replyToMessageId: Int? = null,
) : TgNetworkPacket()

// Client to Server Send Voice Note (OGG Opus)
@Serializable
@SerialName("c2s_voice")
data class C2SSendVoicePacket(
    val voiceOggBase64: String,
    val duration: Int = 0,
    val replyToMessageId: Int? = null,
) : TgNetworkPacket()

@Serializable
@SerialName("c2s_voice_chunk")
data class C2SSendVoiceChunkPacket(
    val uploadId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkBase64: String,
    val duration: Int = 0,
    val replyToMessageId: Int? = null,
) : TgNetworkPacket()

// Client to Server Send Sticker
@Serializable
@SerialName("c2s_sticker")
data class C2SSendStickerPacket(
    val stickerFileId: String,
    val replyToMessageId: Int? = null,
) : TgNetworkPacket()

// Server to Client List of recent Telegram users for autocomplete
@Serializable
@SerialName("s2c_users")
data class S2CUserListPacket(
    val users: List<String>,
) : TgNetworkPacket()
