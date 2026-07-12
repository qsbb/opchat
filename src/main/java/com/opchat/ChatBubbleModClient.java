package com.opchat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

public class ChatBubbleModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChatBubbleConfig.load();

        // Intercept vanilla ChatScreen opening
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!ChatBubbleConfig.ENABLED) return;
            if (screen instanceof ChatScreen chatScreen) {
                String initialText = "";
                try {
                    var field = ChatScreen.class.getDeclaredField("originalChatText");
                    field.setAccessible(true);
                    initialText = (String) field.get(chatScreen);
                } catch (Exception e) {
                    // fallback: empty
                }
                client.setScreen(new ChatBubbleScreen(initialText != null ? initialText : ""));
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
            if (client.world == null || client.player == null) {
                key = null;
            } else if (client.getServer() != null) {
                key = "SP:" + client.getServer().getSaveProperties().getLevelName();
            } else if (client.getCurrentServerEntry() != null) {
                key = "MP:" + client.getCurrentServerEntry().name;
            } else {
                key = "world";
            }
            ChatMessageStore.setCurrentWorld(key);
            ChatMessageStore.tickPreview();
            ChatMessageStore.tickStrongHint();
        });
    }
}
