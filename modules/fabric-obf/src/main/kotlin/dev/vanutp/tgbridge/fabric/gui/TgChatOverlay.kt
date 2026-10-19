package dev.vanutp.tgbridge.fabric.gui

import dev.vanutp.tgbridge.fabric.client.TgClientManager
import dev.vanutp.tgbridge.fabric.image.TgImageManager
import dev.vanutp.tgbridge.fabric.voice.TgVoiceRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage

object TgChatOverlay {

    fun render(guiGraphics: GuiGraphics, input: EditBox, width: Int, height: Int, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font

        // Draw [📎] and [🎙️] action buttons next to input box
        val clipX = width - 36
        val clipY = height - 14
        val micX = width - 18
        val micY = height - 14

        val clipHover = mouseX in clipX..(clipX + 16) && mouseY in clipY..(clipY + 12)
        val micHover = mouseX in micX..(micX + 16) && mouseY in micY..(micY + 12)

        guiGraphics.fill(clipX, clipY, clipX + 16, clipY + 12, if (clipHover) 0xDD555555.toInt() else 0x99222222.toInt())
        guiGraphics.drawString(font, "📎", clipX + 2, clipY + 2, 0xFFFFFFFF.toInt())

        val micBg = if (TgVoiceRecorder.recording) {
            0xFFCC2222.toInt()
        } else if (micHover) {
            0xDD555555.toInt()
        } else {
            0x99222222.toInt()
        }
        guiGraphics.fill(micX, micY, micX + 16, micY + 12, micBg)
        guiGraphics.drawString(font, "🎙", micX + 2, micY + 2, 0xFFFFFFFF.toInt())

        if (clipHover) {
            guiGraphics.setTooltipForNextFrame(Component.literal("Прикрепить фото (Ctrl+V или клик)"), mouseX, mouseY)
        } else if (micHover) {
            val micText = if (TgVoiceRecorder.recording) "Идет запись... Нажмите для отправки" else "Записать голосовое сообщение"
            guiGraphics.setTooltipForNextFrame(Component.literal(micText), mouseX, mouseY)
        }

        var bannerY = height - 28

        // 1. Voice recording banner
        if (TgVoiceRecorder.recording) {
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 12, 0xEE441111.toInt())
            val duration = TgVoiceRecorder.durationSeconds
            val secStr = if (duration % 60 < 10) "0${duration % 60}" else "${duration % 60}"
            val minStr = "${duration / 60}"
            guiGraphics.drawString(font, "● Запись голосового... $minStr:$secStr  [✓ Отправить] [✕]", 6, bannerY + 2, 0xFFFF5555.toInt())
            bannerY -= 14
        }

