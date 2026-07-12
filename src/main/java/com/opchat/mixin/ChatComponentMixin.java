package com.opchat.mixin;

import com.opchat.ChatBubbleConfig;
import com.opchat.ChatMessageStore;
import com.opchat.ChatMessageStore.SenderMeta;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatHud.class, priority = 500)
public class ChatComponentMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRender(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean bl, CallbackInfo ci) {
        if (ChatBubbleConfig.ENABLED) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), require = 0)
    private void onAddMessage(Text message, CallbackInfo ci) {
        capturePendingMessage(message);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), require = 0)
    private void onAddMessageFull(Text message, MessageSignatureData signature,
                                   MessageIndicator tag, CallbackInfo ci) {
        capturePendingMessage(message);
    }

    // Only captures messages that have a pending meta set (e.g. from onUnverifiedMessage).
    // Directly-added messages (signed player chat, system, disguised) skip this.
    private void capturePendingMessage(Text finalComponent) {
        if (!ChatBubbleConfig.ENABLED) return;

        SenderMeta meta = ChatMessageStore.consumePendingMeta(finalComponent.getString());
        if (meta == null) return; // already handled directly by listener mixin

        if (ChatMessageStore.isRecentDuplicate(finalComponent.getString())) return;
        if (ChatMessageStore.consumeEchoIfSenderMatches(meta.senderName().getString())) return;
        if (ChatMessageStore.consumeEchoBySystemChat(finalComponent.getString())) return;

        String rawStr = meta.rawContent().getString();
        String finalStr = finalComponent.getString();
        Text content;
        if (finalStr.contains(rawStr)) {
            content = meta.rawContent();
        } else {
            content = finalComponent;
        }

        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem());
    }
}
