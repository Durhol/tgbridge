package dev.vanutp.tgbridge.fabric.image

import com.mojang.blaze3d.platform.NativeImage
import dev.vanutp.tgbridge.common.network.S2CTelegramMessagePacket
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

data class AttachedImageInfo(
    val textureLocation: Identifier,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

object TgImageManager {
    val textures = ConcurrentHashMap<Int, Identifier>()
    val dimensions = ConcurrentHashMap<Int, Pair<Int, Int>>()
    val photoMessages = ConcurrentHashMap<Int, S2CTelegramMessagePacket>()

    var activeAttachment: AttachedImageInfo? = null

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun loadPhoto(packet: S2CTelegramMessagePacket) {
        photoMessages[packet.messageId] = packet
        val url = packet.photoUrl ?: return
        if (textures.containsKey(packet.messageId)) return

        Thread({
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()
                val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray())
                if (resp.statusCode() in 200..299) {
                    val bytes = resp.body()
                    Minecraft.getInstance().execute {
                        try {
                            val nativeImage = NativeImage.read(bytes)
                            val w = nativeImage.width
                            val h = nativeImage.height
                            val dynamicTexture = DynamicTexture({ "tgbridge_photo_${packet.messageId}" }, nativeImage)
                            val loc = Identifier.fromNamespaceAndPath(
                                "tgbridge",
                                "photo_${packet.messageId}"
                            )
                            Minecraft.getInstance().textureManager.register(loc, dynamicTexture)
                            textures[packet.messageId] = loc
                            dimensions[packet.messageId] = Pair(w, h)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }, "Tgbridge-PhotoLoader-${packet.messageId}").apply {
            isDaemon = true
            start()
        }
    }

    fun setAttachedImage(bufferedImage: BufferedImage): AttachedImageInfo? {
        return try {
            val baos = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "png", baos)
            val bytes = baos.toByteArray()

            val nativeImage = NativeImage.read(bytes)
            val w = nativeImage.width
            val h = nativeImage.height
            val dynamicTexture = DynamicTexture({ "tgbridge_attached" }, nativeImage)
            val loc = Identifier.fromNamespaceAndPath(
                "tgbridge",
                "attached_${System.currentTimeMillis()}"
            )
            Minecraft.getInstance().textureManager.register(loc, dynamicTexture)
            val info = AttachedImageInfo(loc, w, h, bytes)
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
