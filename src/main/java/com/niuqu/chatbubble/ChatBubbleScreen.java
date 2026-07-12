package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.AbstractTexture;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.niuqu.chatbubble.packets.QuoteSyncPacket;

import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatBubbleScreen extends Screen {

    // Layout
    private int panelX, panelW;
    private static final int TITLE_H = 24;
    private int titleY, msgTop, msgBottom, barTop;
    private static final int PAD = 10;
    private static final int AVATAR = 20;
    private static final int BUBBLE_PAD_X = 8;
    private static final int BUBBLE_PAD_Y = 5;
    private static final int GAP = 6;
    private static final int NAME_H = 10;
    private static final int TIME_SEP_H = 14;
    private static final int BAR_H = 38;

    private static final int ICON_S = 16;
    private static final Identifier TEX_GEAR = Identifier.of("e33chat", "textures/gui/settings");
    private static final Identifier TEX_SEND = Identifier.of("e33chat", "textures/gui/send");
    private static boolean iconsLoaded;

    private static final int COLOR_NAME = 0xFFCCCCCC;
    private static final int COLOR_TIME = 0xFF999999;
    private static final int COLOR_PANEL_BG = 0xEE1E1E1E;
    private static final int COLOR_TITLE_BG = 0xFF242424;
    private static final int COLOR_BAR_BG = 0xFF242424;
    private static final int COLOR_DIVIDER = 0xFF333333;
    private static final int COLOR_INPUT_BG = 0xFF2A2A2A;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private TextFieldWidget input;
    private ChatInputSuggestor commandSuggestions;
    private static int inputY;
    private final String initialText;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private String historyBuffer = "";
    private int historyPos = -1;
    private String worldName;
    private boolean editingTitle;
    private TextFieldWidget titleEditor;

    // Right-click menu
    private int contextMsgIndex = -1;
    private int contextX, contextY;
    private static final int CTX_W = 80;
    private static final int CTX_ITEM_H = 18;

    // Bubble hit tracking
    private final List<int[]> bubbleRects = new ArrayList<>();

    // Clickable text span tracking
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

    // Reply / quote
    private int replyTargetIndex = -1;

    // Copy toast
    private int copyToastTicks;

    // Animations
    private long animStart;
    private boolean closing;
    private static final int ANIM_MS = 150;
    private static final int NOTIF_H = 14;
    private int newMessageCount;
    private boolean hasNewMentionOrQuote;
    private int latestMentionIndex = -1;
    private int lastSeenMessageCount;
    private int notifCountLeft, notifCountRight;
    private int notifMentionLeft = -1, notifMentionRight = -1;
    private int notifBarTextY;

    public ChatBubbleScreen(String initialText) {
        super(Text.translatable("e33chat.screen.title"));
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        historyPos = client.inGameHud.getChatHud().getMessageHistory().size();
        ChatMessageStore.setScreenOpen(true);
        animStart = net.minecraft.util.Util.getMeasuringTimeMs();
        closing = false;

        panelW = Math.max(200, (int) (width * 0.4));
        panelX = 0;
        titleY = 4;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = barTop - 1;

        int ibY = barTop + (BAR_H - 20) / 2;
        inputY = ibY;
        int inputX = panelX + PAD + ICON_S + 8;
        int inputW = panelX + panelW - PAD - ICON_S - 8 - inputX;

        input = new TextFieldWidget(textRenderer, inputX, ibY, inputW, 20, Text.literal(""));
        input.setMaxLength(256);
        input.setDrawsBackground(false);
        input.setText(initialText);
        input.setFocused(true);
        input.setEditable(true);
        input.setChangedListener(this::onEdited);
        addDrawableChild(input);

        commandSuggestions = new ChatInputSuggestor(client, this, input, textRenderer,
            false, false, 0, 8, true, 0xDD1E1E1E);
        commandSuggestions.refresh();

        if (!iconsLoaded) {
            loadIconTextures();
            iconsLoaded = true;
        }

        worldName = getWorldName();

        int editW = Math.min(180, panelW - 80);
        int editX = panelX + (panelW - editW) / 2;
        int editY = titleY + (TITLE_H - 20) / 2;
        titleEditor = new TextFieldWidget(textRenderer, editX, editY, editW, 20, Text.literal(""));
        titleEditor.setMaxLength(32);
        titleEditor.setDrawsBackground(false);
        titleEditor.setVisible(false);
        addDrawableChild(titleEditor);

        setInitialFocus(input);
    }

    private String getWorldName() {
        if (client.getServer() != null)
            return client.getServer().getSaveProperties().getLevelName();
        if (client.getCurrentServerEntry() != null)
            return client.getCurrentServerEntry().name;
        return Text.translatable("e33chat.title.fallback").getString();
    }

    private void onEdited(String text) {
        if (commandSuggestions != null) {
            commandSuggestions.setWindowActive(!text.equals(initialText));
            commandSuggestions.refresh();
        }
    }

    @Override
    public void tick() {
        if (copyToastTicks > 0) copyToastTicks--;
        if (closing && net.minecraft.util.Util.getMeasuringTimeMs() - animStart >= ANIM_MS)
            client.setScreen(null);
    }

    private float getAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED) return 1.0f;
        long elapsed = net.minecraft.util.Util.getMeasuringTimeMs() - animStart;
        float p = (float) elapsed / ANIM_MS;
        if (closing) p = 1.0f - p;
        return MathHelper.clamp(p, 0f, 1f);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingTitle) {
            if (keyCode == 256) { exitTitleEdit(false); return true; }
            if (keyCode == 257 || keyCode == 335) { exitTitleEdit(true); return true; }
            return titleEditor.keyPressed(keyCode, scanCode, modifiers);
        }
        if (commandSuggestions != null && commandSuggestions.keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (keyCode == 256) { close(); return true; }
        if (keyCode == 257 || keyCode == 335) {
            if (commandSuggestions != null) commandSuggestions.clearWindow();
            sendMessage();
            return true;
        }
        if (keyCode == 265) { moveInHistory(-1); return true; }
        if (keyCode == 264) { moveInHistory(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(verticalAmount))
            return true;
        scrollToBottom = false;
        scrollOffset -= (int) (verticalAmount * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Context menu clicks
        if (button == 0 && contextMsgIndex >= 0) {
            handleContextClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (contextMsgIndex >= 0) { contextMsgIndex = -1; return true; }

        // Notification bar clicks
        if (button == 0 && newMessageCount > 0) {
            if (mouseX >= notifCountLeft && mouseX <= notifCountRight
                && mouseY >= notifBarTextY && mouseY <= notifBarTextY + textRenderer.fontHeight) {
                scrollToBottom = true;
                newMessageCount = 0;
                hasNewMentionOrQuote = false;
                latestMentionIndex = -1;
                lastSeenMessageCount = ChatMessageStore.getMessages().size();
                return true;
            }
            if (hasNewMentionOrQuote && notifMentionLeft >= 0
                && mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= notifBarTextY && mouseY <= notifBarTextY + textRenderer.fontHeight) {
                jumpToMessage(latestMentionIndex);
                return true;
            }
        }

        // Reply bar cancel
        if (button == 0 && replyTargetIndex >= 0 && isMouseOverReplyCancel(mouseX, mouseY)) {
            replyTargetIndex = -1;
            return true;
        }

        if (commandSuggestions != null && commandSuggestions.mouseClicked((int) mouseX, (int) mouseY, button))
            return true;

        if (button == 0) {
            if (editingTitle) {
                if (!isMouseOverTitleEditor(mouseX, mouseY)) {
                    exitTitleEdit(true);
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (isMouseOverPen(mouseX, mouseY)) {
                enterTitleEdit();
                return true;
            }
            if (mouseX >= panelX + panelW - 18 && mouseX <= panelX + panelW - 6
                && mouseY >= titleY + 6 && mouseY <= titleY + 18) {
                close();
                return true;
            }
            if (mouseY >= barTop) {
                if (handleIconClick((int) mouseX, (int) mouseY))
                    return true;
            }
        }

        if (button == 0) {
            for (int[] r : bubbleRects) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(r[4]);
                if (msg == null || msg.isSystem()) continue;
                int avatarX = r[0] - AVATAR - 4;
                int avatarY = r[1] - 6;
                if (mouseX >= avatarX && mouseX <= avatarX + AVATAR
                    && mouseY >= avatarY && mouseY <= avatarY + AVATAR) {
                    String mention = "@" + msg.senderName().getString() + " ";
                    input.setText(input.getText() + mention);
                    input.setCursorToEnd(false);
                    return true;
                }
            }
        }

        if (button == 1) {
            for (int[] r : bubbleRects) {
                if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    contextMsgIndex = r[4];
                    contextX = (int) mouseX;
                    contextY = (int) mouseY;
                    return true;
                }
            }
        }
        if (button == 0) {
            Style style = getHoveredStyle(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                ClickEvent click = style.getClickEvent();
                if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                    input.setText(click.getValue());
                    return true;
                }
                handleTextClick(style);
                if (click.getAction() != ClickEvent.Action.COPY_TO_CLIPBOARD
                    && click.getAction() != ClickEvent.Action.OPEN_URL) {
                    client.setScreen(null);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        int gearX = panelX + PAD;
        if (mx >= gearX && mx <= gearX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            client.setScreen(new ChatBubbleConfigScreen(this));
            return true;
        }
        int sendX = panelX + panelW - PAD - ICON_S;
        if (mx >= sendX && mx <= sendX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            sendMessage();
            return true;
        }
        return false;
    }

    private void handleContextClick(int mx, int my) {
        int menuH = CTX_ITEM_H * 2 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        if (mx >= menuX && mx <= menuX + CTX_W) {
            if (my >= menuY && my <= menuY + CTX_ITEM_H) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextMsgIndex);
                if (msg != null) {
                    client.keyboard.setClipboard(msg.content().getString());
                    copyToastTicks = 20;
                }
            } else if (my >= menuY + CTX_ITEM_H + 1 && my <= menuY + CTX_ITEM_H * 2 + 1) {
                replyTargetIndex = contextMsgIndex;
            }
        }
        contextMsgIndex = -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float anim = getAnimProgress();
        int panelOffset = (int) ((anim - 1.0f) * panelW);

        // Dark overlay to the right of panel
        int overlayAlpha = (int) (0.94f * anim * 160) << 24;
        if (overlayAlpha != 0)
            context.fill(panelX + panelW + panelOffset, 0, width, height, overlayAlpha | 0x000000);

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().translate(panelOffset, 0, 0);

        context.fill(panelX, 0, panelX + panelW, height, COLOR_PANEL_BG);

        renderTitleBar(context, mouseX, mouseY);
        renderMessages(context, mouseX, mouseY);
        Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            // Hover event rendering would go here
        }
        renderNotificationBar(context, mouseX, mouseY);
        renderReplyBar(context, mouseX, mouseY);
        renderContextMenu(context, mouseX, mouseY);
        renderToast(context);
        renderBottomBar(context, mouseX, mouseY);

        context.enableScissor(panelX, 0, panelX + panelW, height);
        if (commandSuggestions != null) commandSuggestions.render(context, mouseX, mouseY);
        context.disableScissor();

        RenderSystem.getModelViewStack().popMatrix();

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderTitleBar(DrawContext context, int mouseX, int mouseY) {
        int ty = titleY;
        context.fill(panelX, ty, panelX + panelW, ty + TITLE_H, COLOR_TITLE_BG);
        context.fill(panelX, ty + TITLE_H, panelX + panelW, ty + TITLE_H + 1, COLOR_DIVIDER);

        if (editingTitle) {
            // titleEditor rendered via super
        } else {
            String title = getDisplayTitle();
            int titleW = textRenderer.getWidth(title);
            int titleX = panelX + (panelW - titleW) / 2;
            int titleTextY = ty + (TITLE_H - textRenderer.fontHeight) / 2;
            context.drawText(textRenderer, Text.literal(title), titleX, titleTextY, 0xFFFFFFFF, false);

            int penX = titleX + titleW + 3;
            int penY = ty + (TITLE_H - 9) / 2;
            boolean hoverPen = mouseX >= penX && mouseX <= penX + 9 && mouseY >= penY && mouseY <= penY + 9;
            int penColor = hoverPen ? 0xFFFFFF88 : 0xFF888888;
            context.drawText(textRenderer, Text.literal("\u270E"), penX, penY, penColor, false);
        }

        String time = LocalTime.now().format(TIME_FMT);
        int timeW = textRenderer.getWidth(time);
        context.drawText(textRenderer, Text.literal(time),
            panelX + panelW - PAD - 20 - timeW, ty + (TITLE_H - textRenderer.fontHeight) / 2, COLOR_TIME, false);

        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        int closeBg = hoverClose ? 0xFF555555 : 0xFF333333;
        context.fill(closeX, closeY, closeX + 12, closeY + 12, closeBg);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"), closeX + 6, closeY + 2, 0xFFCCCCCC);
    }

    private String getDisplayTitle() {
        String ct = ChatMessageStore.getCustomTitle();
        return ct != null ? ct : worldName;
    }

    private boolean isMouseOverPen(double mx, double my) {
        String title = getDisplayTitle();
        int titleW = textRenderer.getWidth(title);
        int titleX = panelX + (panelW - titleW) / 2;
        int penX = titleX + titleW + 3;
        int penY = titleY + (TITLE_H - 9) / 2;
        return mx >= penX && mx <= penX + 9 && my >= penY && my <= penY + 9;
    }

    private boolean isMouseOverTitleEditor(double mx, double my) {
        return mx >= titleEditor.getX() && mx <= titleEditor.getX() + titleEditor.getWidth()
            && my >= titleEditor.getY() && my <= titleEditor.getY() + titleEditor.getHeight();
    }

    private void enterTitleEdit() {
        editingTitle = true;
        titleEditor.setVisible(true);
        titleEditor.setText(getDisplayTitle());
        titleEditor.setCursorToEnd(false);
        titleEditor.setFocused(true);
        input.setFocused(false);
        setFocused(titleEditor);
    }

    private void exitTitleEdit(boolean save) {
        if (save) {
            ChatMessageStore.setCustomTitle(titleEditor.getText().trim());
        }
        editingTitle = false;
        titleEditor.setVisible(false);
        input.setFocused(true);
        setFocused(input);
    }

    private void renderMessages(DrawContext context, int mouseX, int mouseY) {
        bubbleRects.clear();
        clickableSpans.clear();
        List<ChatMessageStore.ChatMessage> messages = ChatMessageStore.getMessages();
        if (messages.isEmpty()) return;

        int timeSeps = 0;
        String lastKey = null;
        for (var msg : messages) {
            if (!msg.isSystem()) {
                String key = msg.time().format(TIME_FMT);
                if (lastKey == null || !key.equals(lastKey)) { timeSeps++; lastKey = key; }
            }
        }

        int effectiveMsgBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
        int areaH = effectiveMsgBottom - msgTop;
        int totalH = 0;
        for (var msg : messages) totalH += getMsgHeight(msg) + GAP;
        totalH += timeSeps * (TIME_SEP_H + GAP);
        int prevMaxScroll = maxScroll;
        maxScroll = Math.max(0, totalH - areaH);

        boolean wasAtBottom = scrollOffset >= prevMaxScroll - 2;

        String playerName = client.player != null ? client.player.getName().getString() : "";
        int currentMsgCount = messages.size();
        if (wasAtBottom) {
            newMessageCount = 0;
            hasNewMentionOrQuote = false;
            latestMentionIndex = -1;
            lastSeenMessageCount = currentMsgCount;
        } else if (currentMsgCount > lastSeenMessageCount) {
            if (lastSeenMessageCount > currentMsgCount) lastSeenMessageCount = currentMsgCount;
            for (int i = lastSeenMessageCount; i < currentMsgCount; i++) {
                var msg = messages.get(i);
                if (msg == null) continue;
                newMessageCount++;
                if (msg.content().getString().contains("@" + playerName)) {
                    hasNewMentionOrQuote = true;
                    latestMentionIndex = i;
                }
                if (msg.replySender() != null && msg.replySender().equals(playerName)) {
                    hasNewMentionOrQuote = true;
                    if (i > latestMentionIndex) latestMentionIndex = i;
                }
            }
            lastSeenMessageCount = currentMsgCount;
        }

        if (scrollToBottom || wasAtBottom) {
            scrollOffset = maxScroll;
            scrollToBottom = false;
        }
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        context.enableScissor(panelX, msgTop, panelX + panelW, effectiveMsgBottom);

        int contentY = 0;
        lastKey = null;
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);

            if (!msg.isSystem()) {
                String key = msg.time().format(TIME_FMT);
                if (lastKey == null || !key.equals(lastKey)) {
                    lastKey = key;
                    int ssy = msgTop + contentY - scrollOffset;
                    if (ssy + TIME_SEP_H > msgTop && ssy < msgBottom)
                        renderTimeSeparator(context, msg.time(), ssy);
                    contentY += TIME_SEP_H + GAP;
                }
            }

            int h = getMsgHeight(msg);
            int screenY = msgTop + contentY - scrollOffset;
            contentY += h + GAP;

            if (screenY + h <= msgTop || screenY >= effectiveMsgBottom) continue;
            renderBubble(context, msg, i, screenY, mouseX, mouseY);
        }
        context.disableScissor();
    }

    private void renderTimeSeparator(DrawContext context, LocalTime time, int y) {
        String text = time.format(TIME_FMT);
        int tw = textRenderer.getWidth(text);
        int tx = panelX + (panelW - tw) / 2;
        context.fill(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, 0x44000000);
        context.drawText(textRenderer, Text.literal(text), tx, y + 3, 0xFF999999, false);
    }

    private int getMsgHeight(ChatMessageStore.ChatMessage msg) {
        if (msg.isSystem()) {
            List<OrderedText> lines = textRenderer.wrapLines(msg.content(), panelW - PAD * 2 - 20);
            return lines.size() * textRenderer.fontHeight + 4;
        }
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<OrderedText> lines = textRenderer.wrapLines(msg.content(), bubbleMaxW);
        int h = lines.size() * textRenderer.fontHeight + BUBBLE_PAD_Y * 2 + NAME_H;
        if (msg.replyContent() != null) h += textRenderer.fontHeight + 2;
        return h;
    }

    private void renderBubble(DrawContext context, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY) {
        if (msg.isSystem()) {
            List<OrderedText> lines = textRenderer.wrapLines(msg.content(), panelW - PAD * 2 - 20);
            int yy = baseY + 2;
            for (var line : lines) {
                int lw = textRenderer.getWidth(line);
                renderLineWithClicks(context, line, panelX + (panelW - lw) / 2, yy, 0xFF888888);
                yy += textRenderer.fontHeight;
            }
            return;
        }

        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<OrderedText> lines = textRenderer.wrapLines(msg.content(), bubbleMaxW);

        int textW = 0;
        for (var line : lines) textW = Math.max(textW, textRenderer.getWidth(line));
        int bubbleW = Math.max(textW + BUBBLE_PAD_X * 2, 36);
        int bubbleH = lines.size() * textRenderer.fontHeight + BUBBLE_PAD_Y * 2;

        int avatarX, bubbleX;
        if (own) {
            avatarX = panelX + panelW - PAD - AVATAR;
            bubbleX = avatarX - 4 - bubbleW;
        } else {
            avatarX = panelX + PAD;
            bubbleX = avatarX + AVATAR + 4;
        }

        int nameY = baseY;

        // Reply preview
        if (msg.replyContent() != null) {
            int replyH = textRenderer.fontHeight;
            int replyBarX = bubbleX;
            int replyMaxW = bubbleW - 10;
            String replyText = msg.replySender() + ": " + msg.replyContent();
            String replyDisplay = textRenderer.trimToWidth(replyText, replyMaxW - textRenderer.getWidth("..."));
            if (!replyDisplay.equals(replyText)) replyDisplay += "...";
            int accentColor = own
                ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR, 0xFF0A0A0A)
                : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR, 0xFFFFFFFF);
            context.fill(replyBarX, nameY, replyBarX + 2, nameY + replyH, accentColor);
            context.drawText(textRenderer, Text.literal(replyDisplay), replyBarX + 6, nameY + 1, 0xFF999999, false);
            nameY += replyH + 2;
        }

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - AVATAR - PAD * 2 - 20;
            Text displayName = msg.senderName();
            if (textRenderer.getWidth(displayName) > maxNameW)
                displayName = Text.literal(textRenderer.trimToWidth(displayName.getString(), maxNameW - textRenderer.getWidth("...")) + "...");
            int nameW = textRenderer.getWidth(displayName);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            context.drawText(textRenderer, displayName, startX, nameY, COLOR_NAME, false);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = bubbleY - 6;

        int bg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR, 0xFF95EC69)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR, 0xFF4A4A4A);
        int fg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR, 0xFF0A0A0A)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR, 0xFFFFFFFF);

        context.fill(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH, bg);

        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(context, lines.get(li), bubbleX + BUBBLE_PAD_X,
                bubbleY + BUBBLE_PAD_Y + li * textRenderer.fontHeight, fg);

        // Draw player skin
        renderPlayerSkin(context, msg.senderUUID(), avatarX, avatarY);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = textRenderer.getWidth(label);
            int labelX, labelY = bubbleY + (bubbleH - textRenderer.fontHeight) / 2;
            if (own) {
                labelX = bubbleX - labelW - 3;
            } else {
                labelX = bubbleX + bubbleW + 3;
            }
            context.drawText(textRenderer, Text.literal(label), labelX, labelY, 0xFFFFAA00, false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});
    }

    private void renderPlayerSkin(DrawContext context, UUID uuid, int x, int y) {
        if (client.getNetworkHandler() == null) return;
        var entry = client.getNetworkHandler().getPlayerListEntry(uuid);
        Identifier skin;
        if (entry != null) {
            skin = entry.getSkinTextures().texture();
        } else {
            skin = Identifier.of("textures/entity/player/slim/steve.png");
        }
        // Draw head (face)
        context.drawTexture(skin, x, y, 8, 8, 8, 8, 64, 64);
        // Draw hat layer
        context.drawTexture(skin, x, y, 40, 8, 8, 8, 64, 64);
    }

    private void renderLineWithClicks(DrawContext context, OrderedText line,
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
                    clickableSpans.add(new ClickableSpan(x + spanStart[0], y,
                        pos[0] - spanStart[0], textRenderer.fontHeight, spanStyle[0]));
                    spanStart[0] = pos[0]; spanStyle[0] = style;
                }
            } else {
                if (spanStart[0] >= 0) {
                    clickableSpans.add(new ClickableSpan(x + spanStart[0], y,
                        pos[0] - spanStart[0], textRenderer.fontHeight, spanStyle[0]));
                    spanStart[0] = -1; spanStyle[0] = null;
                }
            }
            pos[0] += charW;
            return true;
        });
        if (spanStart[0] >= 0) {
            clickableSpans.add(new ClickableSpan(x + spanStart[0], y,
                pos[0] - spanStart[0], textRenderer.fontHeight, spanStyle[0]));
        }
    }

    private Style getHoveredStyle(double mouseX, double mouseY) {
        for (ClickableSpan s : clickableSpans) {
            if (mouseX >= s.x && mouseX <= s.x + s.w
                && mouseY >= s.y && mouseY <= s.y + s.h)
                return s.style;
        }
        return null;
    }

    private void renderNotificationBar(DrawContext context, int mouseX, int mouseY) {
        if (newMessageCount <= 0) return;
        int notifY = barTop - NOTIF_H;
        context.fill(panelX, notifY - 1, panelX + panelW, notifY, COLOR_DIVIDER);
        int yellow = 0xFFFFFF55;
        int textY = notifY + (NOTIF_H - textRenderer.fontHeight) / 2;
        String ct = newMessageCount + Text.translatable("e33chat.notif.new_messages").getString() + " \u25BD";
        notifCountLeft = panelX + PAD;
        notifCountRight = notifCountLeft + textRenderer.getWidth(ct);
        notifBarTextY = textY;
        boolean h = mouseX >= notifCountLeft && mouseX <= notifCountRight
            && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
        context.drawText(textRenderer, Text.literal(ct), notifCountLeft, textY, h ? 0xFFFFFF88 : yellow, false);
        if (hasNewMentionOrQuote) {
            String mt = Text.translatable("e33chat.notif.mention").getString() + " \u25BD";
            notifMentionLeft = panelX + panelW - PAD - textRenderer.getWidth(mt);
            notifMentionRight = notifMentionLeft + textRenderer.getWidth(mt);
            h = mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
            context.drawText(textRenderer, Text.literal(mt), notifMentionLeft, textY, h ? 0xFFFFFF88 : yellow, false);
        } else {
            notifMentionLeft = -1;
            notifMentionRight = -1;
        }
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (contextMsgIndex < 0) return;
        int menuH = CTX_ITEM_H * 2 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        context.fill(menuX, menuY, menuX + CTX_W, menuY + menuH, 0xEE2A2A2A);
        context.fill(menuX, menuY, menuX + CTX_W, menuY + 1, COLOR_DIVIDER);
        context.fill(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);
        context.fill(menuX, menuY, menuX + 1, menuY + menuH, COLOR_DIVIDER);
        context.fill(menuX + CTX_W - 1, menuY, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);

        boolean hoverCopy = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int copyBg = hoverCopy ? 0xFF4A4A4A : 0xFF3A3A3A;
        context.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, copyBg);
        context.drawText(textRenderer, Text.translatable("e33chat.context.copy"), menuX + 8, menuY + 4, 0xFFFFFFFF, false);

        context.fill(menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, 0xFF555555);

        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 1 && mouseY <= menuY + menuH;
        int quoteBg = hoverQuote ? 0xFF4A4A4A : 0xFF3A3A3A;
        context.fill(menuX + 1, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 1, menuY + menuH - 1, quoteBg);
        context.drawText(textRenderer, Text.translatable("e33chat.context.quote"), menuX + 8, menuY + CTX_ITEM_H + 5, 0xFFFFFFFF, false);
    }

    private static final int REPLY_BAR_H = 18;

    private void renderReplyBar(DrawContext context, int mouseX, int mouseY) {
        if (replyTargetIndex < 0) return;
        ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
        if (target == null) { replyTargetIndex = -1; return; }

        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int gearX = panelX + PAD;
        int sendX = panelX + panelW - PAD - ICON_S;
        int barX = gearX + ICON_S + 8;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;

        context.fill(barX, barY, barX + barW, barTop - notifOffset, 0xEE1E1E1E);
        context.fill(barX, barTop - notifOffset - 1, barX + barW, barTop - notifOffset, COLOR_DIVIDER);

        String sender = target.senderName().getString();
        if (sender.isEmpty()) sender = Text.translatable("e33chat.sender.system").getString();
        String preview = sender + ": " + target.content().getString();
        int maxW = barW - 24;
        String display = textRenderer.trimToWidth(preview, maxW - textRenderer.getWidth("..."));
        if (!display.equals(preview)) display += "...";
        context.drawText(textRenderer, Text.literal(display), barX + 6, barY + 4, 0xFFAAAAAA, false);

        int cx = barX + barW - 16;
        int cy = barY + 3;
        boolean hoverX = mouseX >= cx && mouseX <= cx + 12 && mouseY >= cy && mouseY <= cy + 12;
        int xBg = hoverX ? 0xFF555555 : 0xFF3A3A3A;
        context.fill(cx, cy, cx + 12, cy + 12, xBg);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"), cx + 6, cy + 2, 0xFFCCCCCC);
    }

    private boolean isMouseOverReplyCancel(double mx, double my) {
        if (replyTargetIndex < 0) return false;
        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int gearX = panelX + PAD;
        int sendX = panelX + panelW - PAD - ICON_S;
        int barX = gearX + ICON_S + 8;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;
        int cx = barX + barW - 16;
        int cy = barY + 3;
        return mx >= cx && mx <= cx + 12 && my >= cy && my <= cy + 12;
    }

    private void renderToast(DrawContext context) {
        if (copyToastTicks <= 0) return;
        int alpha = copyToastTicks > 5 ? 0xFF : (copyToastTicks * 255 / 5);
        int color = (alpha << 24) | 0xFFFFFF;
        String text = Text.translatable("e33chat.toast.copied").getString();
        int tw = textRenderer.getWidth(text);
        int tx = panelX + (panelW - tw) / 2;
        int ty = msgBottom - 24;
        context.fill(tx - 6, ty - 2, tx + tw + 6, ty + textRenderer.fontHeight + 2, 0xCC000000);
        context.drawText(textRenderer, Text.literal(text), tx, ty, color, false);
    }

    private void renderBottomBar(DrawContext context, int mouseX, int mouseY) {
        context.fill(panelX, barTop, panelX + panelW, height, COLOR_BAR_BG);
        context.fill(panelX, barTop, panelX + panelW, barTop + 1, COLOR_DIVIDER);

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        int gearX = panelX + PAD;
        int sendX = panelX + panelW - PAD - ICON_S;
        int ibX = gearX + ICON_S + 6;
        int ibY = barTop + (BAR_H - 20) / 2;
        int ibW = sendX - 6 - ibX;
        int ibH = 20;
        context.fill(ibX, ibY - 1, ibX + ibW, ibY, COLOR_DIVIDER);
        context.fill(ibX, ibY, ibX + ibW, ibY + ibH, COLOR_INPUT_BG);

        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) context.fill(gearX - 1, iconY - 1, gearX + ICON_S + 1, iconY + ICON_S + 1, 0xFF444444);
        drawTextureIcon(context, TEX_GEAR, gearX, iconY, ICON_S);

        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) context.fill(sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, 0xFF444444);
        drawTextureIcon(context, TEX_SEND, sendX, iconY, ICON_S);
    }

    private void loadIconTextures() {
        loadIconTexture(TEX_GEAR, "assets/e33chat/textures/gui/settings.png");
        loadIconTexture(TEX_SEND, "assets/e33chat/textures/gui/send.png");
    }

    private void loadIconTexture(Identifier loc, String classpath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                client.getTextureManager().registerTexture(loc, tex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawTextureIcon(DrawContext context, Identifier tex, int x, int y, int size) {
        RenderSystem.setShaderTexture(0, client.getTextureManager().getTexture(tex).getGlId());
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.enableBlend();
        context.drawTexture(tex, x, y, 0, 0, size, size, size, size);
    }

    private void jumpToMessage(int msgIndex) {
        var msgs = ChatMessageStore.getMessages();
        if (msgIndex < 0 || msgIndex >= msgs.size()) return;
        int cy = 0;
        String lk = null;
        for (int i = 0; i < msgIndex && i < msgs.size(); i++) {
            var m = msgs.get(i);
            if (!m.isSystem()) {
                String k = m.time().format(TIME_FMT);
                if (lk == null || !k.equals(lk)) {
                    lk = k;
                    cy += TIME_SEP_H + GAP;
                }
            }
            cy += getMsgHeight(m) + GAP;
        }
        scrollOffset = Math.max(0, cy - 20);
        newMessageCount = 0;
        hasNewMentionOrQuote = false;
        latestMentionIndex = -1;
        lastSeenMessageCount = msgs.size();
    }

    private void sendMessage() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;

        if (replyTargetIndex >= 0) {
            ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
            if (target != null) {
                ChatMessageStore.setPendingReply(target.content().getString(), target.senderName().getString());
                QuoteSyncPacket.send(target.senderName().getString(), target.content().getString(), text);
            }
            replyTargetIndex = -1;
        }

        if (text.startsWith("/"))
            client.player.networkHandler.sendCommand(text.substring(1));
        else
            client.player.networkHandler.sendChatMessage(text);
        client.inGameHud.getChatHud().addToMessageHistory(text);

        ChatMessageStore.addMessage(Text.literal(text),
            client.player.getUuid(),
            Text.literal(client.player.getName().getString()),
            false);
        ChatMessageStore.incrementPendingEcho(text);

        input.setText("");
        scrollToBottom = true;
    }

    private void moveInHistory(int delta) {
        var history = client.inGameHud.getChatHud().getMessageHistory();
        int size = history.size();
        int newPos = MathHelper.clamp(historyPos + delta, 0, size);
        if (newPos != historyPos) {
            if (newPos == size) {
                historyPos = size;
                input.setText(historyBuffer);
            } else {
                if (historyPos == size) historyBuffer = input.getText();
                input.setText(history.get(newPos));
                historyPos = newPos;
            }
        }
    }

    @Override
    public void removed() {
        ChatMessageStore.setScreenOpen(false);
        client.inGameHud.getChatHud().resetScroll();
    }

    @Override
    public void close() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED) {
            client.setScreen(null);
            return;
        }
        if (closing) return;
        closing = true;
        animStart = net.minecraft.util.Util.getMeasuringTimeMs();
    }

    public static int getInputY() { return inputY; }

    @Override
    public boolean shouldPause() { return false; }

    private static class ClickableSpan {
        final int x, y, w, h;
        final Style style;
        ClickableSpan(int x, int y, int w, int h, Style style) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.style = style;
        }
    }
}
