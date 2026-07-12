package com.opchat.mixin;

import com.mojang.authlib.GameProfile;
import com.opchat.ChatBubbleConfig;
import com.opchat.ChatMessageStore;
import net.minecraft.client.MinecraftClient;
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
        UUID senderId = senderProfile.id();
        Text message = signedMessage.getContent();
        String rawStr = message.getString();
        if (rawStr.startsWith("xaero-waypoint:")
            || rawStr.startsWith("xaero_waypoint:")
            || rawStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        Text senderName = Text.literal(senderProfile.name());
        // Check echo: own message bouncing back from server
        var player = MinecraftClient.getInstance().player;
        if (player != null && senderName.getString().equals(player.getName().getString())) {
            if (ChatMessageStore.consumeEchoIfSenderMatches(senderName.getString())) return;
        }
        ChatMessageStore.addMessage(message,
            senderId != null ? senderId : new UUID(0, 0),
            senderName,
            false);
    }

    @Inject(method = "onUnverifiedMessage", at = @At("HEAD"))
    private void onUnverifiedChat(UUID senderId, net.minecraft.network.message.MessageSignatureData signature, MessageType.Parameters params, CallbackInfo ci) {
        // Look up player name from UUID
        var networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null) return;
        String senderName = null;
        for (var info : networkHandler.getPlayerList()) {
            if (info.getProfile().id() != null && info.getProfile().id().equals(senderId)) {
                senderName = info.getProfile().name();
                break;
            }
        }
        if (senderName == null || senderName.isEmpty()) return;

        // We can't easily extract the message text from onUnverifiedMessage params,
        // rely on ChatComponentMixin to capture from ChatHud.addMessage
        // Just set pending meta for now
        ChatMessageStore.setPendingMeta(new ChatMessageStore.SenderMeta(
            senderId != null ? senderId : new UUID(0, 0),
            Text.literal(senderName),
            Text.literal(""), // placeholder, actual text comes from ChatHud.addMessage
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
        Text paramName = params.name();
        boolean hasSender = paramName != null && !paramName.getString().isEmpty();
        if (hasSender) {
            ChatMessageStore.addMessage(message,
                new UUID(0, 0),
                paramName,
                false);
        } else if (tryParsePlayerAngleBracket(message)) {
            // 消息文本本身是 <PlayerName> content 格式，已解析为玩家消息
        } else {
            ChatMessageStore.addMessage(message,
                new UUID(0, 0),
                Text.translatable("opchat.sender.system"),
                true);
        }
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onSystemChat(Text message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;

        // 默认尝试解析 <PlayerName> content 格式的玩家消息
        // 这种格式在未签名聊天、No Chat Reports、代理服务器等场景下很常见
        if (tryParsePlayerAngleBracket(message)) return;

        boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE;
        ChatMessageStore.addMessage(message,
            new UUID(0, 0),
            Text.translatable("opchat.sender.system"),
            isSystem);
    }

    // 解析 "<PlayerName> content" 格式的玩家消息
    // 玩家名只允许字母、数字、下划线，长度 1-16，避免误判真正的系统消息
    private static boolean tryParsePlayerAngleBracket(Text message) {
        String text = message.getString();
        if (!text.startsWith("<")) return false;
        int endBracket = text.indexOf("> ");
        if (endBracket <= 1) return false;
        String extractedName = text.substring(1, endBracket);
        if (!extractedName.matches("[A-Za-z0-9_]{1,16}")) return false;

        // 检查是否是自己发送消息的回显（避免重复显示）
        var player = MinecraftClient.getInstance().player;
        if (player != null && extractedName.equals(player.getName().getString())) {
            if (ChatMessageStore.consumeEchoIfSenderMatches(extractedName)) return true;
        }

        String cleanContent = text.substring(endBracket + 2);
        UUID senderId = ChatMessageStore.lookupPlayerUUID(extractedName);
        ChatMessageStore.addMessage(Text.literal(cleanContent),
            senderId,
            Text.literal(extractedName),
            false);
        return true;
    }
}
