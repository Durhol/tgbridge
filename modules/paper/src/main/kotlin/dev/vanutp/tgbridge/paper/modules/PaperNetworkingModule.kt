package dev.vanutp.tgbridge.paper.modules

import dev.vanutp.tgbridge.common.ConfigManager.config
import dev.vanutp.tgbridge.common.TgbridgeEvents
import dev.vanutp.tgbridge.common.network.*
import dev.vanutp.tgbridge.common.tg.*
import dev.vanutp.tgbridge.paper.PaperTelegramBridge
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

class PaperNetworkingModule(override val bridge: PaperTelegramBridge) : AbstractPaperModule(bridge), PluginMessageListener {
    override val paperId: String? = null

    val clientModPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val recentTelegramUsers: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())
    val pendingPhotoChunks = ConcurrentHashMap<String, Array<String?>>()
    val pendingVoiceChunks = ConcurrentHashMap<String, Array<String?>>()

    // Server-side caches for photos and voices so client doesn't need external connections
    val serverPhotoCache = ConcurrentHashMap<Int, ByteArray>()
    val serverVoiceCache = ConcurrentHashMap<Int, ByteArray>()

    override fun shouldEnable() = true

    override fun enable() {
        super.enable()
        val messenger = bridge.plugin.server.messenger
        messenger.registerOutgoingPluginChannel(bridge.plugin, TGBRIDGE_CHANNEL)
        messenger.registerIncomingPluginChannel(bridge.plugin, TGBRIDGE_CHANNEL, this)

        TgbridgeEvents.TG_CHAT_MESSAGE.addListener { e ->
            val msg = e.message
            recentTelegramUsers.add(msg.senderName)

            bridge.coroutineScope.launch {
                try {
                    val largestPhoto: TgPhotoSize? = msg.photo?.maxByOrNull { it.width * it.height }
                    var thumbBase64: String? = null

                    if (largestPhoto != null) {
                        try {
                            val photoBytes = bridge.bot.downloadFileBytes(largestPhoto.fileId)
                            serverPhotoCache[msg.messageId] = photoBytes
                            val thumbBytes = createThumbnailBytes(photoBytes, 32, 20)
                            if (thumbBytes != null) {
                                thumbBase64 = Base64.getEncoder().encodeToString(thumbBytes)
                            }
                        } catch (ex: Exception) {
                            bridge.logger.warn("Failed to download photo ${largestPhoto.fileId}: ${ex.message}")
                        }
                    }

                    val voice = msg.voice
                    if (voice != null) {
                        try {
                            val voiceBytes = bridge.bot.downloadFileBytes(voice.fileId)
                            serverVoiceCache[msg.messageId] = voiceBytes
                        } catch (ex: Exception) {
                            bridge.logger.warn("Failed to download voice ${voice.fileId}: ${ex.message}")
                        }
                    }

                    if (clientModPlayers.isEmpty()) return@launch

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
                        thumbBase64 = thumbBase64,
                        hasVoice = voice != null,
                        voiceFileId = voice?.fileId,
                        voiceDuration = voice?.duration ?: 0,
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
        pendingPhotoChunks.clear()
        pendingVoiceChunks.clear()
        serverPhotoCache.clear()
        serverVoiceCache.clear()
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

            is C2SRequestPhotoPacket -> {
                val photoBytes = serverPhotoCache[packet.messageId] ?: return
                bridge.coroutineScope.launch {
                    val base64 = Base64.getEncoder().encodeToString(photoBytes)
                    val chunkSize = 12000
                    val totalChunks = (base64.length + chunkSize - 1) / chunkSize
                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = minOf(base64.length, start + chunkSize)
                        val chunk = base64.substring(start, end)
                        val respPacket = S2CPhotoChunkPacket(packet.messageId, i, totalChunks, chunk)
                        if (player.isOnline) {
                            player.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, respPacket.toBytes())
                        }
                    }
                }
            }

            is C2SRequestVoicePacket -> {
                val voiceBytes = serverVoiceCache[packet.messageId] ?: return
                bridge.coroutineScope.launch {
                    val base64 = Base64.getEncoder().encodeToString(voiceBytes)
                    val chunkSize = 12000
                    val totalChunks = (base64.length + chunkSize - 1) / chunkSize
                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = minOf(base64.length, start + chunkSize)
                        val chunk = base64.substring(start, end)
                        val respPacket = S2CVoiceChunkPacket(packet.messageId, i, totalChunks, chunk)
                        if (player.isOnline) {
                            player.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, respPacket.toBytes())
                        }
                    }
                }
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

            is C2SSendPhotoChunkPacket -> {
                val chunks = pendingPhotoChunks.computeIfAbsent(packet.uploadId) { arrayOfNulls(packet.totalChunks) }
                chunks[packet.chunkIndex] = packet.chunkBase64
                if (chunks.all { it != null }) {
                    pendingPhotoChunks.remove(packet.uploadId)
                    val fullBase64 = chunks.joinToString("")
                    val chat = config.getDefaultChat()
                    bridge.coroutineScope.launch {
                        try {
                            val photoBytes = Base64.getDecoder().decode(fullBase64)
                            val caption = if (packet.caption.isNotBlank()) "${player.name}: ${packet.caption}" else player.name
                            val tgMessage = bridge.bot.sendPhoto(
                                chat.chatId,
                                photoBytes,
                                caption = caption,
                                replyToMessageId = packet.replyToMessageId ?: chat.topicId,
                            )

                            // Cache photo bytes on server
                            serverPhotoCache[tgMessage.messageId] = photoBytes
                            val thumbBytes = createThumbnailBytes(photoBytes, 32, 20)
                            val thumbBase64 = thumbBytes?.let { Base64.getEncoder().encodeToString(it) }

                            // Broadcast rich chat message with clickEvent to open photo viewer
                            val component = Component.text("<${player.name}> ")
                                .append(
                                    Component.text("[Фото]")
                                        .color(NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.runCommand("/tgbridge_client photo ${tgMessage.messageId}"))
                                        .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы развернуть фото")))
                                )
                                .append(if (packet.caption.isNotBlank()) Component.text(" " + packet.caption) else Component.empty())
                            bridge.platform.broadcastMessage(bridge.platform.getOnlinePlayers(), component)

                            // Send rich packet to client mods so thumbnail appears instantly
                            val s2cPacket = S2CTelegramMessagePacket(
                                messageId = tgMessage.messageId,
                                senderName = player.name,
                                senderId = 0L,
                                text = packet.caption,
                                hasPhoto = true,
                                thumbBase64 = thumbBase64,
                            )
                            val s2cBytes = s2cPacket.toBytes()
                            for (uuid in clientModPlayers) {
                                val p = bridge.plugin.server.getPlayer(uuid)
                                if (p != null && p.isOnline) {
                                    p.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, s2cBytes)
                                }
                            }
                        } catch (e: Exception) {
                            bridge.logger.error("Failed to send chunked photo from ${player.name}", e)
                        }
                    }
                }
            }

            is C2SSendVoiceChunkPacket -> {
                val chunks = pendingVoiceChunks.computeIfAbsent(packet.uploadId) { arrayOfNulls(packet.totalChunks) }
                chunks[packet.chunkIndex] = packet.chunkBase64
                if (chunks.all { it != null }) {
                    pendingVoiceChunks.remove(packet.uploadId)
                    val fullBase64 = chunks.joinToString("")
                    val chat = config.getDefaultChat()
                    bridge.coroutineScope.launch {
                        try {
                            val voiceBytes = Base64.getDecoder().decode(fullBase64)
                            val tgMessage = bridge.bot.sendVoice(
                                chat.chatId,
                                voiceBytes,
                                caption = player.name,
                                replyToMessageId = packet.replyToMessageId ?: chat.topicId,
                            )

                            // Cache voice bytes on server
                            serverVoiceCache[tgMessage.messageId] = voiceBytes

                            val durationText = if (packet.duration > 0) " (${packet.duration}с)" else ""
                            val component = Component.text("<${player.name}> ")
                                .append(
                                    Component.text("[Голосовое сообщение$durationText]")
                                        .color(NamedTextColor.GREEN)
                                        .clickEvent(ClickEvent.runCommand("/tgbridge_client voice ${tgMessage.messageId}"))
                                        .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы прослушать")))
                                )
                            bridge.platform.broadcastMessage(bridge.platform.getOnlinePlayers(), component)

                            val s2cPacket = S2CTelegramMessagePacket(
                                messageId = tgMessage.messageId,
                                senderName = player.name,
                                senderId = 0L,
                                text = "",
                                hasVoice = true,
                                voiceDuration = packet.duration,
                            )
                            val s2cBytes = s2cPacket.toBytes()
                            for (uuid in clientModPlayers) {
                                val p = bridge.plugin.server.getPlayer(uuid)
                                if (p != null && p.isOnline) {
                                    p.sendPluginMessage(bridge.plugin, TGBRIDGE_CHANNEL, s2cBytes)
                                }
                            }
                        } catch (e: Exception) {
                            bridge.logger.error("Failed to send chunked voice from ${player.name}", e)
                        }
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

    private fun createThumbnailBytes(imageBytes: ByteArray, maxW: Int = 32, maxH: Int = 20): ByteArray? {
        return try {
            val img = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: return null
            val scale = minOf(maxW.toDouble() / img.width, maxH.toDouble() / img.height)
            val w = (img.width * scale).toInt().coerceAtLeast(1)
            val h = (img.height * scale).toInt().coerceAtLeast(1)
            val thumb = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = thumb.createGraphics()
            g.drawImage(img, 0, 0, w, h, null)
            g.dispose()
            val baos = ByteArrayOutputStream()
            ImageIO.write(thumb, "jpg", baos)
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
