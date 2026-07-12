package com.niuqu.chatbubble;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

public class ChatBubbleModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChatBubbleConfig.load();

        // Intercept vanilla ChatScreen opening
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!ChatBubbleConfig.ENABLED) return;
            if (screen instanceof ChatScreen chatScreen) {
                ScreenEvents.remove(screen).register(removed -> {});
                // Replace with our screen
                client.setScreen(new ChatBubbleScreen(""));
            }
        });

        // HUD overlay rendering
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            if (!ChatBubbleConfig.ENABLED) return;
            ChatBubbleHudOverlay.render(drawContext);
        });

        // Client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            String key;
            if (client.level == null || client.player == null) {
                key = null;
            } else if (client.getSingleplayerServer() != null) {
                key = "SP:" + client.getSingleplayerServer().getWorldData().getLevelName();
            } else if (client.getCurrentServer() != null) {
                key = "MP:" + client.getCurrentServer().name;
            } else {
                key = "world";
            }
            ChatMessageStore.setCurrentWorld(key);
            ChatMessageStore.tickPreview();
            ChatMessageStore.tickStrongHint();
        });
    }
}
