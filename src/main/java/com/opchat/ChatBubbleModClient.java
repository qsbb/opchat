package com.opchat;

import com.opchat.ime.IMEBlocker;
import com.opchat.mixin.ChatScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.WorldSavePath;

import java.util.Locale;

public class ChatBubbleModClient implements ClientModInitializer {

    private static Screen lastScreen;

    @Override
    public void onInitializeClient() {
        ChatBubbleConfig.load();

        // 游戏启动时若已开启输入法冲突修复，立即禁用游戏内输入法
        if (ChatBubbleConfig.IME_BLOCKER_ENABLED && IMEBlocker.isSupported()) {
            IMEBlocker.disableIME();
        }

        // Intercept vanilla ChatScreen opening
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!ChatBubbleConfig.ENABLED) return;
            // 进入任意屏幕：启用输入法
            handleIMEForScreen(screen);
            if (screen instanceof ChatScreen chatScreen) {
                ChatScreenAccessor accessor = (ChatScreenAccessor) chatScreen;
                String initialText = accessor.opchat$getOriginalChatText();
                accessor.opchat$getChatField().setText("");
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
                key = "SP:" + client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            } else if (client.getCurrentServerEntry() != null) {
                key = "MP:" + client.getCurrentServerEntry().address.trim().toLowerCase(Locale.ROOT);
            } else {
                key = "world";
            }
            ChatMessageStore.setCurrentWorld(key);
            ChatMessageStore.tickPreview();
            ChatMessageStore.tickStrongHint();

            // 屏幕关闭回到游戏内时禁用输入法
            Screen current = client.currentScreen;
            if (lastScreen != null && current == null) {
                handleIMEForScreen(null);
            }
            lastScreen = current;
        });
    }

    /**
     * 根据当前屏幕控制输入法状态：
     *  - 进入文本输入屏幕（聊天框、命令输入、配置屏幕等）时启用输入法
     *  - 进入游戏内（无屏幕）时禁用输入法，避免输入法吞掉 WASD 等按键
     *
     * 仅在用户开启「输入法冲突修复」开关且平台支持时生效。
     */
    private static void handleIMEForScreen(Screen screen) {
        if (!ChatBubbleConfig.IME_BLOCKER_ENABLED || !IMEBlocker.isSupported()) return;
        if (screen == null) {
            // 退出到游戏内，禁用输入法
            IMEBlocker.disableIME();
        } else {
            // 进入任意 GUI 屏幕（通常包含文本输入），启用输入法
            IMEBlocker.enableIME();
        }
    }
}
