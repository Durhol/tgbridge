package dev.vanutp.tgbridge.fabric

import com.google.gson.Gson
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.vanutp.tgbridge.fabric.client.TgClientManager
import dev.vanutp.tgbridge.fabric.image.TgImageManager
import dev.vanutp.tgbridge.fabric.network.TgPayload
import dev.vanutp.tgbridge.fabric.voice.TgVoiceManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.resources.language.ClientLanguage
import net.minecraft.locale.Language
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import net.minecraft.network.chat.Component as Text

object FabricTelegramBridgeClient : ClientModInitializer {
    private fun onDumpLangCommand(ctx: CommandContext<FabricClientCommandSource>): Int {
        val configDir = FabricLoader.getInstance().configDir.resolve(FabricTelegramBridge.MOD_ID)
        configDir.createDirectories()
        val minecraftLangFile = configDir.resolve("minecraft_lang.json")
        val translations = (Language.getInstance() as ClientLanguage).storage
        minecraftLangFile.writeText(Gson().toJson(translations))
        ctx.source.sendFeedback(Text.literal("minecraft_lang.json created"))
        return 1
    }

    private fun onPhotoCommand(ctx: CommandContext<FabricClientCommandSource>): Int {
        val id = IntegerArgumentType.getInteger(ctx, "id")
        TgClientManager.openPhotoViewer(id)
        return 1
    }

    private fun onVoiceCommand(ctx: CommandContext<FabricClientCommandSource>): Int {
        val id = IntegerArgumentType.getInteger(ctx, "id")
        TgVoiceManager.playVoiceMessage(id)
        return 1
    }

    private fun onReplyCommand(ctx: CommandContext<FabricClientCommandSource>): Int {
        val id = IntegerArgumentType.getInteger(ctx, "id")
        val msg = TgImageManager.photoMessages[id]
        val sender = msg?.senderName ?: "Telegram"
        val text = msg?.text ?: ""
        TgClientManager.setReply(id, sender, text)
        return 1
    }

    override fun onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(TgPayload.TYPE, TgPayload.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(TgPayload.TYPE, TgPayload.STREAM_CODEC)

        ClientPlayNetworking.registerGlobalReceiver(TgPayload.TYPE) { payload, context ->
            context.client().execute {
                TgClientManager.handlePacket(payload.packet)
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("tgbridge")
                    .then(
                        ClientCommandManager.literal("dump_lang")
                            .executes(::onDumpLangCommand)
                    )
            )

            dispatcher.register(
                ClientCommandManager.literal("tgbridge_client")
                    .then(
                        ClientCommandManager.literal("photo")
                            .then(
                                ClientCommandManager.argument("id", IntegerArgumentType.integer())
                                    .executes(::onPhotoCommand)
                            )
                    )
                    .then(
                        ClientCommandManager.literal("voice")
                            .then(
                                ClientCommandManager.argument("id", IntegerArgumentType.integer())
                                    .executes(::onVoiceCommand)
                            )
                    )
                    .then(
                        ClientCommandManager.literal("reply")
                            .then(
                                ClientCommandManager.argument("id", IntegerArgumentType.integer())
                                    .executes(::onReplyCommand)
                            )
                    )
            )
        }
    }
}
