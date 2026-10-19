package dev.vanutp.tgbridge.fabric.client

import dev.vanutp.tgbridge.common.network.*
import dev.vanutp.tgbridge.fabric.gui.ImageViewerScreen
import dev.vanutp.tgbridge.fabric.image.TgImageManager
import dev.vanutp.tgbridge.fabric.network.TgPayload
import dev.vanutp.tgbridge.fabric.voice.TgVoiceManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class ReplyContext(
    val messageId: Int,
    val senderName: String,
    val text: String,
)

object TgClientManager {
    var isConnectedToServerMod = false
    var activeReply: ReplyContext? = null
    val telegramUsers = CopyOnWriteArrayList<String>()

    private val pendingVoiceChunks = ConcurrentHashMap<Int, Array<String?>>()

    fun handlePacket(packet: TgNetworkPacket) {
        when (packet) {
            is S2CHandshakePacket -> {
                isConnectedToServerMod = true
                ClientPlayNetworking.send(TgPayload(C2SHandshakePacket("0.9.14")))
            }

            is S2CUserListPacket -> {
                telegramUsers.clear()
                telegramUsers.addAll(packet.users)
            }

            is S2CTelegramMessagePacket -> {
                if (packet.hasPhoto) {
                    TgImageManager.loadPhotoFromPacket(packet)
                }
                if (!telegramUsers.contains(packet.senderName)) {
                    telegramUsers.add(packet.senderName)
                }
            }

            is S2CPhotoChunkPacket -> {
                TgImageManager.handlePhotoChunk(
                    packet.messageId,
                    packet.chunkIndex,
                    packet.totalChunks,
                    packet.chunkBase64
                )
            }

            is S2CVoiceChunkPacket -> {
                val chunks = pendingVoiceChunks.computeIfAbsent(packet.messageId) { arrayOfNulls(packet.totalChunks) }
                chunks[packet.chunkIndex] = packet.chunkBase64
                if (chunks.all { it != null }) {
                    pendingVoiceChunks.remove(packet.messageId)
                    val fullBase64 = chunks.joinToString("")
                    try {
                        val bytes = Base64.getDecoder().decode(fullBase64)
                        TgVoiceManager.handleVoiceChunk(packet.messageId, bytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            else -> {}
        }
    }

    fun openPhotoViewer(messageId: Int) {
        TgImageManager.requestFullPhoto(messageId)
        Minecraft.getInstance().execute {
            Minecraft.getInstance().setScreen(ImageViewerScreen(messageId))
        }
    }

    fun setReply(messageId: Int, senderName: String, text: String) {
        activeReply = ReplyContext(messageId, senderName, text)
        Minecraft.getInstance().execute {
            val mc = Minecraft.getInstance()
            if (mc.screen !is ChatScreen) {
                mc.setScreen(ChatScreen("", false))
            }
        }
    }

    fun clearReply() {
        activeReply = null
    }

    fun sendReply(text: String, replyToMessageId: Int) {
        ClientPlayNetworking.send(TgPayload(C2SSendReplyPacket(text, replyToMessageId)))
    }

    fun sendPhoto(photoBytes: ByteArray, caption: String, replyToMessageId: Int? = null) {
        Thread({
            try {
                val base64 = Base64.getEncoder().encodeToString(photoBytes)
                val chunkSize = 12000
                val totalChunks = (base64.length + chunkSize - 1) / chunkSize
                val uploadId = UUID.randomUUID().toString()

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(base64.length, start + chunkSize)
                    val chunk = base64.substring(start, end)
                    val packet = C2SSendPhotoChunkPacket(
                        uploadId = uploadId,
                        chunkIndex = i,
                        totalChunks = totalChunks,
                        chunkBase64 = chunk,
                        caption = if (i == 0) caption else "",
                        replyToMessageId = replyToMessageId,
                    )
                    Minecraft.getInstance().execute {
                        ClientPlayNetworking.send(TgPayload(packet))
                    }
                    Thread.sleep(10)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, "TgPhotoUploader").start()
    }

    fun sendVoice(voiceOggBytes: ByteArray, duration: Int, replyToMessageId: Int? = null) {
        Thread({
            try {
                val base64 = Base64.getEncoder().encodeToString(voiceOggBytes)
                val chunkSize = 12000
                val totalChunks = (base64.length + chunkSize - 1) / chunkSize
                val uploadId = UUID.randomUUID().toString()

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(base64.length, start + chunkSize)
                    val chunk = base64.substring(start, end)
                    val packet = C2SSendVoiceChunkPacket(
                        uploadId = uploadId,
                        chunkIndex = i,
                        totalChunks = totalChunks,
                        chunkBase64 = chunk,
                        duration = duration,
                        replyToMessageId = replyToMessageId,
                    )
                    Minecraft.getInstance().execute {
                        ClientPlayNetworking.send(TgPayload(packet))
                    }
                    Thread.sleep(10)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, "TgVoiceUploader").start()
    }
}
