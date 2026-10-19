package dev.vanutp.tgbridge.fabric.gui

import dev.vanutp.tgbridge.fabric.client.TgClientManager
import dev.vanutp.tgbridge.fabric.image.TgImageManager
import dev.vanutp.tgbridge.fabric.mixin.ChatComponentAccessor
import dev.vanutp.tgbridge.fabric.voice.TgVoiceRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.PlainTextContents
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage

object TgChatOverlay {

    var hoveredThumbnailId: Int? = null

    fun formatPhotoMessage(component: Component): Component {
        return try {
            val str = component.string
            if (!str.contains("Фото") && !str.contains("/tgbridge_client photo")) {
                component
            } else {
                replacePhotoPlaceholderRecursive(component)
            }
        } catch (_: Exception) {
            component
        }
    }

    private fun replacePhotoPlaceholderRecursive(comp: Component): Component {
        val contents = comp.contents
        val newComp = if (contents is PlainTextContents) {
            val txt = contents.text()
            if (txt.contains("[Фото]")) {
                Component.literal(txt.replace("[Фото]", "       "))
            } else {
                comp.plainCopy()
            }
        } else {
            comp.plainCopy()
        }

        newComp.style = comp.style

        for (sibling in comp.siblings) {
            newComp.append(replacePhotoPlaceholderRecursive(sibling))
        }
        return newComp
    }

    fun renderChatThumbnails(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, isChatOpen: Boolean) {
        val mc = Minecraft.getInstance()
        val chat = mc.gui.chat as? ChatComponentAccessor ?: return
        val trimmed = chat.trimmedMessages ?: return
        if (trimmed.isEmpty()) return

        val scale = chat.invokeGetScale().toFloat()
        if (scale <= 0f) return
        val lineHeight = chat.invokeGetLineHeight()
        val scroll = if (isChatOpen) chat.chatScrollbarPos else 0
        val linesPerPage = chat.invokeGetLinesPerPage()

        val guiHeight = mc.window.guiScaledHeight
        val chatBottom = guiHeight - 40
        val currentTicks = mc.gui.guiTicks

        hoveredThumbnailId = null

        guiGraphics.pose().pushMatrix()
        guiGraphics.pose().scale(scale, scale)

        val drawnPhotos = mutableSetOf<Int>()
        val maxLines = minOf(linesPerPage, trimmed.size - scroll)

        for (i in 0 until maxLines) {
            val lineIdx = i + scroll
            if (lineIdx < 0 || lineIdx >= trimmed.size) continue
            val line = trimmed[lineIdx]

            // When chat is closed (HUD mode), don't draw messages older than 200 ticks (10s)
            if (!isChatOpen) {
                val age = currentTicks - line.addedTime()
                if (age >= 200) continue
            }

            var foundPhotoId: Int? = null
            var photoXOffset = 0
            var currentX = 0

            line.content().accept { _, style, codePoint ->
                val click = style.clickEvent
                if (click is ClickEvent.RunCommand) {
                    val cmd = click.command()
                    if (cmd.startsWith("/tgbridge_client photo ")) {
                        if (foundPhotoId == null) {
                            val parsedId = cmd.removePrefix("/tgbridge_client photo ").trim().toIntOrNull()
                            if (parsedId != null && !drawnPhotos.contains(parsedId)) {
                                foundPhotoId = parsedId
                                photoXOffset = currentX
                            }
                        }
                    }
                }
                val charStr = Character.toString(codePoint)
                currentX += mc.font.width(charStr)
                true
            }

            if (foundPhotoId != null) {
                drawnPhotos.add(foundPhotoId)
                val lineY = (chatBottom / scale).toInt() - (i + 1) * lineHeight
                val startX = 4 + photoXOffset + 1
                val thumbW = 22
                val thumbH = 9

                val scaledMouseX = (mouseX / scale).toInt()
                val scaledMouseY = (mouseY / scale).toInt()
                val isSuggestionsOpen = (mc.screen as? dev.vanutp.tgbridge.fabric.mixin.IChatScreenSuggestions)?.`tgbridge$isSuggestionsVisible`() == true
                val isCoveredBySuggestions = isSuggestionsOpen && (scaledMouseX in 0..280) && (scaledMouseY in (lineY - 20)..((chatBottom / scale).toInt()))
                val isHovered = isChatOpen && !isCoveredBySuggestions && (scaledMouseX in startX..(startX + thumbW)) && (scaledMouseY in lineY..(lineY + thumbH))

                val texture = TgImageManager.textures[foundPhotoId]
                TgIcons.drawMiniatureThumbnail(guiGraphics, texture, startX, lineY, thumbW, thumbH, isHovered)

                if (isHovered) {
                    hoveredThumbnailId = foundPhotoId
                }
            }
        }

        guiGraphics.pose().popMatrix()

        if (hoveredThumbnailId != null && isChatOpen) {
            guiGraphics.setTooltipForNextFrame(Component.literal("Нажмите, чтобы развернуть фото"), mouseX, mouseY)
        }
    }

