package dev.vanutp.tgbridge.fabric.image

import com.mojang.blaze3d.platform.NativeImage
import dev.vanutp.tgbridge.common.network.C2SRequestPhotoPacket
import dev.vanutp.tgbridge.common.network.S2CTelegramMessagePacket
import dev.vanutp.tgbridge.fabric.network.TgPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

data class AttachedImageInfo(
    val textureLocation: Identifier,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

data class PhotoDetails(
    val textureLocation: Identifier,
    val width: Int,
    val height: Int,
    val isFullRes: Boolean,
)

object TgImageManager {
    val textures = ConcurrentHashMap<Int, Identifier>()
    val dimensions = ConcurrentHashMap<Int, Pair<Int, Int>>()
    val photoDetails = ConcurrentHashMap<Int, PhotoDetails>()
    val photoMessages = ConcurrentHashMap<Int, S2CTelegramMessagePacket>()
    val fullPhotoCache = ConcurrentHashMap<Int, ByteArray>()

    private val pendingPhotoChunks = ConcurrentHashMap<Int, Array<String?>>()

    var activeAttachment: AttachedImageInfo? = null

    fun loadPhotoFromPacket(packet: S2CTelegramMessagePacket) {
        photoMessages[packet.messageId] = packet

        // If thumbnail is embedded directly in packet, load immediately
        val thumbBase64 = packet.thumbBase64
        if (thumbBase64 != null && !textures.containsKey(packet.messageId)) {
            try {
                val bytes = Base64.getDecoder().decode(thumbBase64)
                registerImageBytes(packet.messageId, bytes, isFull = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestFullPhoto(messageId: Int) {
        if (fullPhotoCache.containsKey(messageId)) return
        ClientPlayNetworking.send(TgPayload(C2SRequestPhotoPacket(messageId)))
    }

    fun handlePhotoChunk(messageId: Int, chunkIndex: Int, totalChunks: Int, chunkBase64: String) {
        val chunks = pendingPhotoChunks.computeIfAbsent(messageId) { arrayOfNulls(totalChunks) }
        chunks[chunkIndex] = chunkBase64

        if (chunks.all { it != null }) {
            pendingPhotoChunks.remove(messageId)
            val fullBase64 = chunks.joinToString("")
            try {
                val bytes = Base64.getDecoder().decode(fullBase64)
                fullPhotoCache[messageId] = bytes
                registerImageBytes(messageId, bytes, isFull = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun registerImageBytes(messageId: Int, bytes: ByteArray, isFull: Boolean) {
        Thread({
            try {
                val buffered = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@Thread
                val baos = ByteArrayOutputStream()
                ImageIO.write(buffered, "png", baos)
                val pngBytes = baos.toByteArray()

                Minecraft.getInstance().execute {
                    try {
                        val nativeImage = NativeImage.read(pngBytes)
                        val w = nativeImage.width
                        val h = nativeImage.height
                        val textureName = if (isFull) "photo_full_$messageId" else "photo_thumb_$messageId"
                        val dynamicTexture = DynamicTexture({ textureName }, nativeImage)
                        val loc = Identifier.fromNamespaceAndPath("tgbridge", textureName)
                        Minecraft.getInstance().textureManager.register(loc, dynamicTexture)
                        textures[messageId] = loc
                        dimensions[messageId] = Pair(w, h)
                        photoDetails[messageId] = PhotoDetails(loc, w, h, isFull)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }, "TgImageLoader-$messageId").start()
    }

    fun optimizeForUpload(input: BufferedImage): ByteArray {
        var img = input
        val maxDim = 1280
        if (img.width > maxDim || img.height > maxDim) {
            val scale = minOf(maxDim.toDouble() / img.width, maxDim.toDouble() / img.height)
            val targetW = (img.width * scale).toInt()
            val targetH = (img.height * scale).toInt()
            val scaled = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(img, 0, 0, targetW, targetH, null)
            g.dispose()
            img = scaled
        }

        val rgbImg = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val g2 = rgbImg.createGraphics()
        g2.color = Color.WHITE
        g2.fillRect(0, 0, img.width, img.height)
        g2.drawImage(img, 0, 0, null)
        g2.dispose()

        val baos = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        if (writers.hasNext()) {
            val writer = writers.next()
            val param = writer.defaultWriteParam
            param.compressionMode = ImageWriteParam.MODE_EXPLICIT
            param.compressionQuality = 0.85f
            val ios = ImageIO.createImageOutputStream(baos)
            writer.output = ios
            writer.write(null, IIOImage(rgbImg, null, null), param)
            writer.dispose()
            ios.close()
            return baos.toByteArray()
        }

        ImageIO.write(rgbImg, "jpg", baos)
        return baos.toByteArray()
    }

    fun setAttachedImage(bufferedImage: BufferedImage): AttachedImageInfo? {
        return try {
            val optimizedBytes = optimizeForUpload(bufferedImage)
            val baos = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "png", baos)
            val pngBytes = baos.toByteArray()

            val nativeImage = NativeImage.read(pngBytes)
            val w = nativeImage.width
            val h = nativeImage.height
            val dynamicTexture = DynamicTexture({ "tgbridge_attached" }, nativeImage)
            val loc = Identifier.fromNamespaceAndPath(
                "tgbridge",
                "attached_${System.currentTimeMillis()}"
            )
            Minecraft.getInstance().textureManager.register(loc, dynamicTexture)
            val info = AttachedImageInfo(loc, w, h, optimizedBytes)
            activeAttachment = info
            info
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    fun clearAttachedImage() {
        activeAttachment = null
    }
}
