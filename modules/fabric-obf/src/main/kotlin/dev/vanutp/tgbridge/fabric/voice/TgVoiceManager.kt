package dev.vanutp.tgbridge.fabric.voice

import dev.vanutp.tgbridge.common.modules.VoiceChatPlugin
import dev.vanutp.tgbridge.common.modules.extractOpusPackets
import dev.vanutp.tgbridge.common.network.C2SRequestVoicePacket
import dev.vanutp.tgbridge.fabric.network.TgPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

object TgVoiceManager {
    val voiceCache = ConcurrentHashMap<Int, ByteArray>()
    private var currentPlayingLine: SourceDataLine? = null
    private var currentPlayingThread: Thread? = null
    var currentlyPlayingMessageId: Int? = null

    fun handleVoiceChunk(messageId: Int, fullOggBytes: ByteArray) {
        voiceCache[messageId] = fullOggBytes
        if (currentlyPlayingMessageId == messageId) {
            playVoiceBytes(messageId, fullOggBytes)
        }
    }

    fun playVoiceMessage(messageId: Int) {
        if (currentlyPlayingMessageId == messageId && currentPlayingLine != null) {
            stopPlaying()
            return
        }

        stopPlaying()
        currentlyPlayingMessageId = messageId

        val cached = voiceCache[messageId]
        if (cached != null) {
            playVoiceBytes(messageId, cached)
        } else {
            // Request from server
            Minecraft.getInstance().gui.setOverlayMessage(
                Component.literal("Загрузка голосового сообщения..."),
                false
            )
            ClientPlayNetworking.send(TgPayload(C2SRequestVoicePacket(messageId)))
        }
    }

    fun stopPlaying() {
        try {
            currentPlayingLine?.stop()
            currentPlayingLine?.flush()
            currentPlayingLine?.close()
        } catch (_: Exception) {}
        currentPlayingLine = null
        currentPlayingThread?.interrupt()
        currentPlayingThread = null
        currentlyPlayingMessageId = null
    }

    private fun playVoiceBytes(messageId: Int, oggBytes: ByteArray) {
        currentPlayingThread = Thread({
            var decoder = VoiceChatPlugin.api?.createDecoder()
            try {
                val frames = extractOpusPackets(oggBytes)
                if (frames.isEmpty()) {
                    Minecraft.getInstance().execute {
                        Minecraft.getInstance().gui.setOverlayMessage(
                            Component.literal("Ошибка: пустая аудиодорожка"),
                            false
                        )
                    }
                    return@Thread
                }

                val format = AudioFormat(48000.0f, 16, 1, true, false)
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format)
                line.start()
                currentPlayingLine = line

                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.setOverlayMessage(
                        Component.literal("▶ Воспроизведение голосового..."),
                        false
                    )
                }

                for (frame in frames) {
                    if (currentlyPlayingMessageId != messageId) break
                    val shorts = decoder?.decode(frame)
                    if (shorts != null && shorts.isNotEmpty()) {
                        val byteBuf = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (s in shorts) {
                            byteBuf.putShort(s)
                        }
                        val bytes = byteBuf.array()
                        line.write(bytes, 0, bytes.size)
                    }
                }

                line.drain()
                line.stop()
                line.close()

                Minecraft.getInstance().execute {
                    if (currentlyPlayingMessageId == messageId) {
                        Minecraft.getInstance().gui.setOverlayMessage(
                            Component.literal("■ Голосовое сообщение завершено"),
                            false
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    decoder?.close()
                } catch (_: Exception) {}
                if (currentlyPlayingMessageId == messageId) {
                    currentlyPlayingMessageId = null
                    currentPlayingLine = null
                }
            }
        }, "TgVoicePlayer").apply {
            isDaemon = true
            start()
        }
    }
}
