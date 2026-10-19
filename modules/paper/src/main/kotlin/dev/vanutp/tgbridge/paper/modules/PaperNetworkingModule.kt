package dev.vanutp.tgbridge.paper.modules

import dev.vanutp.tgbridge.common.ConfigManager.config
import dev.vanutp.tgbridge.common.TgbridgeEvents
import dev.vanutp.tgbridge.common.network.*
import dev.vanutp.tgbridge.common.tg.*
import dev.vanutp.tgbridge.paper.PaperTelegramBridge
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PaperNetworkingModule(override val bridge: PaperTelegramBridge) : AbstractPaperModule(bridge), PluginMessageListener {
    override val paperId: String? = null

    val clientModPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val recentTelegramUsers: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())

    override fun shouldEnable() = true

    override fun enable() {
        super.enable()
        val messenger = bridge.plugin.server.messenger
        messenger.registerOutgoingPluginChannel(bridge.plugin, TGBRIDGE_CHANNEL)
        messenger.registerIncomingPluginChannel(bridge.plugin, TGBRIDGE_CHANNEL, this)

        TgbridgeEvents.TG_CHAT_MESSAGE.addListener { e ->
            val msg = e.message
            recentTelegramUsers.add(msg.senderName)

            if (clientModPlayers.isEmpty()) return@addListener

            bridge.coroutineScope.launch {
                try {
                    val largestPhoto: TgPhotoSize? = msg.photo?.maxByOrNull { it.width * it.height }
                    val photoUrl = largestPhoto?.let {
                        try {
                            bridge.bot.getFileDownloadUrl(it.fileId)
                        } catch (ex: Exception) {
                            bridge.logger.warn("Failed to get photo URL for file ${it.fileId}: ${ex.message}")
                            null
                        }
                    }

                    val replyMsg = msg.replyToMessage
                    val replySender = replyMsg?.senderName
                    val replyText = replyMsg?.tgText?.text ?: replyMsg?.caption

                    val packet = S2CTelegramMessagePacket(
                        messageId = msg.messageId,
                        senderName = msg.senderName,
                        senderId = msg.from?.id ?: msg.senderChat?.id ?: 0L,
                        text = msg.tgText?.text ?: msg.caption ?: "",
                        replyToId = replyMsg?.messageId,
                        replyToSender = replySender,
                        replyToText = replyText,
                        hasPhoto = largestPhoto != null,
                        photoFileId = largestPhoto?.fileId,
                        photoWidth = largestPhoto?.width ?: 0,
                        photoHeight = largestPhoto?.height ?: 0,
                        photoUrl = photoUrl,
                        hasVoice = msg.voice != null,
                        voiceFileId = msg.voice?.fileId,
                        voiceDuration = msg.voice?.duration ?: 0,
                        hasSticker = msg.sticker != null,
                        stickerEmoji = msg.sticker?.emoji,
                        stickerFileId = msg.sticker?.fileId,
                    )

                    val bytes = packet.toBytes()
                    for (uuid in clientModPlayers) {
                        val player = bridge.plugin.server.getPlayer(uuid)
                        if (player != null && player.isOnline) {
                            player.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, bytes)
                        }
                    }
                } catch (ex: Exception) {
                    bridge.logger.error("Error broadcasting rich TG message to client mods", ex)
                }
            }
        }
    }

    override fun disable() {
        super.disable()
        val messenger = bridge.plugin.server.messenger
        messenger.unregisterOutgoingPluginChannel(bridge.plugin, TGBRIDGE_CHANNEL)
        messenger.unregisterIncomingPluginChannel(bridge.plugin, TGBRIDGE_CHANNEL, this)
        clientModPlayers.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        bridge.plugin.server.scheduler.runTaskLater(bridge.plugin, Runnable {
            if (event.player.isOnline) {
                val handshake = S2CHandshakePacket(serverVersion = "0.9.14")
                event.player.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, handshake.toBytes())
            }
        }, 10L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        clientModPlayers.remove(event.player.uniqueId)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != TGBRIDGE_CHANNEL) return
        val packet = TgNetworkPacket.fromBytes(message) ?: return

        when (packet) {
            is C2SHandshakePacket -> {
                clientModPlayers.add(player.uniqueId)
                bridge.logger.info("Player ${player.name} connected with tgbridge client mod v${packet.clientVersion}")
                val userListPacket = S2CUserListPacket(recentTelegramUsers.toList())
                player.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, userListPacket.toBytes())
            }

            is C2SSendReplyPacket -> {
                val chat = config.getDefaultChat()
                bridge.coroutineScope.launch {
                    try {
                        val formatted = "${player.name}: ${packet.text}"
                        bridge.bot.sendMessage(
                            chat.chatId,
                            formatted,
                            replyToMessageId = packet.replyToMessageId,
                        )

                        val component = Component.text("<${player.name}> ")
                            .append(Component.text("↩ [Ответ]: ", NamedTextColor.GRAY, TextDecoration.ITALIC))
                            .append(Component.text(packet.text))
                        bridge.platform.broadcastMessage(bridge.platform.getOnlinePlayers(), component)
                    } catch (e: Exception) {
                        bridge.logger.error("Failed to send reply from ${player.name}", e)
                    }
                }
            }

            is C2SSendPhotoPacket -> {
                val chat = config.getDefaultChat()
                bridge.coroutineScope.launch {
                    try {
                        val photoBytes = Base64.getDecoder().decode(packet.photoBytesBase64)
                        val caption = if (packet.caption.isNotBlank()) "${player.name}: ${packet.caption}" else player.name
                        bridge.bot.sendPhoto(
                            chat.chatId,
                            photoBytes,
                            caption = caption,
                            replyToMessageId = packet.replyToMessageId ?: chat.topicId,
                        )

                        val component = Component.text("<${player.name}> ")
                            .append(Component.text("[🖼️ Фото] ", NamedTextColor.AQUA))
                            .append(Component.text(packet.caption))
                        bridge.platform.broadcastMessage(bridge.platform.getOnlinePlayers(), component)
                    } catch (e: Exception) {
                        bridge.logger.error("Failed to send photo from ${player.name}", e)
                    }
                }
            }

            is C2SSendVoicePacket -> {
                val chat = config.getDefaultChat()
                bridge.coroutineScope.launch {
                    try {
                        val voiceBytes = Base64.getDecoder().decode(packet.voiceOggBase64)
                        bridge.bot.sendVoice(
                            chat.chatId,
                            voiceBytes,
                            caption = player.name,
                            replyToMessageId = packet.replyToMessageId ?: chat.topicId,
                        )

                        val durationText = if (packet.duration > 0) " (${packet.duration}с)" else ""
                        val component = Component.text("<${player.name}> ")
                            .append(Component.text("[🎙️ Голосовое сообщение$durationText]", NamedTextColor.GREEN))
                        bridge.platform.broadcastMessage(bridge.platform.getOnlinePlayers(), component)
                    } catch (e: Exception) {
                        bridge.logger.error("Failed to send voice from ${player.name}", e)
                    }
                }
            }

            is C2SSendStickerPacket -> {
                val chat = config.getDefaultChat()
                bridge.coroutineScope.launch {
                    try {
                        bridge.bot.sendSticker(
                            chat.chatId,
                            packet.stickerFileId,
                            replyToMessageId = packet.replyToMessageId ?: chat.topicId,
                        )
                    } catch (e: Exception) {
                        bridge.logger.error("Failed to send sticker from ${player.name}", e)
                    }
                }
            }

            else -> {}
        }
    }
}
