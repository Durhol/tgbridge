package dev.vanutp.tgbridge.fabric.mixin;

import dev.vanutp.tgbridge.fabric.gui.TgChatOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected EditBox input;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tgbridge$renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TgChatOverlay.INSTANCE.render(guiGraphics, input, width, height, mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void tgbridge$onKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (TgChatOverlay.INSTANCE.onKeyPressed(keyEvent, input, (ChatScreen) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tgbridge$onMouseClicked(MouseButtonEvent mouseEvent, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (TgChatOverlay.INSTANCE.onMouseClicked(mouseEvent, isDoubleClick, input, (ChatScreen) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
