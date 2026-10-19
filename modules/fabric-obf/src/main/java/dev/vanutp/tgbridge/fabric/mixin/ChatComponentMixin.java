package dev.vanutp.tgbridge.fabric.mixin;

import dev.vanutp.tgbridge.fabric.gui.TgChatOverlay;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component tgbridge$modifyIncomingChatMessage(Component message) {
        return TgChatOverlay.INSTANCE.formatPhotoMessage(message);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tgbridge$renderChatThumbnails(GuiGraphics guiGraphics, Font font, int tickCount, int mouseX, int mouseY, boolean focused, boolean isChatOpen, CallbackInfo ci) {
        if (!focused) {
            TgChatOverlay.INSTANCE.renderChatThumbnails(guiGraphics, -1, -1, false);
        }
    }
}
