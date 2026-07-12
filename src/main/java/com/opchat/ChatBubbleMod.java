package com.opchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.opchat.packets.QuoteSyncPacket;

public class ChatBubbleMod implements ModInitializer {
    public static final String MODID = "opchat";

    @Override
    public void onInitialize() {
        NetworkHandler.register();
    }
}
