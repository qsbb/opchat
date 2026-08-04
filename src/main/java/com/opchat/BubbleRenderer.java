package com.opchat;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BubbleRenderer {
    private final ChatBubbleScreen screen;
    private final TextRenderer textRenderer;

    private final List<int[]> bubbleRects = new ArrayList<>();
    private final List<ChatBubbleScreen.ClickableSpan> clickableSpans = new ArrayList<>();

    public BubbleRenderer(ChatBubbleScreen screen, TextRenderer textRenderer) {
        this.screen = screen;
        this.textRenderer = textRenderer;
    }

    public void clear() {
        bubbleRects.clear();
        clickableSpans.clear();
    }

    public List<int[]> getBubbleRects() { return bubbleRects; }
    public List<ChatBubbleScreen.ClickableSpan> getClickableSpans() { return clickableSpans; }

    public Style getHoveredStyle(double mouseX, double mouseY) {
        for (ChatBubbleScreen.ClickableSpan s : clickableSpans) {
            if (mouseX >= s.x && mouseX <= s.x + s.w
                && mouseY >= s.y && mouseY <= s.y + s.h)
                return s.style;
        }
        return null;
    }

    public void renderTimeSeparator(DrawContext context, LocalDateTime dateTime, int y) {
        LocalDate today = java.time.LocalDate.now();
        String text = dateTime.toLocalDate().equals(today)
            ? dateTime.format(ChatBubbleScreen.TIME_FMT)
            : dateTime.format(ChatBubbleScreen.DATE_TIME_FMT);
        int tw = textRenderer.getWidth(text);
        int tx = screen.getPanelX() + (screen.getPanelW() - tw) / 2;
        context.fill(tx - 6, y + 2, tx + tw + 6, y + ChatBubbleScreen.TIME_SEP_H - 2, 0x44000000);
        context.drawText(textRenderer, Text.literal(text), tx, y + 3, ChatBubbleScreen.COLOR_TIME, false);
    }

    public int getMsgHeight(ChatMessageStore.ChatMessage msg) {
        if (msg.isSystem()) {
            List<OrderedText> lines = textRenderer.wrapLines(msg.content(), screen.getPanelW() - ChatBubbleScreen.PAD * 2 - 20);
            return lines.size() * textRenderer.fontHeight + 4;
        }
        int bubbleMaxW = screen.getPanelW() - ChatBubbleScreen.AVATAR - ChatBubbleScreen.PAD * 2 - ChatBubbleScreen.BUBBLE_PAD_X * 2 - 16;
        List<OrderedText> lines = textRenderer.wrapLines(msg.content(), bubbleMaxW);
        int h = lines.size() * textRenderer.fontHeight + ChatBubbleScreen.BUBBLE_PAD_Y * 2 + ChatBubbleScreen.NAME_H;
        if (msg.replyContent() != null) h += textRenderer.fontHeight + 2;
        return h;
    }

    public void renderBubble(DrawContext context, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY) {
        if (msg.isSystem()) {
            List<OrderedText> lines = textRenderer.wrapLines(msg.content(), screen.getPanelW() - ChatBubbleScreen.PAD * 2 - 20);
            int yy = baseY + 2;
            for (var line : lines) {
                int lw = textRenderer.getWidth(line);
                renderLineWithClicks(context, line, screen.getPanelX() + (screen.getPanelW() - lw) / 2, yy, 0xFF888888);
                yy += textRenderer.fontHeight;
            }
            return;
        }

        boolean own = msg.isOwn();
        int bubbleMaxW = screen.getPanelW() - ChatBubbleScreen.AVATAR - ChatBubbleScreen.PAD * 2 - ChatBubbleScreen.BUBBLE_PAD_X * 2 - 16;
        List<OrderedText> lines = textRenderer.wrapLines(msg.content(), bubbleMaxW);

        int textW = 0;
        for (var line : lines) textW = Math.max(textW, textRenderer.getWidth(line));
        int bubbleW = Math.max(textW + ChatBubbleScreen.BUBBLE_PAD_X * 2, 36);
        int bubbleH = lines.size() * textRenderer.fontHeight + ChatBubbleScreen.BUBBLE_PAD_Y * 2;

        int avatarX, bubbleX;
        if (own) {
            avatarX = screen.getPanelX() + screen.getPanelW() - ChatBubbleScreen.PAD - ChatBubbleScreen.AVATAR;
            bubbleX = avatarX - 4 - bubbleW;
        } else {
            avatarX = screen.getPanelX() + ChatBubbleScreen.PAD;
            bubbleX = avatarX + ChatBubbleScreen.AVATAR + 4;
        }

        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = screen.getPanelW() - ChatBubbleScreen.AVATAR - ChatBubbleScreen.PAD * 2 - 20;
            String rawName = msg.senderName().getString();
            Text displayName;
            if (!own) {
                String nick = WhisperHistory.getNickname(rawName);
                displayName = (nick != null && !nick.isEmpty()) ? Text.literal(nick) : msg.senderName();
            } else {
                displayName = msg.senderName();
            }
            if (textRenderer.getWidth(displayName) > maxNameW)
                displayName = Text.literal(textRenderer.trimToWidth(displayName.getString(), maxNameW - textRenderer.getWidth("...")) + "...");
            int nameW = textRenderer.getWidth(displayName);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            context.drawText(textRenderer, displayName, startX, nameY, ChatBubbleScreen.COLOR_NAME, false);
        }

        int bubbleY = baseY + ChatBubbleScreen.NAME_H;
        int avatarY = bubbleY - 6;

        int bg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR, 0xFF95EC69)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR, 0xFF4A4A4A);
        int fg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR, 0xFF0A0A0A)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR, 0xFFFFFFFF);

        context.fill(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH, bg);

        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(context, lines.get(li), bubbleX + ChatBubbleScreen.BUBBLE_PAD_X,
                bubbleY + ChatBubbleScreen.BUBBLE_PAD_Y + li * textRenderer.fontHeight, fg);

        // Reply preview (below the bubble)
        if (msg.replyContent() != null) {
            int replyY = bubbleY + bubbleH + 2;
            int replyH = textRenderer.fontHeight;
            int replyMaxW = bubbleMaxW;
            String replyText = msg.replySender() + ": " + msg.replyContent();
            String replyDisplay = textRenderer.trimToWidth(replyText, replyMaxW - textRenderer.getWidth("..."));
            if (!replyDisplay.equals(replyText)) replyDisplay += "...";
            int replyDisplayW = textRenderer.getWidth(replyDisplay);
            int replyBarX = own ? (bubbleX + bubbleW - replyDisplayW) : bubbleX;
            int accentColor = own
                ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR, 0xFF0A0A0A)
                : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR, 0xFFFFFFFF);
            context.fill(replyBarX, replyY, replyBarX + 2, replyY + replyH, accentColor);
            context.drawText(textRenderer, Text.literal(replyDisplay), replyBarX + 6, replyY + 1, ChatBubbleScreen.COLOR_TIME, false);
        }

        // Draw player skin
        AvatarHelper.renderSkin(context, msg.senderUUID(), avatarX, avatarY);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = textRenderer.getWidth(label);
            int labelX = bubbleX + bubbleW - labelW - 3;
            int labelY = bubbleY + bubbleH - textRenderer.fontHeight / 2 - 1;
            context.drawText(textRenderer, Text.literal(label), labelX, labelY, 0x99AAAAAA, false);
        }

        int bubbleRight = bubbleX + bubbleW;
        int bubbleBottom = bubbleY + bubbleH;
        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});
    }

    public void renderLineWithClicks(DrawContext context, OrderedText line,
                                       int x, int y, int color) {
        context.drawText(textRenderer, line, x, y, color, false);

        final int[] pos = {0};
        final int[] spanStart = {-1};
        final Style[] spanStyle = {null};

        line.accept((index, style, codePoint) -> {
            int charW = textRenderer.getWidth(String.valueOf((char) codePoint));
            if (style.getClickEvent() != null) {
                if (spanStart[0] < 0) {
                    spanStart[0] = pos[0]; spanStyle[0] = style;
                } else if (!style.equals(spanStyle[0])) {
                    clickableSpans.add(new ChatBubbleScreen.ClickableSpan(x + spanStart[0], y,
                        pos[0] - spanStart[0], textRenderer.fontHeight, spanStyle[0]));
                    spanStart[0] = pos[0]; spanStyle[0] = style;
                }
            } else {
                if (spanStart[0] >= 0) {
                    clickableSpans.add(new ChatBubbleScreen.ClickableSpan(x + spanStart[0], y,
                        pos[0] - spanStart[0], textRenderer.fontHeight, spanStyle[0]));
                    spanStart[0] = -1; spanStyle[0] = null;
                }
            }
            pos[0] += charW;
            return true;
        });
        if (spanStart[0] >= 0) {
            clickableSpans.add(new ChatBubbleScreen.ClickableSpan(x + spanStart[0], y,
                pos[0] - spanStart[0], textRenderer.fontHeight, spanStyle[0]));
        }
    }
}
