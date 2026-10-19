package dev.vanutp.tgbridge.fabric.network

import dev.vanutp.tgbridge.common.network.TGBRIDGE_CHANNEL
import dev.vanutp.tgbridge.common.network.TgNetworkPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class TgPayload(val packet: TgNetworkPacket) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<TgPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<TgPayload> = CustomPacketPayload.createType(TGBRIDGE_CHANNEL)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TgPayload> = CustomPacketPayload.codec(
            { payload: TgPayload, buf: FriendlyByteBuf ->
                val bytes = payload.packet.toBytes()
                buf.writeBytes(bytes)
            },
            { buf: FriendlyByteBuf ->
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                val pkt = TgNetworkPacket.fromBytes(bytes)
                    ?: throw IllegalArgumentException("Invalid tgbridge packet")
                TgPayload(pkt)
            }
        )
    }
}
