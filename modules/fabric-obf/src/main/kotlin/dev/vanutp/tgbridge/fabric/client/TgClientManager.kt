package dev.vanutp.tgbridge.fabric.client

import dev.vanutp.tgbridge.common.network.*
import dev.vanutp.tgbridge.fabric.gui.ImageViewerScreen
import dev.vanutp.tgbridge.fabric.image.TgImageManager
import dev.vanutp.tgbridge.fabric.network.TgPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import java.util.*
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
                    TgImageManager.loadPhoto(packet)
                }
                if (!telegramUsers.contains(packet.senderName)) {
                    telegramUsers.add(packet.senderName)
                }
            }

            else -> {}
        }
    }

    fun openPhotoViewer(messageId: Int) {
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
        val base64 = Base64.getEncoder().encodeToString(photoBytes)
        ClientPlayNetworking.send(TgPayload(C2SSendPhotoPacket(base64, caption, replyToMessageId)))
    }

    fun sendVoice(voiceOggBytes: ByteArray, duration: Int, replyToMessageId: Int? = null) {
        val base64 = Base64.getEncoder().encodeToString(voiceOggBytes)
        ClientPlayNetworking.send(TgPayload(C2SSendVoicePacket(base64, duration, replyToMessageId)))
    }
}
