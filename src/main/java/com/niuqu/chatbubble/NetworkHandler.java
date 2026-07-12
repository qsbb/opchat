package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPacket;
import com.niuqu.chatbubble.packets.QuoteSyncPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.ResourceLocation;

public class NetworkHandler {
    public static final ResourceLocation QUOTE_SYNC_ID = ResourceLocation.fromNamespaceAndPath(ChatBubbleMod.MODID, "quote_sync");
    public static final ResourceLocation CHAT_META_ID = ResourceLocation.fromNamespaceAndPath(ChatBubbleMod.MODID, "chat_meta");

    public static void register() {
        // Register C2S (client-to-server) payload types
        PayloadTypeRegistry.playC2S().register(QuoteSyncPacket.TYPE, QuoteSyncPacket.CODEC);
        // Register S2C (server-to-client) payload types
        PayloadTypeRegistry.playS2C().register(ChatMetaPacket.TYPE, ChatMetaPacket.CODEC);

        // Register server-side receiver
        QuoteSyncPacket.registerReceiver();
        // Register client-side receiver
        ChatMetaPacket.registerReceiver();
    }
}
