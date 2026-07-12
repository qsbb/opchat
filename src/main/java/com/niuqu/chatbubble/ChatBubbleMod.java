package com.niuqu.chatbubble;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.niuqu.chatbubble.packets.QuoteSyncPacket;

public class ChatBubbleMod implements ModInitializer {
    public static final String MODID = "e33chat";

    @Override
    public void onInitialize() {
        NetworkHandler.register();
    }
}
