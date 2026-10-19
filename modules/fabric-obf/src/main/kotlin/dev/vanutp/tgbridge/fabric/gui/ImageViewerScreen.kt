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
        addRenderableWidget(
            Button.builder(Component.literal("✕ Закрыть [ESC]")) { onClose() }
                .bounds(width - 120, 10, 110, 20)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xE6101010.toInt())

        val texture = TgImageManager.textures[messageId]
        val dim = TgImageManager.dimensions[messageId]
        val msg = TgImageManager.photoMessages[messageId]

        val headerText = msg?.let { "Фото от @${it.senderName}" } ?: "Просмотр фото"
        guiGraphics.drawCenteredString(font, headerText, width / 2, 16, 0xFFFFFFFF.toInt())

        if (texture != null && dim != null) {
            val origW = dim.first
            val origH = dim.second

            val maxW = (width - 80).coerceAtLeast(100)
            val maxH = (height - 90).coerceAtLeast(100)
            val scale = minOf(maxW.toFloat() / origW, maxH.toFloat() / origH, 2.0f)

            val drawW = (origW * scale).toInt()
            val drawH = (origH * scale).toInt()
            val drawX = (width - drawW) / 2
            val drawY = (height - drawH) / 2

            guiGraphics.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0xFF404040.toInt())
            guiGraphics.blit(texture, drawX, drawY, drawW, drawH, 0f, 0f, 1f, 1f)

            val caption = msg?.text?.takeIf { it.isNotBlank() }
            if (caption != null) {
                guiGraphics.drawCenteredString(font, caption, width / 2, (drawY + drawH + 8).coerceAtMost(height - 18), 0xFFDDDDDD.toInt())
            }
        } else {
            guiGraphics.drawCenteredString(font, "Загрузка изображения из Telegram...", width / 2, height / 2, 0xFFAAAAAA.toInt())
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
