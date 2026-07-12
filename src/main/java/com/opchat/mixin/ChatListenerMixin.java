package com.opchat.mixin;

import com.mojang.authlib.GameProfile;
import com.opchat.ChatBubbleConfig;
import com.opchat.ChatBubbleHudOverlay;
import com.opchat.ChatMessageStore;
import com.opchat.WhisperHistory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
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
        if (tryDetectWhisper(params, message.getString(), senderProfile.name())) return;
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

        // Check for whisper: if targetName is present, defer to ChatComponentMixin
        try {
            Optional<Text> targetOpt = params.targetName();
            if (targetOpt != null && targetOpt.isPresent() && !targetOpt.get().getString().isEmpty()) {
                var player = MinecraftClient.getInstance().player;
                String selfName = player != null ? player.getName().getString() : "";
                String target = targetOpt.get().getString();
                if (senderName.equals(selfName)) {
                    ChatMessageStore.setPendingWhisper(target, selfName, true);
                } else {
                    ChatMessageStore.setPendingWhisper(target, senderName, false);
                }
                return;
            }
        } catch (Throwable ignored) {}

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
        if (tryDetectWhisper(params, msgStr, paramName != null ? paramName.getString() : "")) return;
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

        if (tryCaptureWhisperNotification(message)) return;

        // 默认尝试解析 <PlayerName> content 格式的玩家消息
        // 这种格式在未签名聊天、No Chat Reports、代理服务器等场景下很常见
        if (tryParsePlayerAngleBracket(message)) return;

        boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE;
        ChatMessageStore.addMessage(message,
            new UUID(0, 0),
            Text.translatable("opchat.sender.system"),
            isSystem);
    }

    // 拦截原版 /tell 等命令的私聊通知系统消息
    // 原版通过 onGameMessage 发送，Text 为 TranslatableTextContent
    //   commands.message.display.incoming: args=[senderName, message]  (收到的私聊)
    //   commands.message.display.outgoing: args=[targetName, message]  (发出的私聊)
    private static void onWhisperIncoming(String sender, String content) {
        WhisperHistory.addIncoming(sender, content);
        if (!ChatMessageStore.isScreenOpen()) {
            ChatBubbleHudOverlay.showNotification(sender + ": " + content);
        }
    }

    private static boolean tryCaptureWhisperNotification(Text message) {
        // 优先通过 TranslatableTextContent 提取结构化数据
        try {
            var content = message.getContent();
            if (content instanceof net.minecraft.text.TranslatableTextContent ttc) {
                String key = ttc.getKey();
                Object[] args = ttc.getArgs();
                if (args != null && args.length >= 2) {
                    String name = args[0] instanceof Text t ? t.getString() : String.valueOf(args[0]);
                    String msg = args[1] instanceof Text t ? t.getString() : String.valueOf(args[1]);
                    if ("commands.message.display.incoming".equals(key)) {
                        onWhisperIncoming(name, msg);
                        return true;
                    }
                    if ("commands.message.display.outgoing".equals(key)) {
                        WhisperHistory.addOutgoing(name, msg);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        // 文本兜底：部分服务器发送已格式化纯文本，需解析出发送者/目标和消息内容
        String str = message.getString();
        // zh_cn 收到: "xxx悄悄[地]对你说[：:]message"
        var m = java.util.regex.Pattern.compile("^(.+?)悄悄地?对你说[：:]\\s*(.+)$").matcher(str);
        if (m.matches()) {
            onWhisperIncoming(m.group(1).trim(), m.group(2).trim());
            return true;
        }
        // zh_cn 发出: "你悄悄[地]对xxx说[：:]message"
        m = java.util.regex.Pattern.compile("^你悄悄地?对\\s*(.+?)\\s*说[：:]\\s*(.+)$").matcher(str);
        if (m.matches()) {
            WhisperHistory.addOutgoing(m.group(1).trim(), m.group(2).trim());
            return true;
        }
        // en_us 收到: "xxx whispers to you: message"
        m = java.util.regex.Pattern.compile("^(.+?) whispers to you:\\s*(.+)$").matcher(str);
        if (m.matches()) {
            onWhisperIncoming(m.group(1).trim(), m.group(2).trim());
            return true;
        }
        // en_us 发出: "You whisper to xxx: message"
        m = java.util.regex.Pattern.compile("^You whisper to (.+?):\\s*(.+)$").matcher(str);
        if (m.matches()) {
            WhisperHistory.addOutgoing(m.group(1).trim(), m.group(2).trim());
            return true;
        }
        return false;
    }

    // 检测私聊消息：通过 params.targetName() 判断是否为私聊
    // targetName 非空时，若发送者是自己则为发出的私聊，否则为收到的私聊
    // 返回 true 表示已拦截（不再添加到公共聊天）
    private static boolean tryDetectWhisper(MessageType.Parameters params, String content, String senderName) {
        try {
            Optional<Text> targetOpt = params.targetName();
            if (targetOpt == null || targetOpt.isEmpty()) return false;
            Text target = targetOpt.get();
            if (target.getString().isEmpty()) return false;
            var player = MinecraftClient.getInstance().player;
            String selfName = player != null ? player.getName().getString() : "";
            if (senderName.equals(selfName)) {
                WhisperHistory.addOutgoing(target.getString(), content);
                ChatMessageStore.consumeEchoIfSenderMatches(selfName);
            } else if (!senderName.isEmpty()) {
                onWhisperIncoming(senderName, content);
            } else {
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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
