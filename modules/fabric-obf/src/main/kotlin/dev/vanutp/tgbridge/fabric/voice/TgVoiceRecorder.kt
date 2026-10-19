package dev.vanutp.tgbridge.fabric.voice

import dev.vanutp.tgbridge.common.modules.VoiceChatPlugin
import dev.vanutp.tgbridge.common.modules.createOgg
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

object TgVoiceRecorder {
    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var targetLine: TargetDataLine? = null
    private val opusPackets = mutableListOf<ByteArray>()
    private var startTimeMs: Long = 0

    val recording: Boolean
        get() = isRecording.get()

    val durationSeconds: Int
        get() = if (startTimeMs > 0) ((System.currentTimeMillis() - startTimeMs) / 1000).toInt() else 0

    fun startRecording(): Boolean {
        if (isRecording.getAndSet(true)) return false
        synchronized(opusPackets) {
            opusPackets.clear()
        }
        startTimeMs = System.currentTimeMillis()

        try {
            val format = AudioFormat(48000.0f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) {
                isRecording.set(false)
                return false
            }
            val line = AudioSystem.getLine(info) as TargetDataLine
            line.open(format)
            line.start()
            targetLine = line

            val encoder = VoiceChatPlugin.api?.createEncoder()

            recordThread = Thread({
                val buffer = ByteArray(1920)
                val shorts = ShortArray(960)

                while (isRecording.get()) {
                    var bytesRead = 0
                    while (bytesRead < buffer.size && isRecording.get()) {
                        val read = line.read(buffer, bytesRead, buffer.size - bytesRead)
                        if (read <= 0) break
                        bytesRead += read
                    }
                    if (bytesRead == buffer.size) {
                        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        if (encoder != null) {
                            val packet = encoder.encode(shorts)
                            if (packet != null && packet.isNotEmpty()) {
                                synchronized(opusPackets) {
                                    opusPackets.add(packet)
                                }
                            }
                        }
                    }
                }
                try {
                    encoder?.close()
                } catch (_: Exception) {}
            }, "Tgbridge-VoiceRecorder").apply {
                isDaemon = true
                start()
            }
            return true
        } catch (e: Exception) {
            isRecording.set(false)
            return false
        }
    }

    fun stopRecordingAndEncode(): ByteArray? {
        if (!isRecording.getAndSet(false)) return null
        try {
            targetLine?.stop()
            targetLine?.close()
            targetLine = null
            recordThread?.join(300)
        } catch (_: Exception) {}

        synchronized(opusPackets) {
            if (opusPackets.isEmpty()) return null
            return try {
                createOgg(opusPackets)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun cancelRecording() {
        if (!isRecording.getAndSet(false)) return
        try {
            targetLine?.stop()
            targetLine?.close()
            targetLine = null
        } catch (_: Exception) {}
        synchronized(opusPackets) {
            opusPackets.clear()
        }
    }
}
