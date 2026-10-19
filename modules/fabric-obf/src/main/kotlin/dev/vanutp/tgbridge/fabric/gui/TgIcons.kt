package dev.vanutp.tgbridge.fabric.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier

object TgIcons {

    /**
     * Draws a crisp paperclip icon within a 12x12 bounding box starting at (x, y).
     */
    fun drawPaperclip(g: GuiGraphics, x: Int, y: Int, color: Int) {
        // Outer loop
        g.fill(x + 4, y + 1, x + 8, y + 2, color)      // top curve
        g.fill(x + 8, y + 2, x + 9, y + 9, color)      // right outer down
        g.fill(x + 4, y + 9, x + 8, y + 10, color)     // bottom outer curve
        g.fill(x + 3, y + 4, x + 4, y + 9, color)      // left outer up
        g.fill(x + 4, y + 3, x + 6, y + 4, color)      // top inner curve
        g.fill(x + 6, y + 4, x + 7, y + 7, color)      // right inner down
        g.fill(x + 5, y + 7, x + 6, y + 8, color)      // bottom inner
    }

    /**
     * Draws a crisp microphone icon within a 12x12 bounding box starting at (x, y).
     */
    fun drawMicrophone(g: GuiGraphics, x: Int, y: Int, color: Int, isRecording: Boolean = false) {
        // Capsule (mic head)
        g.fill(x + 4, y + 1, x + 7, y + 6, color)

        // U-shaped cup around capsule
        g.fill(x + 2, y + 4, x + 3, y + 7, color)      // left cup wall
        g.fill(x + 8, y + 4, x + 9, y + 7, color)      // right cup wall
        g.fill(x + 3, y + 7, x + 8, y + 8, color)      // bottom cup floor

        // Stem & base
        g.fill(x + 5, y + 8, x + 6, y + 10, color)     // center stand stem
        g.fill(x + 3, y + 10, x + 8, y + 11, color)    // bottom base bar

        if (isRecording) {
            // Sound wave arcs
            val waveColor = 0xFFFF4444.toInt()
            g.fill(x + 0, y + 3, x + 1, y + 7, waveColor)
            g.fill(x + 10, y + 3, x + 11, y + 7, waveColor)
        }
    }

    /**
     * Draws a crisp close/cross (X) icon within a size x size box.
     */
    fun drawCloseCross(g: GuiGraphics, x: Int, y: Int, size: Int = 8, color: Int = 0xFFAAAAAA.toInt()) {
        for (i in 0 until size) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color)
            g.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, color)
        }
    }

    /**
     * Draws a crisp checkmark icon within a size x size box.
     */
    fun drawCheckmark(g: GuiGraphics, x: Int, y: Int, color: Int = 0xFF55FF55.toInt()) {
        g.fill(x + 1, y + 4, x + 2, y + 6, color)
        g.fill(x + 2, y + 5, x + 3, y + 7, color)
        g.fill(x + 3, y + 6, x + 4, y + 8, color)
        g.fill(x + 4, y + 5, x + 5, y + 7, color)
        g.fill(x + 5, y + 4, x + 6, y + 6, color)
        g.fill(x + 6, y + 3, x + 7, y + 5, color)
        g.fill(x + 7, y + 2, x + 8, y + 4, color)
    }

    /**
     * Draws a crisp reply curved arrow icon pointing left.
     */
    fun drawReplyArrow(g: GuiGraphics, x: Int, y: Int, color: Int = 0xFFAAAAAA.toInt()) {
        // Arrow head pointing left
        g.fill(x + 0, y + 3, x + 1, y + 4, color)
        g.fill(x + 1, y + 2, x + 2, y + 5, color)
        g.fill(x + 2, y + 1, x + 3, y + 6, color)

        // Arrow stem curving down-right
        g.fill(x + 3, y + 3, x + 6, y + 4, color)      // horizontal line
        g.fill(x + 6, y + 4, x + 7, y + 7, color)      // vertical curve down
    }

    /**
     * Draws a camera / image icon placeholder.
     */
    fun drawImageIcon(g: GuiGraphics, x: Int, y: Int, color: Int) {
        // Outer frame
        g.fill(x + 1, y + 2, x + 11, y + 3, color)
        g.fill(x + 1, y + 9, x + 11, y + 10, color)
        g.fill(x + 1, y + 3, x + 2, y + 9, color)
        g.fill(x + 10, y + 3, x + 11, y + 9, color)

        // Sun dot
        g.fill(x + 8, y + 4, x + 9, y + 5, color)

        // Mountain peaks
        g.fill(x + 4, y + 5, x + 5, y + 6, color)
        g.fill(x + 3, y + 6, x + 6, y + 7, color)
        g.fill(x + 2, y + 7, x + 10, y + 9, color)
    }

    /**
     * Draws a sleek miniature image thumbnail inside chat or banners.
     */
    fun drawMiniatureThumbnail(
        g: GuiGraphics,
        textureLocation: Identifier?,
        x: Int,
        y: Int,
        w: Int = 22,
        h: Int = 10,
        isHovered: Boolean = false,
        onClickHint: String? = null
    ) {
        // Shadow & Background
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, if (isHovered) 0xFF55FFFF.toInt() else 0xCC111111.toInt())
        g.fill(x, y, x + w, y + h, 0xFF181818.toInt())

        if (textureLocation != null) {
            // In Minecraft 1.21.11 GuiGraphics.blit takes: (id, x0, y0, x1, y1, u0, u1, v0, v1)
            g.blit(textureLocation, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f)
        } else {
            // Loading placeholder with camera icon
            drawImageIcon(g, x + (w - 12) / 2, y + (h - 12) / 2, 0xFF888888.toInt())
        }

        // Tiny magnifying glass or expand icon in bottom-right corner when hovered
        if (isHovered) {
            g.fill(x + w - 4, y + h - 4, x + w, y + h, 0xEE000000.toInt())
            g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, 0xFF55FFFF.toInt())
        }
    }
}
