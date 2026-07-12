package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import java.util.UUID;
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

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (ChatBubbleConfig.ENABLED) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        captureMessage(message);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"))
    private void onAddMessageFull(Text message, MessageSignatureData signature,
                                   MessageIndicator tag, CallbackInfo ci) {
        captureMessage(message);
    }

    private void captureMessage(Text finalComponent) {
        if (!ChatBubbleConfig.ENABLED) return;

        SenderMeta meta = ChatMessageStore.consumePendingMeta();
        if (meta == null) {
            if (ChatMessageStore.isRecentDuplicate(finalComponent.getString())) return;
            meta = new SenderMeta(
                new UUID(0, 0),
                Text.translatable("e33chat.sender.system"),
                finalComponent,
                true
            );
        }

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
