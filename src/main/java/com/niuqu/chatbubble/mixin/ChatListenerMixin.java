package com.niuqu.chatbubble.mixin;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = MessageHandler.class, priority = 500)
public class ChatListenerMixin {

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void onPlayerChat(SignedMessage signedMessage, GameProfile senderProfile, MessageType.Parameters params, CallbackInfo ci) {
        UUID senderId = senderProfile.getId();
        Text message = signedMessage.getContent();
        String rawStr = message.getString();
        if (rawStr.startsWith("xaero-waypoint:")
            || rawStr.startsWith("xaero_waypoint:")
            || rawStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        ChatMessageStore.setPendingMeta(new SenderMeta(
            senderId != null ? senderId : new UUID(0, 0),
            Text.literal(senderProfile.getName()),
            message,
            false
        ));
    }

    @Inject(method = "onProfilelessMessage", at = @At("HEAD"))
    private void onDisguisedChat(Text message, MessageType.Parameters params, CallbackInfo ci) {
        String msgStr = message.getString();
        if (msgStr.startsWith("xaero-waypoint:")
            || msgStr.startsWith("xaero_waypoint:")
            || msgStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        Text senderName = params.applyChatDecoration(Text.empty()).getString().isEmpty() 
            ? null : params.name();
        boolean hasSender = senderName != null;
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0),
            hasSender ? senderName : Text.translatable("e33chat.sender.system"),
            message,
            !hasSender
        ));
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onSystemChat(Text message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;

        if (ChatBubbleConfig.CHAT_REPORT_COMPAT) {
            String text = message.getString();
            if (text.startsWith("<") && text.contains("> ")) {
                int endBracket = text.indexOf("> ");
                String extractedName = text.substring(1, endBracket);
                String cleanContent = text.substring(endBracket + 2);
                UUID senderId = ChatMessageStore.lookupPlayerUUID(extractedName);
                ChatMessageStore.setPendingMeta(new SenderMeta(
                    senderId,
                    Text.literal(extractedName),
                    Text.literal(cleanContent),
                    false
                ));
                return;
            }
            boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE;
            ChatMessageStore.setPendingMeta(new SenderMeta(
                new UUID(0, 0),
                Text.translatable("e33chat.sender.system"),
                message,
                isSystem
            ));
            return;
        }

        boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE;
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0),
            Text.translatable("e33chat.sender.system"),
            message,
            isSystem
        ));
    }
}
