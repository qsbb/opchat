package com.opchat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    private static final int RED_DOT_R = 4;
    public static final Identifier TEX_CHAT_ICON =
        Identifier.of("opchat", "textures/gui/chat_icon");
    public static boolean iconLoaded;

    private static boolean notificationMode = false;
    private static long notificationStartTime = 0;
    private static final java.util.List<String> notificationLines = new java.util.ArrayList<>();
    private static final int NOTIFICATION_DURATION_MS = 30000;
    private static final int NOTIFICATION_EDGE_PAD = 8;
    private static final int NOTIFICATION_INNER_PAD = 6;
    private static final int NOTIFICATION_MAX_LINES = 3;

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen != null) return;

        int screenH = mc.getWindow().getScaledHeight();

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().translate(0, 0, 300);

        // Strong hint above hotbar
        if (ChatBubbleConfig.STRONG_HINT_ENABLED) {
            String hint = ChatMessageStore.getStrongHintText();
            if (hint != null) {
                int ticks = ChatMessageStore.getStrongHintTicks();
                int screenW = mc.getWindow().getScaledWidth();
                int hintW = mc.textRenderer.getWidth(hint);
                int hintX = (screenW - hintW) / 2;
                int hintY = screenH - 22 - 30 - mc.textRenderer.fontHeight;
                int alpha;
                if (ticks > 50)
                    alpha = (ChatMessageStore.STRONG_HINT_DURATION - ticks) * 0xFF / 10;
                else if (ticks > 10)
                    alpha = 0xFF;
                else
                    alpha = ticks * 0xFF / 10;
                alpha = Math.min(alpha, 0xFF);
                int bgAlpha = alpha / 2;
                int bgColor = (bgAlpha << 24) | 0x000000;
                int baseColor = ChatMessageStore.isStrongHintMention() ? 0xFFFF55 : 0xFFFFFF;
                int textColor = (alpha << 24) | baseColor;
                context.fill(hintX - 6, hintY - 3, hintX + hintW + 6, hintY + mc.textRenderer.fontHeight + 3, bgColor);
                context.drawText(mc.textRenderer, hint, hintX, hintY, textColor, false);
            }
        }

        if (isNotificationActive()) {
            drawNotificationWindow(context, mc, screenH);
        } else {
            String keyName = mc.options.chatKey.getBoundKeyLocalizedText().getString();
            int x = 3;
            int iconY = screenH - ICON_S - 20;
            int textY = iconY + ICON_S + 1;

            // Message preview above icon (multi-line)
            if (ChatBubbleConfig.PREVIEW_ENABLED) {
                List<ChatMessageStore.PreviewEntry> previews = ChatMessageStore.getPreviews();
                if (previews != null && !previews.isEmpty()) {
                    int maxW = ChatBubbleConfig.PREVIEW_WIDTH;
                    int lineH = mc.textRenderer.fontHeight;
                    int gap = 2;

                    List<String> displays = new ArrayList<>();
                    int maxTextW = 0;
                    for (var e : previews) {
                        String d = mc.textRenderer.trimToWidth(e.text, maxW - 4);
                        if (!d.equals(e.text)) d += "...";
                        displays.add(d);
                        maxTextW = Math.max(maxTextW, mc.textRenderer.getWidth(d));
                    }

                    int px = x + ICON_S / 2 - maxTextW / 2;
                    if (px < 2) px = 2;
                    int bgX1 = px - 3;
                    if (bgX1 < 0) bgX1 = 0;

                    int bottomLineY = iconY - 5 - lineH;
                    int topLineY = bottomLineY - (displays.size() - 1) * (lineH + gap);
                    int newestTicks = previews.get(previews.size() - 1).ticks;
                    int newestAlpha = newestTicks > 10 ? 0xDD : (newestTicks * 0xDD / 10);
                    int bgAlpha = newestAlpha / 2;
                    int bgColor = (bgAlpha << 24) | 0x000000;
                    context.fill(bgX1, topLineY - 2, px + maxTextW + 3, bottomLineY + lineH + 2, bgColor);
                    for (int i = displays.size() - 1; i >= 0; i--) {
                        int lineY = bottomLineY - (displays.size() - 1 - i) * (lineH + gap);
                        context.drawText(mc.textRenderer, displays.get(i), px, lineY, 0xFFFFFFFF, false);
                    }
                }
            }

            // Chat bubble icon
            if (!iconLoaded) {
                loadIconTexture();
                iconLoaded = true;
            }
            drawIcon(context, x, iconY);

            // Red dot
            if (ChatBubbleConfig.RED_DOT_ENABLED && ChatMessageStore.getUnreadCount() > 0) {
                int dotX = x + ICON_S - RED_DOT_R;
                int dotY = iconY + RED_DOT_R;
                int dotColor = ChatMessageStore.hasUnreadMention(mc.player.getName().getString())
                    ? 0xFFFF4444 : 0xFFFF0000;
                context.fill(dotX - RED_DOT_R, dotY - RED_DOT_R, dotX + RED_DOT_R, dotY + RED_DOT_R, dotColor);
            }

            // Keybind text below icon
            String keyDisplay = "[" + keyName + "]";
            int keyW = mc.textRenderer.getWidth(keyDisplay);
            int keyX = keyW > ICON_S ? x : x + (ICON_S - keyW) / 2;
            context.drawText(mc.textRenderer, keyDisplay, keyX, textY, 0xFFFFFFFF, false);
        }

        RenderSystem.getModelViewStack().popMatrix();
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (isNotificationActive()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return false;
        int screenH = mc.getWindow().getScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.textRenderer.fontHeight + 2;
    }

    public static void showNotification(String text) {
        notificationMode = true;
        notificationStartTime = net.minecraft.util.Util.getMeasuringTimeMs();
        notificationLines.add(text);
        while (notificationLines.size() > NOTIFICATION_MAX_LINES)
            notificationLines.remove(0);
    }

    public static void dismissNotification() {
        notificationMode = false;
        notificationLines.clear();
    }

    private static boolean isNotificationActive() {
        if (!notificationMode) return false;
        long elapsed = net.minecraft.util.Util.getMeasuringTimeMs() - notificationStartTime;
        if (elapsed >= NOTIFICATION_DURATION_MS) {
            notificationMode = false;
            notificationLines.clear();
            return false;
        }
        return true;
    }

    private static void drawNotificationWindow(DrawContext context, MinecraftClient mc, int screenH) {
        if (notificationLines.isEmpty()) return;

        int maxTextW = ChatBubbleConfig.PREVIEW_WIDTH - NOTIFICATION_INNER_PAD * 2;
        int lineH = mc.textRenderer.fontHeight;
        int gap = 2;

        java.util.List<String> displays = new java.util.ArrayList<>();
        int actualMaxW = 0;
        for (String line : notificationLines) {
            String d = mc.textRenderer.trimToWidth(line, maxTextW - 3);
            if (!d.equals(line)) d += "...";
            displays.add(d);
            actualMaxW = Math.max(actualMaxW, mc.textRenderer.getWidth(d));
        }

        int windowW = actualMaxW + NOTIFICATION_INNER_PAD * 2;
        int windowH = displays.size() * lineH + (displays.size() - 1) * gap + NOTIFICATION_INNER_PAD * 2;
        int x = NOTIFICATION_EDGE_PAD;
        int y = screenH - NOTIFICATION_EDGE_PAD - windowH;

        context.fill(x, y, x + windowW, y + windowH, 0xDD1E1E1E);

        for (int i = 0; i < displays.size(); i++) {
            context.drawText(mc.textRenderer, displays.get(i),
                x + NOTIFICATION_INNER_PAD,
                y + NOTIFICATION_INNER_PAD + i * (lineH + gap),
                0xFFFFFFFF, false);
        }
    }

    private static void loadIconTexture() {
        try (InputStream in = ChatBubbleHudOverlay.class.getClassLoader()
                .getResourceAsStream("assets/opchat/textures/gui/chat_icon.png")) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "chat_icon", img);
                MinecraftClient.getInstance().getTextureManager().registerTexture(TEX_CHAT_ICON, tex);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void drawIcon(DrawContext context, int x, int y) {
        var mc = MinecraftClient.getInstance();
        try {
            mc.getTextureManager().getTexture(TEX_CHAT_ICON);
        } catch (Exception e) {
            loadIconTexture();
        }
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_CHAT_ICON, x, y, 0.0f, 0.0f, ICON_S, ICON_S, ICON_S, ICON_S);
    }
}