        // 2. Attached image banner
        val attachment = TgImageManager.activeAttachment
        if (attachment != null) {
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 14, 0xEE1E2A38.toInt())
            guiGraphics.blit(attachment.textureLocation, 6, bannerY + 1, 14, 12, 0f, 0f, 1f, 1f)
            guiGraphics.drawString(font, "🖼 Фото прикреплено (${attachment.width}x${attachment.height}) [✕]", 24, bannerY + 3, 0xFF55FFFF.toInt())
            bannerY -= 16
        }

        // 3. Active reply banner
        val reply = TgClientManager.activeReply
        if (reply != null) {
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 12, 0xEE2A2A2A.toInt())
            val shortText = if (reply.text.length > 35) reply.text.take(35) + "..." else reply.text
            guiGraphics.drawString(font, "↩ Ответ @${reply.senderName}: \"$shortText\" [✕]", 6, bannerY + 2, 0xFFAAAAAA.toInt())
            bannerY -= 14
        }
    }

    fun onKeyPressed(keyEvent: KeyEvent, input: EditBox, screen: ChatScreen): Boolean {
        // Ctrl + V: Paste image from clipboard
        if (keyEvent.isPaste()) {
            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                    val img = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image
                    if (img != null) {
                        val buffered = if (img is BufferedImage) img else {
                            val b = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)
                            val g = b.createGraphics()
                            g.drawImage(img, 0, 0, null)
                            g.dispose()
                            b
                        }
                        TgImageManager.setAttachedImage(buffered)
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Enter key handling
        if (keyEvent.isConfirmation()) {
            if (TgVoiceRecorder.recording) {
                val ogg = TgVoiceRecorder.stopRecordingAndEncode()
                if (ogg != null) {
                    TgClientManager.sendVoice(ogg, TgVoiceRecorder.durationSeconds, TgClientManager.activeReply?.messageId)
                    TgClientManager.clearReply()
                }
                Minecraft.getInstance().setScreen(null)
                return true
            }

            val attachment = TgImageManager.activeAttachment
            if (attachment != null) {
                TgClientManager.sendPhoto(attachment.bytes, input.value, TgClientManager.activeReply?.messageId)
                TgImageManager.clearAttachedImage()
                TgClientManager.clearReply()
                input.value = ""
                Minecraft.getInstance().setScreen(null)
                return true
            }

            val reply = TgClientManager.activeReply
            if (reply != null && input.value.isNotBlank()) {
                TgClientManager.sendReply(input.value, reply.messageId)
                TgClientManager.clearReply()
                input.value = ""
                Minecraft.getInstance().setScreen(null)
                return true
            }
        }

        // Escape key handling
        if (keyEvent.isEscape()) {
            if (TgVoiceRecorder.recording) {
                TgVoiceRecorder.cancelRecording()
                return true
            }
            if (TgImageManager.activeAttachment != null) {
                TgImageManager.clearAttachedImage()
                return true
            }
            if (TgClientManager.activeReply != null) {
                TgClientManager.clearReply()
                return true
            }
        }

        return false
    }

    fun onMouseClicked(mouseEvent: MouseButtonEvent, isDoubleClick: Boolean, input: EditBox, screen: ChatScreen): Boolean {
        val mc = Minecraft.getInstance()
        val width = screen.width
        val height = screen.height
        val mouseX = mouseEvent.x().toInt()
        val mouseY = mouseEvent.y().toInt()

        val clipX = width - 36
        val clipY = height - 14
        val micX = width - 18
        val micY = height - 14

        // Click [📎] button
        if (mouseX in clipX..(clipX + 16) && mouseY in clipY..(clipY + 12)) {
            pasteImageFromClipboard()
            return true
        }

        // Click [🎙️] button
        if (mouseX in micX..(micX + 16) && mouseY in micY..(micY + 12)) {
            if (TgVoiceRecorder.recording) {
                val ogg = TgVoiceRecorder.stopRecordingAndEncode()
                if (ogg != null) {
                    TgClientManager.sendVoice(ogg, TgVoiceRecorder.durationSeconds, TgClientManager.activeReply?.messageId)
                    TgClientManager.clearReply()
                }
            } else {
                TgVoiceRecorder.startRecording()
            }
            return true
        }

        var bannerY = height - 28

        // Voice banner click
        if (TgVoiceRecorder.recording) {
            if (mouseY in bannerY..(bannerY + 12) && mouseX in 2..(width - 4)) {
                if (mouseX > width - 30) {
                    TgVoiceRecorder.cancelRecording()
                } else {
                    val ogg = TgVoiceRecorder.stopRecordingAndEncode()
                    if (ogg != null) {
                        TgClientManager.sendVoice(ogg, TgVoiceRecorder.durationSeconds, TgClientManager.activeReply?.messageId)
                        TgClientManager.clearReply()
                    }
                }
                return true
            }
            bannerY -= 14
        }

        // Attachment banner click
        if (TgImageManager.activeAttachment != null) {
            if (mouseY in bannerY..(bannerY + 14) && mouseX in 2..(width - 4)) {
                TgImageManager.clearAttachedImage()
                return true
            }
            bannerY -= 16
        }

        // Reply banner click
        if (TgClientManager.activeReply != null) {
            if (mouseY in bannerY..(bannerY + 12) && mouseX in 2..(width - 4)) {
                TgClientManager.clearReply()
                return true
            }
            bannerY -= 14
        }

        return false
    }

    private fun pasteImageFromClipboard() {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                val img = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image
                if (img != null) {
                    val buffered = if (img is BufferedImage) img else {
                        val b = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)
                        val g = b.createGraphics()
                        g.drawImage(img, 0, 0, null)
                        g.dispose()
                        b
                    }
                    TgImageManager.setAttachedImage(buffered)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
