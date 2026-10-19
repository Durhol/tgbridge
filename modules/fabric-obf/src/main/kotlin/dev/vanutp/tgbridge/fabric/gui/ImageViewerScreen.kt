package dev.vanutp.tgbridge.fabric.gui

import dev.vanutp.tgbridge.fabric.image.TgImageManager
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class ImageViewerScreen(val messageId: Int) : Screen(Component.literal("Просмотр изображения")) {

    override fun init() {
        super.init()
        TgImageManager.requestFullPhoto(messageId)
        addRenderableWidget(
            Button.builder(Component.literal("Закрыть [ESC]")) { onClose() }
                .bounds(width - 110, 10, 100, 20)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xEE121418.toInt())

        val texture = TgImageManager.textures[messageId]
        val dim = TgImageManager.dimensions[messageId]
        val msg = TgImageManager.photoMessages[messageId]

        val headerText = msg?.let { "Фото от @${it.senderName}" } ?: "Просмотр фото"
        guiGraphics.drawCenteredString(font, headerText, width / 2, 16, 0xFFFFFFFF.toInt())

        if (texture != null && dim != null) {
            val origW = dim.first
            val origH = dim.second

            val maxW = (width - 60).coerceAtLeast(100)
            val maxH = (height - 80).coerceAtLeast(100)
            val scale = minOf(maxW.toFloat() / origW, maxH.toFloat() / origH)

            val drawW = (origW * scale).toInt()
            val drawH = (origH * scale).toInt()
            val drawX = (width - drawW) / 2
            val drawY = (height - drawH) / 2

            // Dark frame & shadow
            guiGraphics.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0xFF333842.toInt())
            guiGraphics.fill(drawX - 1, drawY - 1, drawX + drawW + 1, drawY + drawH + 1, 0xFF181A20.toInt())
            // In Minecraft 1.21.11: blit(Identifier id, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1)
            guiGraphics.blit(texture, drawX, drawY, drawX + drawW, drawY + drawH, 0.0f, 1.0f, 0.0f, 1.0f)

            val details = TgImageManager.photoDetails[messageId]
            if (details != null && !details.isFullRes) {
                guiGraphics.drawCenteredString(font, "Загрузка полного качества...", width / 2, drawY + drawH + 4, 0xFFAAAAAA.toInt())
            }

            val caption = msg?.text?.takeIf { it.isNotBlank() }
            if (caption != null) {
                guiGraphics.drawCenteredString(font, caption, width / 2, (drawY + drawH + 16).coerceAtMost(height - 18), 0xFFEEEEEE.toInt())
            }
        } else {
            guiGraphics.drawCenteredString(font, "Загрузка изображения...", width / 2, height / 2, 0xFFAAAAAA.toInt())
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun isPauseScreen(): Boolean = false
}
