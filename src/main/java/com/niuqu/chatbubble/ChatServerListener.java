package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatServerListener {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    // player UUID -> pending quote data
    private static final Map<UUID, QuotePending> pendingQuotes = new HashMap<>();

    private record QuotePending(String quotedSenderName, String quotedContent, String messageHash) {}

    public static void onQuoteReceived(UUID senderUUID, String quotedSenderName,
                                        String quotedContent, String messageHash) {
        pendingQuotes.put(senderUUID, new QuotePending(quotedSenderName, quotedContent, messageHash));
    }

    public static void onServerChat(ServerPlayerEntity player, String rawText) {
        String messageHash = String.valueOf(rawText.hashCode());

        List<String> mentions = extractMentions(rawText, player.getServer().getPlayerManager().getPlayerList().size());

        QuotePending quote = pendingQuotes.remove(player.getUuid());
        String quoteSender = quote != null ? quote.quotedSenderName() : "";
        String quoteContent = quote != null ? quote.quotedContent() : "";

        if (quote != null || !mentions.isEmpty()) {
            ChatMetaPacket meta = new ChatMetaPacket(
                player.getUuid(), messageHash, quoteSender, quoteContent, mentions);
            // Send to all players
            for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(target, meta);
            }
        }
    }

    private static List<String> extractMentions(String text, int playerCount) {
        if (playerCount <= 1) return Collections.emptyList();
        List<String> mentions = new ArrayList<>();
        Matcher m = MENTION_PATTERN.matcher(text);
        while (m.find()) {
            mentions.add(m.group(1));
        }
        return mentions;
    }
}