    fun render(guiGraphics: GuiGraphics, input: EditBox, width: Int, height: Int, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font

        // Note: Chat line thumbnails are rendered in ChatComponent.render via ChatComponentMixin
        // so that CommandSuggestions (autocomplete) naturally and correctly renders on top of thumbnails.

        // 2. Action buttons next to chat input
        val clipX = width - 36
        val clipY = height - 14
        val micX = width - 18
        val micY = height - 14

        val clipHover = mouseX in clipX..(clipX + 16) && mouseY in clipY..(clipY + 12)
        val micHover = mouseX in micX..(micX + 16) && mouseY in micY..(micY + 12)

        // Paperclip button
        guiGraphics.fill(clipX, clipY, clipX + 16, clipY + 12, if (clipHover) 0xDD444A52.toInt() else 0x99202328.toInt())
        guiGraphics.fill(clipX, clipY, clipX + 16, clipY + 1, 0x66556677.toInt())
        TgIcons.drawPaperclip(guiGraphics, clipX + 2, clipY, if (clipHover) 0xFF55FFFF.toInt() else 0xFFCCCCCC.toInt())

        // Microphone button
        val micBg = if (TgVoiceRecorder.recording) {
            0xFFCC2222.toInt()
        } else if (micHover) {
            0xDD444A52.toInt()
        } else {
            0x99202328.toInt()
        }
        guiGraphics.fill(micX, micY, micX + 16, micY + 12, micBg)
        guiGraphics.fill(micX, micY, micX + 16, micY + 1, 0x66556677.toInt())
        TgIcons.drawMicrophone(guiGraphics, micX + 2, micY, if (TgVoiceRecorder.recording) 0xFFFFFFFF.toInt() else if (micHover) 0xFF55FF55.toInt() else 0xFFCCCCCC.toInt(), TgVoiceRecorder.recording)

        if (clipHover) {
            guiGraphics.setTooltipForNextFrame(Component.literal("Прикрепить фото (Ctrl+V или клик)"), mouseX, mouseY)
        } else if (micHover) {
            val micText = if (TgVoiceRecorder.recording) "Идет запись... Нажмите для отправки" else "Записать голосовое сообщение"
            guiGraphics.setTooltipForNextFrame(Component.literal(micText), mouseX, mouseY)
        }

        var bannerY = height - 28

        // 3. Voice recording banner
        if (TgVoiceRecorder.recording) {
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 13, 0xEE2A1212.toInt())
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 1, 0xFFFF4444.toInt())

            // Pulsing recording dot
            guiGraphics.fill(6, bannerY + 4, 11, bannerY + 9, 0xFFFF3333.toInt())

            val duration = TgVoiceRecorder.durationSeconds
            val secStr = if (duration % 60 < 10) "0${duration % 60}" else "${duration % 60}"
            val minStr = "${duration / 60}"
            guiGraphics.drawString(font, "Запись голосового... $minStr:$secStr", 16, bannerY + 3, 0xFFFF6666.toInt())

            // Send button with checkmark
            val sendX = width - 85
            val sendHover = mouseX in sendX..(sendX + 55) && mouseY in bannerY..(bannerY + 12)
            guiGraphics.fill(sendX, bannerY + 1, sendX + 55, bannerY + 12, if (sendHover) 0xDD2A662A.toInt() else 0x991A441A.toInt())
            TgIcons.drawCheckmark(guiGraphics, sendX + 2, bannerY + 2, 0xFF55FF55.toInt())
            guiGraphics.drawString(font, "Отправить", sendX + 12, bannerY + 3, 0xFFFFFFFF.toInt())

            // Cancel button with cross
            val cancelX = width - 22
            val cancelHover = mouseX in cancelX..(cancelX + 16) && mouseY in bannerY..(bannerY + 12)
            guiGraphics.fill(cancelX, bannerY + 1, cancelX + 16, bannerY + 12, if (cancelHover) 0xDD662A2A.toInt() else 0x99441A1A.toInt())
            TgIcons.drawCloseCross(guiGraphics, cancelX + 4, bannerY + 3, 6, 0xFFFF6666.toInt())

            bannerY -= 15
        }

        // 4. Attached image banner
        val attachment = TgImageManager.activeAttachment
        if (attachment != null) {
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 14, 0xEE162330.toInt())
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 1, 0xFF3388CC.toInt())

            // Miniature thumbnail
            TgIcons.drawMiniatureThumbnail(guiGraphics, attachment.textureLocation, 5, bannerY + 2, 20, 10, false)

            guiGraphics.drawString(font, "Фото прикреплено (${attachment.width}x${attachment.height})", 30, bannerY + 3, 0xFF55FFFF.toInt())

            // Remove button with cross
            val cancelX = width - 22
            val cancelHover = mouseX in cancelX..(cancelX + 16) && mouseY in bannerY..(bannerY + 13)
            guiGraphics.fill(cancelX, bannerY + 1, cancelX + 16, bannerY + 13, if (cancelHover) 0xDD662A2A.toInt() else 0x99441A1A.toInt())
            TgIcons.drawCloseCross(guiGraphics, cancelX + 4, bannerY + 4, 6, 0xFFAAAAAA.toInt())

            bannerY -= 16
        }

        // 5. Active reply banner
        val reply = TgClientManager.activeReply
        if (reply != null) {
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 13, 0xEE20242A.toInt())
            guiGraphics.fill(2, bannerY, width - 4, bannerY + 1, 0xFF556677.toInt())

            // Curved reply arrow
            TgIcons.drawReplyArrow(guiGraphics, 6, bannerY + 2, 0xFFAAAAAA.toInt())

            val shortText = if (reply.text.length > 35) reply.text.take(35) + "..." else reply.text
            guiGraphics.drawString(font, "Ответ @${reply.senderName}: \"$shortText\"", 18, bannerY + 3, 0xFFAAAAAA.toInt())

            // Close button with cross
            val cancelX = width - 22
            val cancelHover = mouseX in cancelX..(cancelX + 16) && mouseY in bannerY..(bannerY + 12)
            guiGraphics.fill(cancelX, bannerY + 1, cancelX + 16, bannerY + 12, if (cancelHover) 0xDD662A2A.toInt() else 0x99441A1A.toInt())
            TgIcons.drawCloseCross(guiGraphics, cancelX + 4, bannerY + 3, 6, 0xFFAAAAAA.toInt())

            bannerY -= 15
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
        val width = screen.width
        val height = screen.height
        val mouseX = mouseEvent.x().toInt()
        val mouseY = mouseEvent.y().toInt()

        // 1. Click on thumbnail in chat
        val clickedPhoto = hoveredThumbnailId
        if (clickedPhoto != null) {
            TgClientManager.openPhotoViewer(clickedPhoto)
            return true
        }

        // 2. Click action buttons
        val clipX = width - 36
        val clipY = height - 14
        val micX = width - 18
        val micY = height - 14

        // Click paperclip
        if (mouseX in clipX..(clipX + 16) && mouseY in clipY..(clipY + 12)) {
            pasteImageFromClipboard()
            return true
        }

        // Click microphone
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
            if (mouseY in bannerY..(bannerY + 13) && mouseX in 2..(width - 4)) {
                val cancelX = width - 22
                val sendX = width - 85
                if (mouseX >= cancelX) {
                    TgVoiceRecorder.cancelRecording()
                } else if (mouseX in sendX..(sendX + 55)) {
                    val ogg = TgVoiceRecorder.stopRecordingAndEncode()
                    if (ogg != null) {
                        TgClientManager.sendVoice(ogg, TgVoiceRecorder.durationSeconds, TgClientManager.activeReply?.messageId)
                        TgClientManager.clearReply()
                    }
                }
                return true
            }
            bannerY -= 15
        }

        // Attachment banner click
        if (TgImageManager.activeAttachment != null) {
            if (mouseY in bannerY..(bannerY + 14) && mouseX in 2..(width - 4)) {
                val cancelX = width - 22
                if (mouseX >= cancelX) {
                    TgImageManager.clearAttachedImage()
                }
                return true
            }
            bannerY -= 16
        }

        // Reply banner click
        if (TgClientManager.activeReply != null) {
            if (mouseY in bannerY..(bannerY + 13) && mouseX in 2..(width - 4)) {
                val cancelX = width - 22
                if (mouseX >= cancelX) {
                    TgClientManager.clearReply()
                }
                return true
            }
            bannerY -= 15
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
