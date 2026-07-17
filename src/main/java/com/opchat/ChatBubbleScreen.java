package com.opchat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import com.opchat.packets.QuoteSyncPacket;

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
    private static final int AVATAR = 24;
    private static final int BUBBLE_PAD_X = 8;
    private static final int BUBBLE_PAD_Y = 5;
    private static final int GAP = 6;
    private static final int NAME_H = 10;
    private static final int TIME_SEP_H = 14;
    private static final int BAR_H = 38;

    // Sidebar (contacts)
    private static final int SIDEBAR_W = 90;
    private static final int SIDEBAR_ITEM_H = 18;
    private static final int SIDEBAR_TOP = 4;

    private static final int ICON_S = 16;
    private static final Identifier TEX_GEAR = Identifier.of("opchat", "textures/gui/settings");
    private static final Identifier TEX_SEND = Identifier.of("opchat", "textures/gui/send");
    private static boolean iconsLoaded;

    private static final int COLOR_NAME = 0xFFCCCCCC;
    private static final int COLOR_TIME = 0xFF999999;
    private static final int COLOR_DIVIDER = 0xFF333333;
    private int colorPanelBg;
    private int colorTitleBg;
    private int colorBarBg;
    private int colorInputBg;
    private int colorSidebarBg;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private TextFieldWidget input;
    private ChatInputSuggestor commandSuggestions;
    private static int inputY;
    private int suggestionYOffset;
    private final String initialText;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private String historyBuffer = "";
    private int historyPos = -1;
    private boolean suppressHistoryReset = false;
    private boolean historyListVisible = false;
    private String worldName;
    private boolean editingTitle;
    private TextFieldWidget titleEditor;

    // Right-click menu
    private int contextMsgIndex = -1;
    private int contextX, contextY;
    private static final int CTX_W = 110;
    private static final int CTX_ITEM_H = 18;

    // Bubble hit tracking
    private final List<int[]> bubbleRects = new ArrayList<>();

    // Clickable text span tracking
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

    // Reply / quote
    private int replyTargetIndex = -1;

    // Private chat target (null = public chat)
    private String selectedContact = null;
    private int sidebarScroll = 0;

    // Contact right-click context menu
    private String contextMenuContact;
    private String contextMenuContactGroupName; // null = not in any group
    private int contextMenuX, contextMenuY;
    private boolean subMenuVisible; // "add to group" submenu
    private static final int CTX_MENU_W = 90;
    private static final int CTX_SUB_W = 100;

    // Avatar right-click context menu (in public chat)
    private String avatarMenuPlayer; // null = no avatar menu open
    private int avatarMenuX, avatarMenuY;
    private boolean avatarSubMenuVisible; // quick commands submenu
    private static final int AVATAR_MENU_W = 100;
    private static final int AVATAR_SUB_W = 160;

    // Nickname editor
    private boolean editingNickname;
    private String nicknameEditTarget;
    private TextFieldWidget nicknameField;

    // Quick inputs
    private boolean quickPanelOpen;
    private TextFieldWidget quickEditField;
    private int quickIconX;
    private int itemIconX; // 展示手持物品按钮位置
    private static final int QUICK_PANEL_W = 180;
    private static final int QUICK_ITEM_H = 18;
    private static final int QUICK_EDIT_H = 22;
    private static final int DRAG_HANDLE_W = 12; // 拖拽手柄宽度

    // Quick input drag sorting
    private boolean quickInputDragging;
    private int quickInputDragIndex = -1;

    // Quick command drag sorting
    private boolean quickCmdDragging;
    private int quickCmdDragIndex = -1;

    // Quick commands (/ shortcut)
    private int slashIconX;
    private boolean quickCmdPanelOpen;
    private boolean quickCmdEditing; // show add/edit form
    private int quickCmdEditIndex = -1;
    private TextFieldWidget quickCmdDisplayField;
    private TextFieldWidget quickCmdCommandField;
    private TextFieldWidget proxyCmdField; // invisible proxy for ChatInputSuggestor (handles @* → aa)
    private boolean syncingProxy = false;
    private int[] proxyPlaceholderStarts = new int[0]; // positions of "aa" that replace @*
    private ChatInputSuggestor quickCmdSuggestor;
    private static final int QUICK_CMD_PANEL_W = 200;
    private static final int QUICK_CMD_ITEM_H = 18;
    private static final int QUICK_CMD_FORM_H = 78;

    // Hidden contacts group (collapsible)
    private boolean hiddenGroupExpanded = false;

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
        super(Text.translatable("opchat.screen.title"));
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        historyPos = client.inGameHud.getChatHud().getMessageHistory().size();
        ChatMessageStore.setScreenOpen(true);
        // 进入聊天框：启用输入法
        com.opchat.ime.IMEBlocker.enableIME();
        animStart = net.minecraft.util.Util.getMeasuringTimeMs();
        closing = false;
        contextMenuContact = null;
        contextMenuContactGroupName = null;
        subMenuVisible = false;
        avatarMenuPlayer = null;
        avatarSubMenuVisible = false;
        editingNickname = false;
        int a = (int)(MathHelper.clamp(ChatBubbleConfig.PANEL_OPACITY, 0, 100) * 2.55f);
        colorPanelBg = (a << 24) | 0x1E1E1E;
        colorTitleBg = (a << 24) | 0x242424;
        colorBarBg = (a << 24) | 0x242424;
        colorInputBg = (a << 24) | 0x2A2A2A;
        colorSidebarBg = (a << 24) | 0x161616;

        panelW = Math.max(200, (int) (width * 0.4));
        panelX = SIDEBAR_W;
        titleY = 4;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = barTop - 1;

        int ibY = barTop + (BAR_H - 20) / 2;
        inputY = ibY;
        int sendX = panelX + panelW - PAD - ICON_S;
        quickIconX = sendX - ICON_S - 6;
        itemIconX = quickIconX - ICON_S - 4; // 展示手持物品按钮
        slashIconX = panelX + PAD;
        int inputX = slashIconX + ICON_S + 4;
        int inputW = itemIconX - 6 - inputX;

        input = new TextFieldWidget(textRenderer, inputX, ibY, inputW, 20, Text.literal(""));
        input.setMaxLength(256);
        input.setDrawsBackground(false);
        input.setText(initialText);
        input.setFocused(true);
        input.setEditable(true);
        input.setFocusUnlocked(false);
        input.setChangedListener(this::onEdited);
        addDrawableChild(input);

        commandSuggestions = new ChatInputSuggestor(client, this, input, textRenderer,
            false, false, 0, 8, true, 0xDD1E1E1E);
        commandSuggestions.setWindowActive(initialText != null && initialText.startsWith("/"));
        commandSuggestions.refresh();
        suggestionYOffset = barTop - 2 - (height - 15);

        // Quick input edit field (in popup panel)
        quickEditField = new TextFieldWidget(textRenderer, 0, 0, 0, 14, Text.literal(""));
        quickEditField.setMaxLength(256);
        quickEditField.setDrawsBackground(false);
        quickEditField.setVisible(false);
        addDrawableChild(quickEditField);

        // Quick command edit form fields
        quickCmdDisplayField = new TextFieldWidget(textRenderer, 0, 0, 0, 16, Text.literal(""));
        quickCmdDisplayField.setMaxLength(64);
        quickCmdDisplayField.setDrawsBackground(false);
        quickCmdDisplayField.setVisible(false);
        addDrawableChild(quickCmdDisplayField);

        quickCmdCommandField = new TextFieldWidget(textRenderer, 0, 0, 0, 16, Text.literal(""));
        quickCmdCommandField.setMaxLength(256);
        quickCmdCommandField.setDrawsBackground(false);
        quickCmdCommandField.setVisible(false);
        quickCmdCommandField.setChangedListener(text -> {
            if (syncingProxy) return;
            if (quickCmdSuggestor != null) {
                java.util.List<Integer> starts = new java.util.ArrayList<>();
                StringBuilder proxyText = new StringBuilder();
                for (int i = 0; i < text.length(); ) {
                    if (i + 1 < text.length() && text.charAt(i) == '@' && text.charAt(i + 1) == '*') {
                        starts.add(proxyText.length());
                        proxyText.append("aa");
                        i += 2;
                    } else {
                        proxyText.append(text.charAt(i));
                        i++;
                    }
                }
                proxyPlaceholderStarts = starts.stream().mapToInt(Integer::intValue).toArray();
                int cursor = quickCmdCommandField.getCursor();
                syncingProxy = true;
                proxyCmdField.setText(proxyText.toString());
                proxyCmdField.setCursor(cursor, false);
                syncingProxy = false;
                quickCmdSuggestor.setWindowActive(text.startsWith("/"));
                quickCmdSuggestor.refresh();
            }
        });
        addDrawableChild(quickCmdCommandField);

        // Invisible proxy field: ChatInputSuggestor reads from this (with @* → aa),
        // user edits quickCmdCommandField (with @* preserved).
        proxyCmdField = new TextFieldWidget(textRenderer, 0, 0, 0, 16, Text.literal(""));
        proxyCmdField.setMaxLength(256);
        proxyCmdField.setVisible(false);
        proxyCmdField.setChangedListener(text -> {
            if (syncingProxy) return;
            String cmdText;
            if (proxyPlaceholderStarts.length == 0) {
                cmdText = text;
            } else {
                StringBuilder cmd = new StringBuilder(text);
                for (int i = proxyPlaceholderStarts.length - 1; i >= 0; i--) {
                    int pos = proxyPlaceholderStarts[i];
                    if (pos + 2 <= cmd.length() && cmd.charAt(pos) == 'a' && cmd.charAt(pos + 1) == 'a') {
                        cmd.replace(pos, pos + 2, "@*");
                    }
                }
                cmdText = cmd.toString();
            }
            int cursor = proxyCmdField.getCursor();
            syncingProxy = true;
            quickCmdCommandField.setText(cmdText);
            quickCmdCommandField.setCursor(cursor, false);
            syncingProxy = false;
            if (quickCmdSuggestor != null) {
                quickCmdSuggestor.setWindowActive(cmdText.startsWith("/"));
                quickCmdSuggestor.refresh();
            }
        });

        quickCmdSuggestor = new ChatInputSuggestor(client, this, proxyCmdField, textRenderer,
            false, false, 0, 8, true, 0xDD1E1E1E);

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

        nicknameField = new TextFieldWidget(textRenderer, 0, 0, 120, 16, Text.literal(""));
        nicknameField.setMaxLength(32);
        nicknameField.setDrawsBackground(false);
        nicknameField.setVisible(false);
        addDrawableChild(nicknameField);
    }

    @Override
    protected void setInitialFocus() {
        this.setFocused(input);
    }

    private String getWorldName() {
        if (client.getServer() != null)
            return client.getServer().getSaveProperties().getLevelName();
        if (client.getCurrentServerEntry() != null)
            return client.getCurrentServerEntry().name;
        return Text.translatable("opchat.title.fallback").getString();
    }

    private void onEdited(String text) {
        if (suppressHistoryReset) {
            // 历史记录导航中：关闭命令补全，保留历史列表显示
            if (commandSuggestions != null) {
                commandSuggestions.setWindowActive(false);
            }
        } else {
            // 用户手动编辑：关闭历史列表，刷新命令补全
            if (commandSuggestions != null) {
                commandSuggestions.setWindowActive(text.startsWith("/"));
                commandSuggestions.refresh();
            }
            historyPos = client.inGameHud.getChatHud().getMessageHistory().size();
            historyListVisible = false;
        }
    }

    @Override
    public void tick() {
        if (copyToastTicks > 0) copyToastTicks--;
        if (closing && net.minecraft.util.Util.getMeasuringTimeMs() - animStart >= ANIM_MS)
            client.setScreen(null);
        if (sidebarCacheTick++ % 20 == 0) cachedSidebarEntries = null;
    }

    private float getAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED) return 1.0f;
        long elapsed = net.minecraft.util.Util.getMeasuringTimeMs() - animStart;
        float p = (float) elapsed / ANIM_MS;
        if (closing) p = 1.0f - p;
        return MathHelper.clamp(p, 0f, 1f);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        if (editingTitle) {
            if (keyCode == 256) { exitTitleEdit(false); return true; }
            if (keyCode == 257 || keyCode == 335) { exitTitleEdit(true); return true; }
            return titleEditor.keyPressed(keyInput);
        }
        if (editingNickname) {
            if (keyCode == 256) { editingNickname = false; nicknameField.setVisible(false); input.setFocused(true); setFocused(input); return true; }
            if (keyCode == 257 || keyCode == 335) { saveNickname(); return true; }
            return nicknameField.keyPressed(keyInput);
        }
        if (quickPanelOpen && quickEditField.isVisible() && quickEditField.isFocused()) {
            if (keyCode == 256) { closeQuickPanel(); return true; }
            if (keyCode == 257 || keyCode == 335) { addQuickInputFromField(); return true; }
            return quickEditField.keyPressed(keyInput);
        }
        if (quickCmdPanelOpen && quickCmdEditing
            && (quickCmdDisplayField.isFocused() || quickCmdCommandField.isFocused())) {
            if (quickCmdCommandField.isFocused() && quickCmdSuggestor != null
                && quickCmdSuggestor.keyPressed(keyInput)) {
                return true;
            }
            if (keyCode == 256) {
                quickCmdEditing = false;
                quickCmdDisplayField.setVisible(false);
                quickCmdCommandField.setVisible(false);
                if (quickCmdSuggestor != null) quickCmdSuggestor.clearWindow();
                input.setFocused(true);
                setFocused(input);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (quickCmdCommandField.isFocused()) {
                    saveQuickCommand();
                    return true;
                }
                quickCmdCommandField.setFocused(true);
                setFocused(quickCmdCommandField);
                return true;
            }
            if (keyCode == 258) {
                // Tab: toggle focus between fields
                if (quickCmdDisplayField.isFocused()) {
                    quickCmdCommandField.setFocused(true);
                    setFocused(quickCmdCommandField);
                } else {
                    quickCmdDisplayField.setFocused(true);
                    setFocused(quickCmdDisplayField);
                }
                return true;
            }
            if (quickCmdDisplayField.isFocused()) return quickCmdDisplayField.keyPressed(keyInput);
            return quickCmdCommandField.keyPressed(keyInput);
        }
        if (historyListVisible && ChatBubbleConfig.SEND_HISTORY_PREVIEW
            && (keyCode == 265 || keyCode == 264)) {
            moveInHistory(keyCode == 265 ? -1 : 1);
            return true;
        }
        if (commandSuggestions != null && commandSuggestions.keyPressed(keyInput))
            return true;
        if (keyCode == 256) { close(); return true; }
        if (keyCode == 257 || keyCode == 335) {
            if (commandSuggestions != null) commandSuggestions.clearWindow();
            sendMessage();
            return true;
        }
        if (keyCode == 265) { moveInHistory(-1); return true; }
        if (keyCode == 264) { moveInHistory(1); return true; }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput charInput) {
        if (charInput.codepoint() == '/' && selectedContact == null && !input.getText().startsWith("/")) {
            input.setText("/" + input.getText());
            input.setCursorToEnd(false);
            if (commandSuggestions != null) {
                commandSuggestions.setWindowActive(true);
                commandSuggestions.refresh();
            }
            input.setFocused(true);
            setFocused(input);
            return true;
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (historyListVisible && ChatBubbleConfig.SEND_HISTORY_PREVIEW
            && getHistoryListHoverIndex((int) mouseX, (int) mouseY) >= 0) {
            moveInHistory(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        if (mouseX < SIDEBAR_W) {
            sidebarScroll -= (int) (verticalAmount * 3);
            sidebarScroll = Math.max(0, sidebarScroll);
            return true;
        }
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(verticalAmount))
            return true;
        if (quickCmdPanelOpen && quickCmdEditing && quickCmdSuggestor != null
            && quickCmdCommandField.isFocused()
            && quickCmdSuggestor.mouseScrolled(verticalAmount))
            return true;
        scrollToBottom = false;
        scrollOffset -= (int) (verticalAmount * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();
        // Context menu clicks
        if (button == 0 && contextMsgIndex >= 0) {
            handleContextClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (contextMsgIndex >= 0) { contextMsgIndex = -1; return true; }

        // Quick panel click handling
        if (quickPanelOpen) {
            int[] b = getQuickPanelBounds();
            boolean inPanel = b != null && mouseX >= b[0] && mouseX <= b[0] + b[2]
                && mouseY >= b[1] && mouseY <= b[1] + b[3];
            int iconY = barTop + (BAR_H - ICON_S) / 2;
            boolean onToggle = (mouseX >= quickIconX && mouseX <= quickIconX + ICON_S
                || mouseX >= itemIconX && mouseX <= itemIconX + ICON_S)
                && mouseY >= iconY && mouseY <= iconY + ICON_S;
            if (inPanel && button == 0) {
                handleQuickPanelClick((int) mouseX, (int) mouseY);
                return true;
            }
            if (!inPanel && !onToggle) {
                closeQuickPanel();
                return true;
            }
        }

        // Quick command panel click handling
        if (quickCmdPanelOpen) {
            int[] b = getQuickCmdPanelBounds();
            boolean inPanel = b != null && mouseX >= b[0] && mouseX <= b[0] + b[2]
                && mouseY >= b[1] && mouseY <= b[1] + b[3];
            int iconY = barTop + (BAR_H - ICON_S) / 2;
            boolean onToggle = (mouseX >= slashIconX && mouseX <= slashIconX + ICON_S
                || mouseX >= itemIconX && mouseX <= itemIconX + ICON_S)
                && mouseY >= iconY && mouseY <= iconY + ICON_S;
            if (inPanel && button == 0) {
                handleQuickCmdPanelClick((int) mouseX, (int) mouseY);
                return true;
            }
            if (!inPanel && !onToggle) {
                closeQuickCmdPanel();
                return true;
            }
        }

        // Context menu: check first so clicks on the popup are not stolen by sidebar
        if (contextMenuContact != null && button == 0) {
            int menuItems = 4; // pin/hide/add-or-remove/nickname
            int menuH = CTX_ITEM_H * menuItems;

            // Check submenu first
            if (subMenuVisible) {
                int subX = contextMenuX + CTX_MENU_W;
                int subItems = ChatBubbleConfig.CONTACT_GROUPS.size() + 1; // groups + new group
                int subH = CTX_ITEM_H * subItems;
                if (mouseX >= subX && mouseX <= subX + CTX_SUB_W
                    && mouseY >= contextMenuY + CTX_ITEM_H * 2 && mouseY <= contextMenuY + CTX_ITEM_H * 2 + subH) {
                    int item = (int) ((mouseY - (contextMenuY + CTX_ITEM_H * 2)) / CTX_ITEM_H);
                    if (item < ChatBubbleConfig.CONTACT_GROUPS.size()) {
                        String groupName = ChatBubbleConfig.CONTACT_GROUPS.get(item).name;
                        ChatBubbleConfig.addToGroup(groupName, contextMenuContact);
                    } else {
                        // New group - create with default name
                        String baseName = "\u65b0\u5206\u7ec4";
                        String name = baseName;
                        int n = 2;
                        while (true) {
                            boolean exists = false;
                            for (var g : ChatBubbleConfig.CONTACT_GROUPS) {
                                if (g.name.equals(name)) { exists = true; break; }
                            }
                            if (!exists) break;
                            name = baseName + n++;
                        }
                        ChatBubbleConfig.addGroup(name);
                        ChatBubbleConfig.addToGroup(name, contextMenuContact);
                    }
                    contextMenuContact = null;
                    subMenuVisible = false;
                    return true;
                }
            }

            if (mouseX >= contextMenuX && mouseX <= contextMenuX + CTX_MENU_W
                && mouseY >= contextMenuY && mouseY <= contextMenuY + menuH) {
                int item = (int) ((mouseY - contextMenuY) / CTX_ITEM_H);
                if (item == 0) {
                    WhisperHistory.togglePin(contextMenuContact);
                    contextMenuContact = null;
                } else if (item == 1) {
                    WhisperHistory.setHidden(contextMenuContact, !WhisperHistory.isHidden(contextMenuContact));
                    if (contextMenuContact.equals(selectedContact)) {
                        selectedContact = null;
                        resetViewState();
                    }
                    contextMenuContact = null;
                } else if (item == 2) {
                    if (contextMenuContactGroupName != null) {
                        ChatBubbleConfig.removeFromGroup(contextMenuContactGroupName, contextMenuContact);
                        contextMenuContact = null;
                    } else {
                        subMenuVisible = !subMenuVisible;
                    }
                } else if (item == 3) {
                    String target = contextMenuContact;
                    contextMenuContact = null;
                    openNicknameEditor(target);
                }
                return true;
            }
            contextMenuContact = null;
            subMenuVisible = false;
            return true;
        }

        // Avatar context menu (right-clicked player avatar in public chat)
        if (avatarMenuPlayer != null && button == 0) {
            int menuItems = 3;
            int menuH = CTX_ITEM_H * menuItems;

            // Check submenu first (quick commands)
            if (avatarSubMenuVisible) {
                int subX = avatarMenuX + AVATAR_MENU_W;
                int subItems = getActiveQuickCommandList().size();
                int subH = CTX_ITEM_H * Math.max(subItems, 1);
                if (mouseX >= subX && mouseX <= subX + AVATAR_SUB_W
                    && mouseY >= avatarMenuY + CTX_ITEM_H && mouseY <= avatarMenuY + CTX_ITEM_H + subH) {
                    int item = (int) ((mouseY - (avatarMenuY + CTX_ITEM_H)) / CTX_ITEM_H);
                    if (item >= 0 && item < getActiveQuickCommandList().size()) {
                        String player = avatarMenuPlayer;
                        avatarMenuPlayer = null;
                        avatarSubMenuVisible = false;
                        executeQuickCommandForPlayer(getActiveQuickCommandList().get(item), player);
                    }
                    return true;
                }
            }

            if (mouseX >= avatarMenuX && mouseX <= avatarMenuX + AVATAR_MENU_W
                && mouseY >= avatarMenuY && mouseY <= avatarMenuY + menuH) {
                int item = (int) ((mouseY - avatarMenuY) / CTX_ITEM_H);
                if (item == 0) {
                    // Private chat
                    selectedContact = avatarMenuPlayer;
                    avatarMenuPlayer = null;
                    resetViewState();
                    input.setFocused(true);
                    setFocused(input);
                } else if (item == 1) {
                    // Quick commands submenu
                    avatarSubMenuVisible = !avatarSubMenuVisible;
                } else if (item == 2) {
                    // Set nickname
                    String target = avatarMenuPlayer;
                    avatarMenuPlayer = null;
                    openNicknameEditor(target);
                }
                return true;
            }
            avatarMenuPlayer = null;
            avatarSubMenuVisible = false;
            return true;
        }

        // Nickname editor click handling
        if (editingNickname && button == 0) {
            int nickW = 140;
            int nickH = 36;
            int nickX = width / 2 - nickW / 2;
            int nickY = height / 2 - nickH / 2;
            if (mouseX >= nickX && mouseX <= nickX + nickW && mouseY >= nickY && mouseY <= nickY + nickH) {
                // Click inside editor - focus the field
                int fieldY = nickY + 14;
                if (mouseY >= fieldY && mouseY <= fieldY + 16) {
                    nicknameField.setFocused(true);
                    setFocused(nicknameField);
                }
                return true;
            }
            // Click outside - save and close
            saveNickname();
            return true;
        }

        if (button == 0 && mouseX < SIDEBAR_W) {
            handleSidebarClick((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 1 && mouseX < SIDEBAR_W) {
            handleSidebarRightClick((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && mouseX >= panelX + panelW) {
            close();
            return true;
        }

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

        if (button == 0 && historyListVisible && ChatBubbleConfig.SEND_HISTORY_PREVIEW
            && handleHistoryListClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (commandSuggestions != null) {
            Click adjustedClick = new Click(click.x(), click.y() - suggestionYOffset, click.buttonInfo());
            if (commandSuggestions.mouseClicked(adjustedClick))
                return true;
        }

        if (quickCmdPanelOpen && quickCmdEditing && quickCmdSuggestor != null
            && quickCmdCommandField.isFocused()
            && quickCmdSuggestor.mouseClicked(click)) {
            return true;
        }

        if (button == 0) {
            if (editingTitle) {
                if (!isMouseOverTitleEditor(mouseX, mouseY)) {
                    exitTitleEdit(true);
                    return true;
                }
                return super.mouseClicked(click, bl);
            }
            if (isMouseOverPen(mouseX, mouseY)) {
                enterTitleEdit();
                return true;
            }
            // Settings gear in title bar
            int gearX = panelX + PAD;
            int gearY = titleY + (TITLE_H - ICON_S) / 2;
            if (mouseX >= gearX && mouseX <= gearX + ICON_S
                && mouseY >= gearY && mouseY <= gearY + ICON_S) {
                client.setScreen(new ChatBubbleConfigScreen(this));
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
                ChatMessageStore.ChatMessage msg = getMessageAt(r[4]);
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
                int avatarX = r[0] - AVATAR - 4;
                int avatarY = r[1] - 6;
                if (mouseX >= avatarX && mouseX <= avatarX + AVATAR
                    && mouseY >= avatarY && mouseY <= avatarY + AVATAR) {
                    ChatMessageStore.ChatMessage msg = getMessageAt(r[4]);
                    if (msg != null && !msg.isOwn() && !msg.isSystem() && selectedContact == null) {
                        String sender = msg.senderName().getString();
                        if (!sender.isEmpty()) {
                            avatarMenuPlayer = sender;
                            avatarSubMenuVisible = false;
                            avatarMenuX = Math.min((int) mouseX, width - AVATAR_MENU_W - 2);
                            int menuItems = 3;
                            int menuH = CTX_ITEM_H * menuItems;
                            avatarMenuY = Math.min((int) mouseY, height - menuH - 2);
                        }
                    }
                    return true;
                }
            }
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
                ClickEvent clickEvent = style.getClickEvent();
                if (clickEvent instanceof ClickEvent.SuggestCommand sc) {
                    input.setText(sc.command());
                    return true;
                }
                Screen.handleClickEvent(clickEvent, client, this);
                return true;
            }
            // 气泡左键单击：复制消息内容到剪贴板
            for (int[] r : bubbleRects) {
                if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    ChatMessageStore.ChatMessage msg = getMessageAt(r[4]);
                    if (msg != null) {
                        client.keyboard.setClipboard(msg.content().getString());
                        copyToastTicks = 20;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        int button = click.button();
        if (button != 0) return super.mouseDragged(click, offsetX, offsetY);
        double mouseX = click.x();
        double mouseY = click.y();

        // 快捷输入拖拽中：根据当前 y 位置与其它项交换顺序
        if (quickInputDragging && quickInputDragIndex >= 0 && quickPanelOpen) {
            int[] b = getQuickPanelBounds();
            int py = b[1];
            int editY = py + 4;
            int listY = editY + QUICK_EDIT_H + 2;
            int relY = (int) mouseY - listY;
            if (relY >= 0) {
                int newIndex = relY / QUICK_ITEM_H;
                int size = ChatBubbleConfig.QUICK_INPUTS.size();
                if (newIndex < 0) newIndex = 0;
                if (newIndex >= size) newIndex = size - 1;
                if (newIndex != quickInputDragIndex) {
                    String moved = ChatBubbleConfig.QUICK_INPUTS.remove(quickInputDragIndex);
                    ChatBubbleConfig.QUICK_INPUTS.add(newIndex, moved);
                    ChatBubbleConfig.save();
                    quickInputDragIndex = newIndex;
                }
            }
            return true;
        }

        // 快捷指令拖拽中
        if (quickCmdDragging && quickCmdDragIndex >= 0 && quickCmdPanelOpen) {
            int[] b = getQuickCmdPanelBounds();
            int py = b[1];
            int headerY = py + 4;
            int listY = headerY + 22;
            int relY = (int) mouseY - listY;
            if (relY >= 0) {
                int newIndex = relY / QUICK_CMD_ITEM_H;
                var list = getActiveQuickCommandList();
                int size = list.size();
                if (newIndex < 0) newIndex = 0;
                if (newIndex >= size) newIndex = size - 1;
                if (newIndex != quickCmdDragIndex) {
                    var moved = list.remove(quickCmdDragIndex);
                    list.add(newIndex, moved);
                    ChatBubbleConfig.save();
                    quickCmdDragIndex = newIndex;
                }
            }
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (quickInputDragging) {
                quickInputDragging = false;
                quickInputDragIndex = -1;
                return true;
            }
            if (quickCmdDragging) {
                quickCmdDragging = false;
                quickCmdDragIndex = -1;
                return true;
            }
        }
        return super.mouseReleased(click);
    }

    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        int slashX = panelX + PAD;
        if (mx >= slashX && mx <= slashX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            toggleQuickCmdPanel();
            return true;
        }
        if (mx >= quickIconX && mx <= quickIconX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            toggleQuickPanel();
            return true;
        }
        if (mx >= itemIconX && mx <= itemIconX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (quickPanelOpen) closeQuickPanel();
            if (quickCmdPanelOpen) closeQuickCmdPanel();
            sendItemShowcase();
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
        ChatMessageStore.ChatMessage ctxMsg = getMessageAt(contextMsgIndex);
        int menuH = CTX_ITEM_H * 3 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        if (mx >= menuX && mx <= menuX + CTX_W) {
            int item0Top = menuY;
            int item0Bottom = menuY + CTX_ITEM_H;
            int item1Top = menuY + CTX_ITEM_H + 1;
            int item1Bottom = item1Top + CTX_ITEM_H;
            int item2Top = menuY + (CTX_ITEM_H + 1) * 2;
            int item2Bottom = item2Top + CTX_ITEM_H;

            if (my >= item0Top && my <= item0Bottom) {
                // 引用
                replyTargetIndex = contextMsgIndex;
            } else if (my >= item1Top && my <= item1Bottom) {
                // 添加到快捷发送
                if (ctxMsg != null) {
                    String text = ctxMsg.content().getString();
                    if (!text.isEmpty() && !ChatBubbleConfig.QUICK_INPUTS.contains(text)) {
                        ChatBubbleConfig.QUICK_INPUTS.add(text);
                        ChatBubbleConfig.save();
                    }
                }
            } else if (my >= item2Top && my <= item2Bottom) {
                // 添加到快捷指令（弹出编辑框，预填充消息内容）
                if (ctxMsg != null) {
                    String text = ctxMsg.content().getString();
                    openQuickCmdEditorWith(text);
                }
            }
        }
        contextMsgIndex = -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float anim = getAnimProgress();
        int panelOffset = (int) ((anim - 1.0f) * panelW);

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().translate(panelOffset, 0, 0);

        context.fill(panelX, 0, panelX + panelW, height, colorPanelBg);

        renderSidebar(context, mouseX, mouseY);
        renderTitleBar(context, mouseX, mouseY);
        renderMessages(context, mouseX, mouseY);
        Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            // Hover event rendering would go here
        }
        renderNotificationBar(context, mouseX, mouseY);
        renderReplyBar(context, mouseX, mouseY);
        renderContextMenu(context, mouseX, mouseY);
        if (avatarMenuPlayer != null) renderAvatarMenu(context, mouseX, mouseY);
        if (editingNickname) renderNicknameEditor(context, mouseX, mouseY);
        renderToast(context);
        renderBottomBar(context, mouseX, mouseY);
        if (historyListVisible && ChatBubbleConfig.SEND_HISTORY_PREVIEW) renderHistoryList(context, mouseX, mouseY);
        if (quickPanelOpen) renderQuickPanel(context, mouseX, mouseY);
        if (quickCmdPanelOpen) renderQuickCmdPanel(context, mouseX, mouseY);

        context.enableScissor(panelX, 0, panelX + panelW, height);
        if (commandSuggestions != null) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0f, suggestionYOffset);
            commandSuggestions.render(context, mouseX, mouseY - suggestionYOffset);
            context.getMatrices().popMatrix();
        }
        context.disableScissor();

        if (quickCmdPanelOpen && quickCmdEditing && quickCmdSuggestor != null) {
            quickCmdSuggestor.render(context, mouseX, mouseY);
        }

        RenderSystem.getModelViewStack().popMatrix();

        super.render(context, mouseX, mouseY, delta);
    }

    private record SidebarEntry(int kind, String name, String groupName, int count) {}
    // kind: 0=group_header, 1=group_contact, 2=contact, 3=hidden, 4=hidden_group_header, 5=offline_contact

    private java.util.List<SidebarEntry> cachedSidebarEntries = null;
    private int sidebarCacheTick = 0;

    private java.util.List<SidebarEntry> getSidebarEntries() {
        java.util.List<SidebarEntry> entries = new java.util.ArrayList<>();
        if (client.getNetworkHandler() == null) return entries;
        String selfName = client.player != null ? client.player.getName().getString() : "";
        java.util.Set<String> onlineSet = new java.util.HashSet<>();
        for (var entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (name != null && !name.equals(selfName)) onlineSet.add(name);
        }
        java.util.Set<String> allOnline = new java.util.HashSet<>(onlineSet);

        // Group headers + members
        for (var group : ChatBubbleConfig.CONTACT_GROUPS) {
            java.util.List<String> members = ChatBubbleConfig.getGroupOnlineMembers(group, onlineSet);
            int onlineCount = 0;
            for (String m : members) { if (!WhisperHistory.isHidden(m)) onlineCount++; }
            entries.add(new SidebarEntry(0, null, group.name, onlineCount));
            if (group.expanded) {
                for (String m : members) {
                    if (!WhisperHistory.isHidden(m)) {
                        onlineSet.remove(m);
                        entries.add(new SidebarEntry(1, m, group.name, 0));
                    }
                }
            } else {
                for (String m : members) {
                    if (!WhisperHistory.isHidden(m)) onlineSet.remove(m);
                }
            }
        }

        // Pinned contacts first (only if online and not hidden)
        for (String contact : WhisperHistory.getPinnedContacts()) {
            if (!WhisperHistory.isHidden(contact) && onlineSet.remove(contact))
                entries.add(new SidebarEntry(2, contact, null, 0));
        }
        // Recent whisper contacts (most recent at top)
        for (String contact : WhisperHistory.getRecentContacts()) {
            if (!WhisperHistory.isHidden(contact) && onlineSet.remove(contact))
                entries.add(new SidebarEntry(2, contact, null, 0));
        }
        // Remaining online players, sorted alphabetically (excluding hidden)
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (String name : onlineSet) {
            if (!WhisperHistory.isHidden(name)) rest.add(name);
        }
        java.util.Collections.sort(rest);
        for (String name : rest) entries.add(new SidebarEntry(2, name, null, 0));

        // Offline recent whisper contacts (keep conversations accessible)
        for (String contact : WhisperHistory.getRecentContacts()) {
            if (!WhisperHistory.isHidden(contact) && !allOnline.contains(contact)) {
                entries.add(new SidebarEntry(5, contact, null, 0));
            }
        }

        // Hidden contacts grouped at the bottom (online + offline)
        java.util.List<String> allHidden = new java.util.ArrayList<>(WhisperHistory.getHiddenContacts());
        if (!allHidden.isEmpty()) {
            java.util.Collections.sort(allHidden);
            entries.add(new SidebarEntry(4, null, Text.translatable("opchat.contact.hidden_group").getString(), allHidden.size()));
            if (hiddenGroupExpanded) {
                for (String name : allHidden) {
                    entries.add(new SidebarEntry(3, name, null, 0));
                }
            }
        }
        return entries;
    }

    private java.util.List<SidebarEntry> getCachedSidebarEntries() {
        if (cachedSidebarEntries == null) cachedSidebarEntries = getSidebarEntries();
        return cachedSidebarEntries;
    }

    private void renderSidebar(DrawContext context, int mouseX, int mouseY) {
        int sx = 0;
        int sw = SIDEBAR_W;

        context.fill(sx, 0, sx + sw, height, colorSidebarBg);
        context.fill(sx + sw - 1, 0, sx + sw, height, COLOR_DIVIDER);

        int itemY = SIDEBAR_TOP;

        // Public chat
        boolean pubSelected = selectedContact == null;
        boolean hoverPub = mouseX >= sx && mouseX <= sx + sw && mouseY >= itemY && mouseY <= itemY + SIDEBAR_ITEM_H;
        if (pubSelected || hoverPub)
            context.fill(sx, itemY, sx + sw, itemY + SIDEBAR_ITEM_H, pubSelected ? 0xFF2A4A7A : 0xFF333333);
        String pubText = Text.translatable("opchat.contact.public").getString();
        String pubDisplay = textRenderer.trimToWidth(pubText, sw - 8);
        int pubColor = pubSelected ? 0xFFFFFFFF : (hoverPub ? 0xFFFFFFFF : 0xFFAAAAAA);
        context.drawText(textRenderer, Text.literal(pubDisplay), sx + 4,
            itemY + (SIDEBAR_ITEM_H - textRenderer.fontHeight) / 2, pubColor, false);

        itemY += SIDEBAR_ITEM_H + 2;
        context.fill(sx + 4, itemY, sx + sw - 4, itemY + 1, COLOR_DIVIDER);
        itemY += 3;

        // Sidebar entries (bot group + contacts)
        java.util.List<SidebarEntry> entries = getCachedSidebarEntries();
        int sidebarBottom = barTop - 4;
        int availableH = sidebarBottom - itemY;
        int maxItems = Math.max(0, availableH / SIDEBAR_ITEM_H);

        int maxScroll = Math.max(0, entries.size() - maxItems);
        sidebarScroll = MathHelper.clamp(sidebarScroll, 0, maxScroll);

        for (int i = sidebarScroll; i < entries.size() && i < sidebarScroll + maxItems; i++) {
            SidebarEntry entry = entries.get(i);
            int iy = itemY + (i - sidebarScroll) * SIDEBAR_ITEM_H;
            boolean hover = mouseX >= sx && mouseX <= sx + sw && mouseY >= iy && mouseY <= iy + SIDEBAR_ITEM_H;

            if (entry.kind() == 0) {
                // Group header
                if (hover)
                    context.fill(sx, iy, sx + sw, iy + SIDEBAR_ITEM_H, 0xFF333333);
                var group = ChatBubbleConfig.CONTACT_GROUPS.stream()
                    .filter(g -> g.name.equals(entry.groupName())).findFirst().orElse(null);
                String arrow = group != null && group.expanded ? "\u25BC" : "\u25B6";
                String headerText = arrow + " " + entry.groupName() + " (" + entry.count() + ")";
                context.drawText(textRenderer, Text.literal(headerText), sx + 4,
                    iy + (SIDEBAR_ITEM_H - textRenderer.fontHeight) / 2, 0xFFFFAA00, false);
            } else if (entry.kind() == 4) {
                // Hidden group header
                if (hover)
                    context.fill(sx, iy, sx + sw, iy + SIDEBAR_ITEM_H, 0xFF333333);
                String arrow = hiddenGroupExpanded ? "\u25BC" : "\u25B6";
                String headerText = arrow + " " + entry.groupName() + " (" + entry.count() + ")";
                context.drawText(textRenderer, Text.literal(headerText), sx + 4,
                    iy + (SIDEBAR_ITEM_H - textRenderer.fontHeight) / 2, 0xFF888888, false);
            } else {
                String name = entry.name();
                boolean selected = name.equals(selectedContact);
                boolean hidden = entry.kind() == 3;
                boolean isBot = entry.kind() == 1;
                boolean offline = entry.kind() == 5;
                if (selected || hover)
                    context.fill(sx, iy, sx + sw, iy + SIDEBAR_ITEM_H, selected ? 0xFF2A4A7A : 0xFF333333);
                boolean pinned = WhisperHistory.isPinned(name);
                int indent = isBot ? 8 : 0;
                int avatarSize = SIDEBAR_ITEM_H - 4;
                int avatarX = sx + 2 + indent;
                int avatarY = iy + (SIDEBAR_ITEM_H - avatarSize) / 2;
                UUID uuid = AvatarHelper.getUuidForName(name);
                if (uuid != null) {
                    AvatarHelper.renderSkin(context, uuid, avatarX, avatarY, avatarSize);
                } else {
                    context.fill(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, 0xFF333333);
                }
                int textX = avatarX + avatarSize + 3;
                int textMaxW = sx + sw - textX - (pinned ? 12 : 4);
                String display = textRenderer.trimToWidth(WhisperHistory.getDisplayName(name), textMaxW);
                int color = selected ? 0xFFFFFFFF : (hidden ? 0xFF666666 : (offline ? 0xFF777777 : (hover ? 0xFFFFFFFF : (pinned ? 0xFFFFD700 : (isBot ? 0xFFAAAAAA : 0xFFCCCCCC)))));
                context.drawText(textRenderer, Text.literal(display), textX,
                    iy + (SIDEBAR_ITEM_H - textRenderer.fontHeight) / 2, color, false);
                if (pinned) {
                    context.drawText(textRenderer, Text.literal("*"),
                        sx + sw - 10, iy + (SIDEBAR_ITEM_H - textRenderer.fontHeight) / 2, 0xFFFFD700, false);
                }
            }
        }
    }

    private void handleSidebarClick(int mx, int my) {
        int itemY = SIDEBAR_TOP;

        if (my >= itemY && my <= itemY + SIDEBAR_ITEM_H) {
            if (selectedContact != null) {
                selectedContact = null;
                resetViewState();
            }
            input.setFocused(true);
            setFocused(input);
            return;
        }
        itemY += SIDEBAR_ITEM_H + 2 + 3;

        java.util.List<SidebarEntry> entries = getCachedSidebarEntries();
        int sidebarBottom = barTop - 4;
        int availableH = sidebarBottom - itemY;
        int maxItems = Math.max(0, availableH / SIDEBAR_ITEM_H);

        for (int i = sidebarScroll; i < entries.size() && i < sidebarScroll + maxItems; i++) {
            SidebarEntry entry = entries.get(i);
            int iy = itemY + (i - sidebarScroll) * SIDEBAR_ITEM_H;
            if (my >= iy && my <= iy + SIDEBAR_ITEM_H) {
                if (entry.kind() == 0) {
                    for (var g : ChatBubbleConfig.CONTACT_GROUPS) {
                        if (g.name.equals(entry.groupName())) {
                            g.expanded = !g.expanded;
                            cachedSidebarEntries = null;
                            return;
                        }
                    }
                    return;
                }
                if (entry.kind() == 4) {
                    hiddenGroupExpanded = !hiddenGroupExpanded;
                    cachedSidebarEntries = null;
                    return;
                }
                String name = entry.name();
                if (!name.equals(selectedContact)) {
                    selectedContact = name;
                    resetViewState();
                }
                input.setFocused(true);
                setFocused(input);
                return;
            }
        }
    }

    private void handleSidebarRightClick(int mx, int my) {
        int itemY = SIDEBAR_TOP + SIDEBAR_ITEM_H + 2 + 3;
        java.util.List<SidebarEntry> entries = getCachedSidebarEntries();
        int sidebarBottom = barTop - 4;
        int availableH = sidebarBottom - itemY;
        int maxItems = Math.max(0, availableH / SIDEBAR_ITEM_H);

        for (int i = sidebarScroll; i < entries.size() && i < sidebarScroll + maxItems; i++) {
            SidebarEntry entry = entries.get(i);
            int iy = itemY + (i - sidebarScroll) * SIDEBAR_ITEM_H;
            if (my >= iy && my <= iy + SIDEBAR_ITEM_H) {
                if (entry.kind() == 0 || entry.kind() == 4) return; // group header - no context menu
                String name = entry.name();
                contextMenuContact = name;
                contextMenuContactGroupName = entry.kind() == 1 ? entry.groupName() : null;
                subMenuVisible = false;
                contextMenuX = Math.min(mx, width - CTX_MENU_W - 2);
                int menuItems = 4; // pin/hide/add-or-remove/nickname
                int menuH = CTX_ITEM_H * menuItems;
                contextMenuY = Math.min(iy, height - menuH - 2);
                return;
            }
        }
        contextMenuContact = null;
    }

    private void resetViewState() {
        scrollOffset = 0;
        maxScroll = 0;
        scrollToBottom = true;
        newMessageCount = 0;
        hasNewMentionOrQuote = false;
        latestMentionIndex = -1;
        lastSeenMessageCount = 0;
        replyTargetIndex = -1;
    }

    private void renderTitleBar(DrawContext context, int mouseX, int mouseY) {
        int ty = titleY;
        context.fill(panelX, ty, panelX + panelW, ty + TITLE_H, colorTitleBg);
        context.fill(panelX, ty + TITLE_H, panelX + panelW, ty + TITLE_H + 1, COLOR_DIVIDER);

        // Settings gear (top-left of title bar)
        int gearX = panelX + PAD;
        int gearY = ty + (TITLE_H - ICON_S) / 2;
        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= gearY && mouseY <= gearY + ICON_S;
        if (hoverGear) context.fill(gearX - 1, gearY - 1, gearX + ICON_S + 1, gearY + ICON_S + 1, 0xFF444444);
        drawTextureIcon(context, TEX_GEAR, gearX, gearY, ICON_S);

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
            boolean hoverPen = selectedContact == null && mouseX >= penX && mouseX <= penX + 9 && mouseY >= penY && mouseY <= penY + 9;
            int penColor = hoverPen ? 0xFFFFFF88 : 0xFF888888;
            if (selectedContact == null)
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
        if (selectedContact != null) {
            return Text.translatable("opchat.title.whisper").getString() + " " + selectedContact;
        }
        String ct = ChatMessageStore.getCustomTitle();
        return ct != null ? ct : worldName;
    }

    private boolean isMouseOverPen(double mx, double my) {
        if (selectedContact != null) return false;
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

    private java.util.List<ChatMessageStore.ChatMessage> getCurrentMessages() {
        return selectedContact != null
            ? WhisperHistory.getAsChatMessages(selectedContact)
            : ChatMessageStore.getMessages();
    }

    private ChatMessageStore.ChatMessage getMessageAt(int index) {
        java.util.List<ChatMessageStore.ChatMessage> msgs = getCurrentMessages();
        if (index >= 0 && index < msgs.size()) return msgs.get(index);
        return null;
    }

    private void renderMessages(DrawContext context, int mouseX, int mouseY) {
        bubbleRects.clear();
        clickableSpans.clear();
        List<ChatMessageStore.ChatMessage> messages = getCurrentMessages();
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

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - AVATAR - PAD * 2 - 20;
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
            context.drawText(textRenderer, Text.literal(replyDisplay), replyBarX + 6, replyY + 1, 0xFF999999, false);
        }

        // Draw player skin
        AvatarHelper.renderSkin(context, msg.senderUUID(), avatarX, avatarY);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = textRenderer.getWidth(label);
            // 显示在气泡右下角内侧，字体很小
            int labelX = bubbleX + bubbleW - labelW - 3;
            int labelY = bubbleY + bubbleH - textRenderer.fontHeight / 2 - 1;
            context.drawText(textRenderer, Text.literal(label), labelX, labelY, 0x99AAAAAA, false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});
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
        String ct = newMessageCount + Text.translatable("opchat.notif.new_messages").getString() + " \u25BD";
        notifCountLeft = panelX + PAD;
        notifCountRight = notifCountLeft + textRenderer.getWidth(ct);
        notifBarTextY = textY;
        boolean h = mouseX >= notifCountLeft && mouseX <= notifCountRight
            && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
        context.drawText(textRenderer, Text.literal(ct), notifCountLeft, textY, h ? 0xFFFFFF88 : yellow, false);
        if (hasNewMentionOrQuote) {
            String mt = Text.translatable("opchat.notif.mention").getString() + " \u25BD";
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
        // Contact sidebar context menu
        if (contextMenuContact != null) {
            int mx = contextMenuX, my = contextMenuY;
            int menuItems = 4; // pin/hide/add-or-remove/nickname
            int mh = CTX_ITEM_H * menuItems;
            context.fill(mx, my, mx + CTX_MENU_W, my + mh, 0xEE2A2A2A);
            context.fill(mx, my, mx + CTX_MENU_W, my + 1, COLOR_DIVIDER);
            context.fill(mx, my + mh - 1, mx + CTX_MENU_W, my + mh, COLOR_DIVIDER);
            context.fill(mx, my, mx + 1, my + mh, COLOR_DIVIDER);
            context.fill(mx + CTX_MENU_W - 1, my, mx + CTX_MENU_W, my + mh, COLOR_DIVIDER);

            boolean pinned = WhisperHistory.isPinned(contextMenuContact);
            boolean hidden = WhisperHistory.isHidden(contextMenuContact);
            String pinLabel = pinned ? "\u53d6\u6d88\u7f6e\u9876" : "\u7f6e\u9876";
            String hideLabel = hidden ? "\u53d6\u6d88\u9690\u85cf" : "\u9690\u85cf";
            String groupLabel = contextMenuContactGroupName != null
                ? "\u79fb\u51fa\u5206\u7ec4" : "\u6dfb\u52a0\u5230\u5206\u7ec4";
            String nickLabel = "\u8bbe\u7f6e\u5907\u6ce8";

            // Item 0: pin/unpin
            boolean hoverPin = mouseX >= mx && mouseX <= mx + CTX_MENU_W
                && mouseY >= my && mouseY <= my + CTX_ITEM_H;
            context.fill(mx + 1, my + 1, mx + CTX_MENU_W - 1, my + CTX_ITEM_H, hoverPin ? 0xFF4A4A4A : 0xFF3A3A3A);
            context.drawText(textRenderer, Text.literal(pinLabel), mx + 8, my + 5, 0xFFFFFFFF, false);

            context.fill(mx + 4, my + CTX_ITEM_H, mx + CTX_MENU_W - 4, my + CTX_ITEM_H + 1, 0xFF555555);

            // Item 1: hide/unhide
            boolean hoverHide = mouseX >= mx && mouseX <= mx + CTX_MENU_W
                && mouseY >= my + CTX_ITEM_H + 1 && mouseY <= my + CTX_ITEM_H * 2;
            context.fill(mx + 1, my + CTX_ITEM_H + 1, mx + CTX_MENU_W - 1, my + CTX_ITEM_H * 2 - 1, hoverHide ? 0xFF4A4A4A : 0xFF3A3A3A);
            context.drawText(textRenderer, Text.literal(hideLabel), mx + 8, my + CTX_ITEM_H + 5, 0xFFFFFFFF, false);

            context.fill(mx + 4, my + CTX_ITEM_H * 2, mx + CTX_MENU_W - 4, my + CTX_ITEM_H * 2 + 1, 0xFF555555);

            // Item 2: add to group / remove from group
            boolean hoverGroup = mouseX >= mx && mouseX <= mx + CTX_MENU_W
                && mouseY >= my + CTX_ITEM_H * 2 + 1 && mouseY <= my + CTX_ITEM_H * 3;
            if (contextMenuContactGroupName == null && hoverGroup) subMenuVisible = true;
            context.fill(mx + 1, my + CTX_ITEM_H * 2 + 1, mx + CTX_MENU_W - 1, my + CTX_ITEM_H * 3 - 1, hoverGroup ? 0xFF4A4A4A : 0xFF3A3A3A);
            context.drawText(textRenderer, Text.literal(groupLabel), mx + 8, my + CTX_ITEM_H * 2 + 5, 0xFFFFFFFF, false);
            if (contextMenuContactGroupName == null) {
                context.drawText(textRenderer, Text.literal("\u203a"), mx + CTX_MENU_W - 12, my + CTX_ITEM_H * 2 + 5, 0xFFAAAAAA, false);
            }

            context.fill(mx + 4, my + CTX_ITEM_H * 3, mx + CTX_MENU_W - 4, my + CTX_ITEM_H * 3 + 1, 0xFF555555);

            // Item 3: set nickname
            boolean hoverNick = mouseX >= mx && mouseX <= mx + CTX_MENU_W
                && mouseY >= my + CTX_ITEM_H * 3 + 1 && mouseY <= my + mh;
            context.fill(mx + 1, my + CTX_ITEM_H * 3 + 1, mx + CTX_MENU_W - 1, my + mh - 1, hoverNick ? 0xFF4A4A4A : 0xFF3A3A3A);
            context.drawText(textRenderer, Text.literal(nickLabel), mx + 8, my + CTX_ITEM_H * 3 + 5, 0xFFFFFFFF, false);

            // Submenu: list of groups + new group
            if (subMenuVisible && contextMenuContactGroupName == null) {
                int subX = mx + CTX_MENU_W;
                int subY = my + CTX_ITEM_H * 2;
                int subItems = ChatBubbleConfig.CONTACT_GROUPS.size() + 1;
                int subH = CTX_ITEM_H * subItems;
                context.fill(subX, subY, subX + CTX_SUB_W, subY + subH, 0xEE2A2A2A);
                context.fill(subX, subY, subX + CTX_SUB_W, subY + 1, COLOR_DIVIDER);
                context.fill(subX, subY + subH - 1, subX + CTX_SUB_W, subY + subH, COLOR_DIVIDER);
                context.fill(subX, subY, subX + 1, subY + subH, COLOR_DIVIDER);
                context.fill(subX + CTX_SUB_W - 1, subY, subX + CTX_SUB_W, subY + subH, COLOR_DIVIDER);

                for (int i = 0; i < ChatBubbleConfig.CONTACT_GROUPS.size(); i++) {
                    int iy = subY + i * CTX_ITEM_H;
                    boolean hover = mouseX >= subX && mouseX <= subX + CTX_SUB_W
                        && mouseY >= iy && mouseY <= iy + CTX_ITEM_H;
                    context.fill(subX + 1, iy + (i > 0 ? 1 : 0), subX + CTX_SUB_W - 1, iy + CTX_ITEM_H, hover ? 0xFF4A4A4A : 0xFF3A3A3A);
                    context.drawText(textRenderer, Text.literal(ChatBubbleConfig.CONTACT_GROUPS.get(i).name), subX + 8, iy + 5, 0xFFFFFFFF, false);
                }
                // New group item
                int ny = subY + ChatBubbleConfig.CONTACT_GROUPS.size() * CTX_ITEM_H;
                boolean hoverNew = mouseX >= subX && mouseX <= subX + CTX_SUB_W
                    && mouseY >= ny && mouseY <= ny + CTX_ITEM_H;
                context.fill(subX + 1, ny + 1, subX + CTX_SUB_W - 1, ny + CTX_ITEM_H, hoverNew ? 0xFF4A4A4A : 0xFF3A3A3A);
                context.drawText(textRenderer, Text.literal("+ \u65b0\u5efa\u5206\u7ec4"), subX + 8, ny + 5, 0xFFFFFFAA, false);
            }
            return;
        }

        if (contextMsgIndex < 0) return;
        int menuH = CTX_ITEM_H * 3 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        context.fill(menuX, menuY, menuX + CTX_W, menuY + menuH, 0xEE2A2A2A);
        context.fill(menuX, menuY, menuX + CTX_W, menuY + 1, COLOR_DIVIDER);
        context.fill(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);
        context.fill(menuX, menuY, menuX + 1, menuY + menuH, COLOR_DIVIDER);
        context.fill(menuX + CTX_W - 1, menuY, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);

        // Item 0: 引用
        int item0Y = menuY;
        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= item0Y && mouseY <= item0Y + CTX_ITEM_H;
        int quoteBg = hoverQuote ? 0xFF4A4A4A : 0xFF3A3A3A;
        context.fill(menuX + 1, item0Y + 1, menuX + CTX_W - 1, item0Y + CTX_ITEM_H, quoteBg);
        context.drawText(textRenderer, Text.translatable("opchat.context.quote"), menuX + 8, item0Y + 4, 0xFFFFFFFF, false);

        context.fill(menuX + 4, item0Y + CTX_ITEM_H, menuX + CTX_W - 4, item0Y + CTX_ITEM_H + 1, 0xFF555555);

        // Item 1: 添加到快捷发送
        int item1Y = menuY + CTX_ITEM_H + 1;
        boolean hoverAddInput = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= item1Y && mouseY <= item1Y + CTX_ITEM_H;
        int addInputBg = hoverAddInput ? 0xFF4A4A4A : 0xFF3A3A3A;
        context.fill(menuX + 1, item1Y + 1, menuX + CTX_W - 1, item1Y + CTX_ITEM_H, addInputBg);
        context.drawText(textRenderer, Text.translatable("opchat.context.add_quick_input"), menuX + 8, item1Y + 4, 0xFFFFFFFF, false);

        context.fill(menuX + 4, item1Y + CTX_ITEM_H, menuX + CTX_W - 4, item1Y + CTX_ITEM_H + 1, 0xFF555555);

        // Item 2: 添加到快捷指令
        int item2Y = menuY + (CTX_ITEM_H + 1) * 2;
        boolean hoverAddCmd = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= item2Y && mouseY <= item2Y + CTX_ITEM_H;
        int addCmdBg = hoverAddCmd ? 0xFF4A4A4A : 0xFF3A3A3A;
        context.fill(menuX + 1, item2Y + 1, menuX + CTX_W - 1, item2Y + CTX_ITEM_H, addCmdBg);
        context.drawText(textRenderer, Text.translatable("opchat.context.add_quick_command"), menuX + 8, item2Y + 4, 0xFFFFFFFF, false);
    }

    private void renderAvatarMenu(DrawContext context, int mouseX, int mouseY) {
        int mx = avatarMenuX, my = avatarMenuY;
        int menuItems = 3;
        int mh = CTX_ITEM_H * menuItems;
        context.fill(mx, my, mx + AVATAR_MENU_W, my + mh, 0xEE2A2A2A);
        context.fill(mx, my, mx + AVATAR_MENU_W, my + 1, COLOR_DIVIDER);
        context.fill(mx, my + mh - 1, mx + AVATAR_MENU_W, my + mh, COLOR_DIVIDER);
        context.fill(mx, my, mx + 1, my + mh, COLOR_DIVIDER);
        context.fill(mx + AVATAR_MENU_W - 1, my, mx + AVATAR_MENU_W, my + mh, COLOR_DIVIDER);

        // Item 0: private chat
        boolean hover0 = mouseX >= mx && mouseX <= mx + AVATAR_MENU_W
            && mouseY >= my && mouseY <= my + CTX_ITEM_H;
        context.fill(mx + 1, my + 1, mx + AVATAR_MENU_W - 1, my + CTX_ITEM_H, hover0 ? 0xFF4A4A4A : 0xFF3A3A3A);
        context.drawText(textRenderer, Text.literal("\u79c1\u804a"), mx + 8, my + 5, 0xFFFFFFFF, false);

        context.fill(mx + 4, my + CTX_ITEM_H, mx + AVATAR_MENU_W - 4, my + CTX_ITEM_H + 1, 0xFF555555);

        // Item 1: quick commands submenu
        boolean hover1 = mouseX >= mx && mouseX <= mx + AVATAR_MENU_W
            && mouseY >= my + CTX_ITEM_H + 1 && mouseY <= my + CTX_ITEM_H * 2;
        if (hover1) avatarSubMenuVisible = true;
        context.fill(mx + 1, my + CTX_ITEM_H + 1, mx + AVATAR_MENU_W - 1, my + CTX_ITEM_H * 2 - 1, hover1 ? 0xFF4A4A4A : 0xFF3A3A3A);
        context.drawText(textRenderer, Text.literal("\u5feb\u6377\u6307\u4ee4"), mx + 8, my + CTX_ITEM_H + 5, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal("\u203a"), mx + AVATAR_MENU_W - 12, my + CTX_ITEM_H + 5, 0xFFAAAAAA, false);

        context.fill(mx + 4, my + CTX_ITEM_H * 2, mx + AVATAR_MENU_W - 4, my + CTX_ITEM_H * 2 + 1, 0xFF555555);

        // Item 2: set nickname
        boolean hover2 = mouseX >= mx && mouseX <= mx + AVATAR_MENU_W
            && mouseY >= my + CTX_ITEM_H * 2 + 1 && mouseY <= my + mh;
        context.fill(mx + 1, my + CTX_ITEM_H * 2 + 1, mx + AVATAR_MENU_W - 1, my + mh - 1, hover2 ? 0xFF4A4A4A : 0xFF3A3A3A);
        context.drawText(textRenderer, Text.literal("\u8bbe\u7f6e\u5907\u6ce8"), mx + 8, my + CTX_ITEM_H * 2 + 5, 0xFFFFFFFF, false);

        // Submenu: quick commands list
        if (avatarSubMenuVisible) {
            int subX = mx + AVATAR_MENU_W;
            int subY = my + CTX_ITEM_H;
            int subItems = Math.max(getActiveQuickCommandList().size(), 1);
            int subH = CTX_ITEM_H * subItems;
            context.fill(subX, subY, subX + AVATAR_SUB_W, subY + subH, 0xEE2A2A2A);
            context.fill(subX, subY, subX + AVATAR_SUB_W, subY + 1, COLOR_DIVIDER);
            context.fill(subX, subY + subH - 1, subX + AVATAR_SUB_W, subY + subH, COLOR_DIVIDER);
            context.fill(subX, subY, subX + 1, subY + subH, COLOR_DIVIDER);
            context.fill(subX + AVATAR_SUB_W - 1, subY, subX + AVATAR_SUB_W, subY + subH, COLOR_DIVIDER);

            if (getActiveQuickCommandList().isEmpty()) {
                context.drawText(textRenderer, Text.literal("(\u65e0\u5feb\u6377\u6307\u4ee4)"), subX + 8, subY + 5, 0xFF888888, false);
            } else {
                for (int i = 0; i < getActiveQuickCommandList().size(); i++) {
                    int iy = subY + i * CTX_ITEM_H;
                    boolean hover = mouseX >= subX && mouseX <= subX + AVATAR_SUB_W
                        && mouseY >= iy && mouseY <= iy + CTX_ITEM_H;
                    context.fill(subX + 1, iy + (i > 0 ? 1 : 0), subX + AVATAR_SUB_W - 1, iy + CTX_ITEM_H, hover ? 0xFF4A4A4A : 0xFF3A3A3A);
                    String display = getActiveQuickCommandList().get(i).getDisplay();
                    String shown = textRenderer.trimToWidth(display, AVATAR_SUB_W - 12);
                    if (!shown.equals(display)) shown = textRenderer.trimToWidth(display, AVATAR_SUB_W - 18) + "...";
                    context.drawText(textRenderer, Text.literal(shown), subX + 8, iy + 5, 0xFFEEEEEE, false);
                }
            }
        }
    }

    private void renderNicknameEditor(DrawContext context, int mouseX, int mouseY) {
        int nickW = 140;
        int nickH = 36;
        int nickX = width / 2 - nickW / 2;
        int nickY = height / 2 - nickH / 2;

        context.fill(nickX, nickY, nickX + nickW, nickY + nickH, 0xEE2A2A2A);
        context.fill(nickX, nickY, nickX + nickW, nickY + 1, COLOR_DIVIDER);
        context.fill(nickX, nickY + nickH - 1, nickX + nickW, nickY + nickH, COLOR_DIVIDER);
        context.fill(nickX, nickY, nickX + 1, nickY + nickH, COLOR_DIVIDER);
        context.fill(nickX + nickW - 1, nickY, nickX + nickW, nickY + nickH, COLOR_DIVIDER);

        String target = nicknameEditTarget != null ? WhisperHistory.getDisplayName(nicknameEditTarget) : "";
        context.drawText(textRenderer, Text.literal("\u5907\u6ce8: " + target), nickX + 6, nickY + 3, 0xFFAAAAAA, false);

        int fieldX = nickX + 6;
        int fieldY = nickY + 14;
        int fieldW = nickW - 12;
        int fieldH = 16;
        context.fill(fieldX - 1, fieldY - 1, fieldX + fieldW + 1, fieldY + fieldH + 1, 0xFF555555);
        context.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, colorInputBg);
        nicknameField.setX(fieldX);
        nicknameField.setY(fieldY);
        nicknameField.setWidth(fieldW);
        nicknameField.setHeight(fieldH);
    }

    private static final int REPLY_BAR_H = 18;

    private void renderReplyBar(DrawContext context, int mouseX, int mouseY) {
        if (replyTargetIndex < 0) return;
        ChatMessageStore.ChatMessage target = getMessageAt(replyTargetIndex);
        if (target == null) { replyTargetIndex = -1; return; }

        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int slashX = panelX + PAD;
        int sendX = panelX + panelW - PAD - ICON_S;
        int barX = slashX + ICON_S + 4;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;

        context.fill(barX, barY, barX + barW, barTop - notifOffset, 0xEE1E1E1E);
        context.fill(barX, barTop - notifOffset - 1, barX + barW, barTop - notifOffset, COLOR_DIVIDER);

        String sender = target.senderName().getString();
        if (sender.isEmpty()) sender = Text.translatable("opchat.sender.system").getString();
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
        int slashX = panelX + PAD;
        int sendX = panelX + panelW - PAD - ICON_S;
        int barX = slashX + ICON_S + 4;
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
        String text = Text.translatable("opchat.toast.copied").getString();
        int tw = textRenderer.getWidth(text);
        int tx = panelX + (panelW - tw) / 2;
        int ty = msgBottom - 24;
        context.fill(tx - 6, ty - 2, tx + tw + 6, ty + textRenderer.fontHeight + 2, 0xCC000000);
        context.drawText(textRenderer, Text.literal(text), tx, ty, color, false);
    }

    private void renderBottomBar(DrawContext context, int mouseX, int mouseY) {
        context.fill(panelX, barTop, panelX + panelW, height, colorBarBg);
        context.fill(panelX, barTop, panelX + panelW, barTop + 1, COLOR_DIVIDER);

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        int slashX = panelX + PAD;
        int sendX = panelX + panelW - PAD - ICON_S;
        int ibX = slashX + ICON_S + 4;
        int ibY = barTop + (BAR_H - 20) / 2;
        int ibW = itemIconX - 6 - ibX;
        int ibH = 20;
        context.fill(ibX, ibY - 1, ibX + ibW, ibY, COLOR_DIVIDER);
        context.fill(ibX, ibY, ibX + ibW, ibY + ibH, colorInputBg);

        // / 快捷指令按钮（带背景）
        boolean hoverSlash = mouseX >= slashX && mouseX <= slashX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        int slashBg = (hoverSlash || quickCmdPanelOpen) ? 0xFF4A4A4A : 0xFF333333;
        context.fill(slashX - 1, iconY - 1, slashX + ICON_S + 1, iconY + ICON_S + 1, slashBg);
        int slashColor = (hoverSlash || quickCmdPanelOpen) ? 0xFFFFFFFF : 0xFFCCCCCC;
        int slashTextW = textRenderer.getWidth("/");
        context.drawText(textRenderer, Text.literal("/"),
            slashX + (ICON_S - slashTextW) / 2,
            iconY + (ICON_S - textRenderer.fontHeight) / 2,
            slashColor, false);

        // 快捷发送按钮（三横图标 + 背景）
        boolean hoverQuick = mouseX >= quickIconX && mouseX <= quickIconX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        int quickBg = (hoverQuick || quickPanelOpen) ? 0xFF4A4A4A : 0xFF333333;
        context.fill(quickIconX - 1, iconY - 1, quickIconX + ICON_S + 1, iconY + ICON_S + 1, quickBg);
        int quickColor = (hoverQuick || quickPanelOpen) ? 0xFFFFFFFF : 0xFFCCCCCC;
        // 三横图标
        int cx = quickIconX + ICON_S / 2;
        for (int line = 0; line < 3; line++) {
            int ly = iconY + 4 + line * 3;
            context.fill(cx - 4, ly, cx + 4, ly + 1, quickColor);
        }

        // 展示手持物品按钮（带背景）
        boolean hoverItem = mouseX >= itemIconX && mouseX <= itemIconX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        int itemBg = hoverItem ? 0xFF4A4A4A : 0xFF333333;
        context.fill(itemIconX - 1, iconY - 1, itemIconX + ICON_S + 1, iconY + ICON_S + 1, itemBg);
        int itemColor = hoverItem ? 0xFFFFFFFF : 0xFFCCCCCC;
        String itemLabel = "\u7269";
        int itemTextW = textRenderer.getWidth(itemLabel);
        context.drawText(textRenderer, Text.literal(itemLabel),
            itemIconX + (ICON_S - itemTextW) / 2,
            iconY + (ICON_S - textRenderer.fontHeight) / 2,
            itemColor, false);

        // 发送按钮（带背景）
        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        int sendBg = hoverSend ? 0xFF4A4A4A : 0xFF333333;
        context.fill(sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, sendBg);
        drawTextureIcon(context, TEX_SEND, sendX, iconY, ICON_S);
    }

    private boolean handleHistoryListClick(int mouseX, int mouseY) {
        var history = client.inGameHud.getChatHud().getMessageHistory();
        int size = history.size();
        if (size == 0) return false;

        int listX = panelX + PAD;
        int listW = panelW - 2 * PAD;
        int itemH = 14;
        int startIdx = Math.max(0, historyPos - 3);
        int endIdx = Math.min(size, startIdx + 8);
        startIdx = Math.max(0, endIdx - 8);
        int count = endIdx - startIdx;
        int listTop = barTop - (count * itemH + 6);
        if (mouseX < listX || mouseX >= listX + listW
            || mouseY < listTop + 3 || mouseY >= listTop + 3 + count * itemH) {
            return false;
        }

        int index = startIdx + (mouseY - listTop - 3) / itemH;
        if (index < startIdx || index >= endIdx) return false;
        suppressHistoryReset = true;
        input.setText(history.get(index));
        input.setCursorToEnd(false);
        suppressHistoryReset = false;
        historyPos = index;
        historyListVisible = false;
        input.setFocused(true);
        setFocused(input);
        return true;
    }

    private void renderHistoryList(DrawContext context, int mouseX, int mouseY) {
        var history = client.inGameHud.getChatHud().getMessageHistory();
        int size = history.size();
        if (size == 0) return;

        int listX = panelX + PAD;
        int listW = panelW - 2 * PAD;
        int itemH = 14;
        int maxItems = 8;

        int startIdx = Math.max(0, historyPos - 3);
        int endIdx = Math.min(size, startIdx + maxItems);
        startIdx = Math.max(0, endIdx - maxItems);
        int count = endIdx - startIdx;
        if (count <= 0) return;

        int listH = count * itemH + 6;
        int listBottom = barTop;
        int listTop = listBottom - listH;

        context.fill(listX, listTop, listX + listW, listBottom, 0xEE1A1A1A);
        context.fill(listX, listTop, listX + listW, listTop + 1, COLOR_DIVIDER);
        context.fill(listX, listBottom - 1, listX + listW, listBottom, COLOR_DIVIDER);
        context.fill(listX, listTop, listX + 1, listBottom, COLOR_DIVIDER);
        context.fill(listX + listW - 1, listTop, listX + listW, listBottom, COLOR_DIVIDER);

        int hoverIdx = getHistoryListHoverIndex(mouseX, mouseY);
        for (int i = 0; i < count; i++) {
            int idx = startIdx + i;
            int entryY = listTop + 3 + i * itemH;
            boolean isCurrent = (idx == historyPos);
            boolean isHover = (idx == hoverIdx && !isCurrent);

            if (isCurrent) {
                context.fill(listX + 2, entryY - 1, listX + listW - 2, entryY + itemH - 2, 0xFF3A5A8A);
            } else if (isHover) {
                context.fill(listX + 2, entryY - 1, listX + listW - 2, entryY + itemH - 2, 0xFF2E2E2E);
            }

            String text = history.get(idx);
            String trimmed = textRenderer.trimToWidth(text, listW - 14);
            if (!trimmed.equals(text)) trimmed = textRenderer.trimToWidth(text, listW - 20) + "...";
            int color = isCurrent ? 0xFFFFFFFF : (isHover ? 0xFFDDDDDD : 0xFFAAAAAA);
            context.drawText(textRenderer, Text.literal(trimmed), listX + 6, entryY, color, false);
        }
    }

    // 计算鼠标在历史记录列表中悬停的条目索引，不在列表内返回 -1
    private int getHistoryListHoverIndex(int mouseX, int mouseY) {
        var history = client.inGameHud.getChatHud().getMessageHistory();
        int size = history.size();
        if (size == 0) return -1;

        int listX = panelX + PAD;
        int listW = panelW - 2 * PAD;
        int itemH = 14;
        int maxItems = 8;

        int startIdx = Math.max(0, historyPos - 3);
        int endIdx = Math.min(size, startIdx + maxItems);
        startIdx = Math.max(0, endIdx - maxItems);
        int count = endIdx - startIdx;
        if (count <= 0) return -1;

        int listH = count * itemH + 6;
        int listBottom = barTop;
        int listTop = listBottom - listH;

        if (mouseX < listX || mouseX >= listX + listW
            || mouseY < listTop + 3 || mouseY >= listTop + 3 + count * itemH) {
            return -1;
        }
        int index = startIdx + (mouseY - listTop - 3) / itemH;
        if (index < startIdx || index >= endIdx) return -1;
        return index;
    }

    private void loadIconTextures() {
        loadIconTexture(TEX_GEAR, "assets/opchat/textures/gui/settings.png");
        loadIconTexture(TEX_SEND, "assets/opchat/textures/gui/send.png");
    }

    private void loadIconTexture(Identifier loc, String classpath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> loc.getPath(), img);
                client.getTextureManager().registerTexture(loc, tex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawTextureIcon(DrawContext context, Identifier tex, int x, int y, int size) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 0.0f, size, size, size, size);
    }

    // ===== 快捷输入 =====

    private int[] getQuickPanelBounds() {
        int w = QUICK_PANEL_W;
        int h = QUICK_EDIT_H + 4 + Math.max(0, ChatBubbleConfig.QUICK_INPUTS.size()) * QUICK_ITEM_H + 4;
        int x = quickIconX + ICON_S - w;
        if (x < panelX + 2) x = panelX + 2;
        int y = barTop - h - 1;
        if (y < msgTop) y = msgTop;
        return new int[]{x, y, w, h};
    }

    private void renderQuickPanel(DrawContext context, int mouseX, int mouseY) {
        int[] b = getQuickPanelBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];

        context.fill(px, py, px + pw, py + ph, 0xEE2A2A2A);
        context.fill(px, py, px + pw, py + 1, COLOR_DIVIDER);
        context.fill(px, py + ph - 1, px + pw, py + ph, COLOR_DIVIDER);
        context.fill(px, py, px + 1, py + ph, COLOR_DIVIDER);
        context.fill(px + pw - 1, py, px + pw, py + ph, COLOR_DIVIDER);

        int editY = py + 4;
        int editFieldX = px + 6;
        int editFieldW = pw - 6 - 24 - 6;
        int addX = px + pw - 24 - 4;

        quickEditField.setX(editFieldX);
        quickEditField.setY(editY + 3);
        quickEditField.setWidth(editFieldW);
        quickEditField.setHeight(14);
        quickEditField.setVisible(true);

        context.fill(editFieldX - 2, editY + 2, editFieldX + editFieldW + 2, editY + 2 + 18, colorInputBg);

        boolean hoverAdd = mouseX >= addX && mouseX <= addX + 24
            && mouseY >= editY + 2 && mouseY <= editY + 20;
        context.fill(addX, editY + 2, addX + 24, editY + 20, hoverAdd ? 0xFF4A4A4A : 0xFF3A3A3A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("+"), addX + 12, editY + 6, 0xFFFFFFFF);

        int listY = editY + QUICK_EDIT_H + 2;
        for (int i = 0; i < ChatBubbleConfig.QUICK_INPUTS.size(); i++) {
            String text = ChatBubbleConfig.QUICK_INPUTS.get(i);
            int itemX = px + 4;
            int itemY = listY + i * QUICK_ITEM_H;
            int itemW = pw - 8;
            // 布局: [拖拽手柄] [文本...] [删除x]
            int textW = itemW - DRAG_HANDLE_W - 16;
            int handleX = itemX;
            int textX = itemX + DRAG_HANDLE_W + 2;
            int delX = itemX + itemW - 14;

            boolean isDraggingThis = quickInputDragging && quickInputDragIndex == i;
            int itemBg = isDraggingThis ? 0xFF3A5A8A : 0xFF2E2E2E;
            context.fill(itemX, itemY, itemX + itemW - 1, itemY + QUICK_ITEM_H - 2, itemBg);

            // 拖拽手柄
            boolean hoverHandle = mouseX >= handleX && mouseX <= handleX + DRAG_HANDLE_W
                && mouseY >= itemY && mouseY <= itemY + QUICK_ITEM_H - 2;
            context.fill(handleX, itemY, handleX + DRAG_HANDLE_W, itemY + QUICK_ITEM_H - 2,
                hoverHandle || isDraggingThis ? 0xFF4A4A4A : 0xFF333333);
            // 三横图标
            int cx = handleX + DRAG_HANDLE_W / 2;
            for (int line = 0; line < 3; line++) {
                int ly = itemY + 5 + line * 3;
                context.fill(cx - 3, ly, cx + 3, ly + 1, 0xFFAAAAAA);
            }

            boolean hoverItem = mouseX >= textX && mouseX <= delX
                && mouseY >= itemY && mouseY <= itemY + QUICK_ITEM_H - 2;

            String display = textRenderer.trimToWidth(text, textW);
            if (!display.equals(text)) display = textRenderer.trimToWidth(text, textW - 6) + "...";
            context.drawText(textRenderer, Text.literal(display), textX, itemY + 4, 0xFFEEEEEE, false);

            boolean hoverDel = mouseX >= delX && mouseX <= delX + 12
                && mouseY >= itemY && mouseY <= itemY + QUICK_ITEM_H - 2;
            context.fill(delX, itemY, delX + 12, itemY + QUICK_ITEM_H - 2, hoverDel ? 0xFF553333 : 0xFF333333);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"), delX + 6, itemY + 4, 0xFFCCCCCC);
        }
    }

    private void handleQuickPanelClick(int mx, int my) {
        int[] b = getQuickPanelBounds();
        int px = b[0], py = b[1], pw = b[2];

        int editY = py + 4;
        int editFieldX = px + 6;
        int editFieldW = pw - 6 - 24 - 6;
        int addX = px + pw - 24 - 4;

        if (mx >= editFieldX - 2 && mx <= editFieldX + editFieldW + 2
            && my >= editY + 2 && my <= editY + 20) {
            quickEditField.setFocused(true);
            setFocused(quickEditField);
            return;
        }

        if (mx >= addX && mx <= addX + 24 && my >= editY + 2 && my <= editY + 20) {
            addQuickInputFromField();
            return;
        }

        int listY = editY + QUICK_EDIT_H + 2;
        for (int i = 0; i < ChatBubbleConfig.QUICK_INPUTS.size(); i++) {
            int itemX = px + 4;
            int itemY = listY + i * QUICK_ITEM_H;
            int itemW = pw - 8;
            int handleX = itemX;
            int textX = itemX + DRAG_HANDLE_W + 2;
            int delX = itemX + itemW - 14;

            // 拖拽手柄：开始拖拽
            if (mx >= handleX && mx <= handleX + DRAG_HANDLE_W && my >= itemY && my <= itemY + QUICK_ITEM_H - 2) {
                quickInputDragging = true;
                quickInputDragIndex = i;
                return;
            }

            if (mx >= delX && mx <= delX + 12 && my >= itemY && my <= itemY + QUICK_ITEM_H - 2) {
                ChatBubbleConfig.QUICK_INPUTS.remove(i);
                ChatBubbleConfig.save();
                return;
            }

            if (mx >= textX && mx <= delX && my >= itemY && my <= itemY + QUICK_ITEM_H - 2) {
                sendQuickInput(ChatBubbleConfig.QUICK_INPUTS.get(i));
                return;
            }
        }

        quickEditField.setFocused(false);
    }

    private void toggleQuickPanel() {
        if (quickPanelOpen) {
            closeQuickPanel();
        } else {
            quickPanelOpen = true;
            quickEditField.setText("");
            quickEditField.setVisible(true);
            quickEditField.setFocused(true);
            setFocused(quickEditField);
        }
    }

    private void closeQuickPanel() {
        quickPanelOpen = false;
        quickEditField.setVisible(false);
        quickEditField.setFocused(false);
        input.setFocused(true);
        setFocused(input);
    }

    private void addQuickInputFromField() {
        String text = quickEditField.getText().trim();
        if (text.isEmpty()) return;
        if (!ChatBubbleConfig.QUICK_INPUTS.contains(text)) {
            ChatBubbleConfig.QUICK_INPUTS.add(text);
            ChatBubbleConfig.save();
        }
        quickEditField.setText("");
    }

    private void sendQuickInput(String text) {
        closeQuickPanel();
        boolean isWhisper = selectedContact != null;
        if (isWhisper) {
            if (!isContactOnline(selectedContact)) {
                WhisperHistory.addSystemMessage(selectedContact, "\u5bf9\u65b9\u5df2\u79bb\u7ebf");
                return;
            }
            client.player.networkHandler.sendChatCommand("w " + selectedContact + " " + text);
            WhisperHistory.addOutgoing(selectedContact, text);
        } else if (text.startsWith("/"))
            client.player.networkHandler.sendChatCommand(text.substring(1));
        else
            client.player.networkHandler.sendChatMessage(text);
        addToHistoryAndDeduplicate(text);

        if (!isWhisper) {
            ChatMessageStore.addMessage(Text.literal(text),
                client.player.getUuid(),
                Text.literal(client.player.getName().getString()),
                false);
        }
        ChatMessageStore.incrementPendingEcho(text);

        scrollToBottom = true;
    }

    // ===== 快捷指令 (/) =====

    private String getCurrentModeKey() {
        if (client.interactionManager == null) return null;
        GameMode mode = client.interactionManager.getCurrentGameMode();
        return switch (mode) {
            case CREATIVE -> "creative";
            case SURVIVAL -> "survival";
            case SPECTATOR -> "spectator";
            case ADVENTURE -> "adventure";
            default -> null;
        };
    }

    private List<ChatBubbleConfig.QuickCommand> getActiveQuickCommandList() {
        return ChatBubbleConfig.getActiveQuickCommands(getCurrentModeKey());
    }

    private int[] getQuickCmdPanelBounds() {
        int w = QUICK_CMD_PANEL_W;
        int headerH = 22;
        int listH = Math.max(0, getActiveQuickCommandList().size()) * QUICK_CMD_ITEM_H;
        int footerH = 6;
        int h = headerH + listH + footerH;
        if (quickCmdEditing) h += QUICK_CMD_FORM_H;
        int x = slashIconX;
        if (x + w > panelX + panelW - 2) x = panelX + panelW - w - 2;
        if (x < panelX + 2) x = panelX + 2;
        int y = barTop - h - 1;
        if (y < msgTop) y = msgTop;
        return new int[]{x, y, w, h};
    }

    private void renderQuickCmdPanel(DrawContext context, int mouseX, int mouseY) {
        int[] b = getQuickCmdPanelBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];

        context.fill(px, py, px + pw, py + ph, 0xEE2A2A2A);
        context.fill(px, py, px + pw, py + 1, COLOR_DIVIDER);
        context.fill(px, py + ph - 1, px + pw, py + ph, COLOR_DIVIDER);
        context.fill(px, py, px + 1, py + ph, COLOR_DIVIDER);
        context.fill(px + pw - 1, py, px + pw, py + ph, COLOR_DIVIDER);

        int headerY = py + 4;
        String headerText = "\u5feb\u6377\u6307\u4ee4";
        if (ChatBubbleConfig.MULTI_MODE_COMMANDS) {
            String modeKey = getCurrentModeKey();
            if (modeKey != null) {
                String modeName = switch (modeKey) {
                    case "creative" -> "\u521b\u9020";
                    case "survival" -> "\u751f\u5b58";
                    case "spectator" -> "\u65c1\u89c2";
                    case "adventure" -> "\u5192\u9669";
                    default -> "\u5171\u7528";
                };
                headerText += " [" + modeName + "]";
            }
        }
        context.drawText(textRenderer, Text.literal(headerText), px + 8, headerY + 2, 0xFFFFAA00, false);

        int addX = px + pw - 32 - 4;
        int addY = headerY;
        int addW = 32;
        int addH = 16;
        boolean hoverAdd = mouseX >= addX && mouseX <= addX + addW && mouseY >= addY && mouseY <= addY + addH;
        context.fill(addX, addY, addX + addW, addY + addH, hoverAdd ? 0xFF4A4A4A : 0xFF3A3A3A);
        String addLabel = quickCmdEditing ? "\u53d6\u6d88" : "+ \u6dfb\u52a0";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(addLabel), addX + addW / 2, addY + 4, 0xFFFFFFFF);

        int listY = headerY + 22;
        for (int i = 0; i < getActiveQuickCommandList().size(); i++) {
            var cmd = getActiveQuickCommandList().get(i);
            int itemX = px + 4;
            int itemY = listY + i * QUICK_CMD_ITEM_H;
            int itemW = pw - 8;
            // 布局: [拖拽手柄] [文本] [编辑] [删除x]
            int textW = itemW - DRAG_HANDLE_W - 28;
            int handleX = itemX;
            int textStartX = itemX + DRAG_HANDLE_W + 2;

            boolean isDraggingThis = quickCmdDragging && quickCmdDragIndex == i;
            int itemBg = isDraggingThis ? 0xFF3A5A8A : 0xFF2E2E2E;
            context.fill(itemX, itemY, itemX + itemW - 1, itemY + QUICK_CMD_ITEM_H - 2, itemBg);

            // 拖拽手柄
            boolean hoverHandle = mouseX >= handleX && mouseX <= handleX + DRAG_HANDLE_W
                && mouseY >= itemY && mouseY <= itemY + QUICK_CMD_ITEM_H - 2;
            context.fill(handleX, itemY, handleX + DRAG_HANDLE_W, itemY + QUICK_CMD_ITEM_H - 2,
                hoverHandle || isDraggingThis ? 0xFF4A4A4A : 0xFF333333);
            int cx = handleX + DRAG_HANDLE_W / 2;
            for (int line = 0; line < 3; line++) {
                int ly = itemY + 5 + line * 3;
                context.fill(cx - 3, ly, cx + 3, ly + 1, 0xFFAAAAAA);
            }

            String display = cmd.getDisplay();
            String shown = textRenderer.trimToWidth(display, textW - 8);
            if (!shown.equals(display)) shown = textRenderer.trimToWidth(display, textW - 14) + "...";
            context.drawText(textRenderer, Text.literal(shown), textStartX, itemY + 4, 0xFFEEEEEE, false);

            int editX = itemX + itemW - 28;
            int editW = 12;
            boolean hoverEdit = mouseX >= editX && mouseX <= editX + editW && mouseY >= itemY && mouseY <= itemY + QUICK_CMD_ITEM_H - 2;
            context.fill(editX, itemY, editX + editW, itemY + QUICK_CMD_ITEM_H - 2, hoverEdit ? 0xFF444444 : 0xFF333333);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u270E"), editX + editW / 2, itemY + 4, 0xFFCCCCCC);

            int delX = editX + editW + 2;
            int delW = 12;
            boolean hoverDel = mouseX >= delX && mouseX <= delX + delW && mouseY >= itemY && mouseY <= itemY + QUICK_CMD_ITEM_H - 2;
            context.fill(delX, itemY, delX + delW, itemY + QUICK_CMD_ITEM_H - 2, hoverDel ? 0xFF553333 : 0xFF333333);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"), delX + delW / 2, itemY + 4, 0xFFCCCCCC);
        }

        if (quickCmdEditing) {
            int formY = listY + getActiveQuickCommandList().size() * QUICK_CMD_ITEM_H + 4;
            renderQuickCmdForm(context, mouseX, mouseY, px, formY, pw);
        }
    }

    private void renderQuickCmdForm(DrawContext context, int mouseX, int mouseY, int px, int formY, int pw) {
        context.fill(px + 4, formY - 2, px + pw - 4, formY - 1, COLOR_DIVIDER);

        int dispLabelY = formY + 2;
        context.drawText(textRenderer, Text.literal("\u663e\u793a\u540d"), px + 8, dispLabelY, 0xFF999999, false);
        int dispFieldX = px + 8;
        int dispFieldY = dispLabelY + 10;
        int dispFieldW = pw - 16;
        int dispFieldH = 16;
        context.fill(dispFieldX - 1, dispFieldY - 1, dispFieldX + dispFieldW + 1, dispFieldY + dispFieldH + 1, 0xFF555555);
        context.fill(dispFieldX, dispFieldY, dispFieldX + dispFieldW, dispFieldY + dispFieldH, colorInputBg);
        quickCmdDisplayField.setX(dispFieldX);
        quickCmdDisplayField.setY(dispFieldY);
        quickCmdDisplayField.setWidth(dispFieldW);
        quickCmdDisplayField.setHeight(dispFieldH);
        quickCmdDisplayField.setVisible(true);

        int cmdLabelY = dispFieldY + dispFieldH + 4;
        context.drawText(textRenderer, Text.literal("\u6307\u4ee4"), px + 8, cmdLabelY, 0xFF999999, false);
        int cmdFieldX = px + 8;
        int cmdFieldY = cmdLabelY + 10;
        int varBtnW = 28;
        int cmdFieldW = pw - 16 - varBtnW - 4;
        int cmdFieldH = 16;
        context.fill(cmdFieldX - 1, cmdFieldY - 1, cmdFieldX + cmdFieldW + 1, cmdFieldY + cmdFieldH + 1, 0xFF555555);
        context.fill(cmdFieldX, cmdFieldY, cmdFieldX + cmdFieldW, cmdFieldY + cmdFieldH, colorInputBg);
        quickCmdCommandField.setX(cmdFieldX);
        quickCmdCommandField.setY(cmdFieldY);
        quickCmdCommandField.setWidth(cmdFieldW);
        quickCmdCommandField.setHeight(cmdFieldH);
        quickCmdCommandField.setVisible(true);
        proxyCmdField.setX(cmdFieldX);
        proxyCmdField.setY(cmdFieldY);
        proxyCmdField.setWidth(cmdFieldW);
        proxyCmdField.setHeight(cmdFieldH);

        int varBtnX = cmdFieldX + cmdFieldW + 4;
        boolean hoverVar = mouseX >= varBtnX && mouseX <= varBtnX + varBtnW && mouseY >= cmdFieldY && mouseY <= cmdFieldY + cmdFieldH;
        context.fill(varBtnX, cmdFieldY, varBtnX + varBtnW, cmdFieldY + cmdFieldH, hoverVar ? 0xFF4A4A4A : 0xFF3A3A3A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("@*"), varBtnX + varBtnW / 2, cmdFieldY + 4, 0xFFFFFFAA);

        int saveY = cmdFieldY + cmdFieldH + 6;
        int btnW = (pw - 20) / 2;
        int saveX = px + 8;
        boolean hoverSave = mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= saveY && mouseY <= saveY + 16;
        context.fill(saveX, saveY, saveX + btnW, saveY + 16, hoverSave ? 0xFF4A7A4A : 0xFF3A5A3A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u4fdd\u5b58"), saveX + btnW / 2, saveY + 4, 0xFFFFFFFF);

        int cancelX = saveX + btnW + 4;
        boolean hoverCancel = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= saveY && mouseY <= saveY + 16;
        context.fill(cancelX, saveY, cancelX + btnW, saveY + 16, hoverCancel ? 0xFF7A4A4A : 0xFF5A3A3A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u53d6\u6d88"), cancelX + btnW / 2, saveY + 4, 0xFFFFFFFF);
    }

    private void handleQuickCmdPanelClick(int mx, int my) {
        int[] b = getQuickCmdPanelBounds();
        int px = b[0], py = b[1], pw = b[2];

        int headerY = py + 4;
        int addX = px + pw - 32 - 4;
        int addY = headerY;
        int addW = 32;
        int addH = 16;

        if (mx >= addX && mx <= addX + addW && my >= addY && my <= addY + addH) {
            if (quickCmdEditing) {
                quickCmdEditing = false;
                quickCmdDisplayField.setVisible(false);
                quickCmdCommandField.setVisible(false);
                if (quickCmdSuggestor != null) quickCmdSuggestor.clearWindow();
                input.setFocused(true);
                setFocused(input);
            } else {
                quickCmdEditing = true;
                quickCmdEditIndex = -1;
                quickCmdDisplayField.setText("");
                quickCmdCommandField.setText("");
                quickCmdDisplayField.setVisible(true);
                quickCmdCommandField.setVisible(true);
                quickCmdCommandField.setFocused(true);
                setFocused(quickCmdCommandField);
            }
            return;
        }

        int listY = headerY + 22;
        for (int i = 0; i < getActiveQuickCommandList().size(); i++) {
            int itemX = px + 4;
            int itemY = listY + i * QUICK_CMD_ITEM_H;
            int itemW = pw - 8;
            int handleX = itemX;
            int editX = itemX + itemW - 28;
            int editW = 12;
            int delX = editX + editW + 2;
            int delW = 12;

            // 拖拽手柄
            if (mx >= handleX && mx <= handleX + DRAG_HANDLE_W && my >= itemY && my <= itemY + QUICK_CMD_ITEM_H - 2) {
                quickCmdDragging = true;
                quickCmdDragIndex = i;
                return;
            }

            if (mx >= delX && mx <= delX + delW && my >= itemY && my <= itemY + QUICK_CMD_ITEM_H - 2) {
                getActiveQuickCommandList().remove(i);
                ChatBubbleConfig.save();
                return;
            }

            if (mx >= editX && mx <= editX + editW && my >= itemY && my <= itemY + QUICK_CMD_ITEM_H - 2) {
                var cmd = getActiveQuickCommandList().get(i);
                quickCmdEditing = true;
                quickCmdEditIndex = i;
                quickCmdDisplayField.setText(cmd.display);
                quickCmdCommandField.setText(cmd.command);
                quickCmdDisplayField.setVisible(true);
                quickCmdCommandField.setVisible(true);
                quickCmdCommandField.setFocused(true);
                setFocused(quickCmdCommandField);
                return;
            }

            // 文本区域点击（排除手柄、编辑、删除）
            int textEndX = editX;
            int textStartX = handleX + DRAG_HANDLE_W + 2;
            if (mx >= textStartX && mx <= textEndX && my >= itemY && my <= itemY + QUICK_CMD_ITEM_H - 2) {
                executeQuickCommand(getActiveQuickCommandList().get(i));
                return;
            }
        }

        if (quickCmdEditing) {
            int formY = listY + getActiveQuickCommandList().size() * QUICK_CMD_ITEM_H + 4;
            handleQuickCmdFormClick(mx, my, px, formY, pw);
            return;
        }

        quickCmdDisplayField.setFocused(false);
        quickCmdCommandField.setFocused(false);
    }

    private void handleQuickCmdFormClick(int mx, int my, int px, int formY, int pw) {
        int dispLabelY = formY + 2;
        int dispFieldX = px + 8;
        int dispFieldY = dispLabelY + 10;
        int dispFieldW = pw - 16;
        int dispFieldH = 16;

        if (mx >= dispFieldX && mx <= dispFieldX + dispFieldW && my >= dispFieldY && my <= dispFieldY + dispFieldH) {
            quickCmdDisplayField.setFocused(true);
            setFocused(quickCmdDisplayField);
            return;
        }

        int cmdLabelY = dispFieldY + dispFieldH + 4;
        int cmdFieldX = px + 8;
        int cmdFieldY = cmdLabelY + 10;
        int varBtnW = 28;
        int cmdFieldW = pw - 16 - varBtnW - 4;
        int cmdFieldH = 16;

        if (mx >= cmdFieldX && mx <= cmdFieldX + cmdFieldW && my >= cmdFieldY && my <= cmdFieldY + cmdFieldH) {
            quickCmdCommandField.setFocused(true);
            setFocused(quickCmdCommandField);
            if (quickCmdSuggestor != null) {
                String t = quickCmdCommandField.getText();
                quickCmdSuggestor.setWindowActive(t.startsWith("/"));
                quickCmdSuggestor.refresh();
            }
            return;
        }

        int varBtnX = cmdFieldX + cmdFieldW + 4;
        if (mx >= varBtnX && mx <= varBtnX + varBtnW && my >= cmdFieldY && my <= cmdFieldY + cmdFieldH) {
            String cur = quickCmdCommandField.getText();
            quickCmdCommandField.setText(cur + "@*");
            quickCmdCommandField.setCursorToEnd(false);
            quickCmdCommandField.setFocused(true);
            setFocused(quickCmdCommandField);
            return;
        }

        int saveY = cmdFieldY + cmdFieldH + 6;
        int btnW = (pw - 20) / 2;
        int saveX = px + 8;
        if (mx >= saveX && mx <= saveX + btnW && my >= saveY && my <= saveY + 16) {
            saveQuickCommand();
            return;
        }

        int cancelX = saveX + btnW + 4;
        if (mx >= cancelX && mx <= cancelX + btnW && my >= saveY && my <= saveY + 16) {
            quickCmdEditing = false;
            quickCmdDisplayField.setVisible(false);
            quickCmdCommandField.setVisible(false);
            if (quickCmdSuggestor != null) quickCmdSuggestor.clearWindow();
            input.setFocused(true);
            setFocused(input);
            return;
        }

        quickCmdDisplayField.setFocused(false);
        quickCmdCommandField.setFocused(false);
    }

    private void saveQuickCommand() {
        String display = quickCmdDisplayField.getText().trim();
        String command = quickCmdCommandField.getText().trim();
        if (command.isEmpty()) {
            quickCmdEditing = false;
            quickCmdDisplayField.setVisible(false);
            quickCmdCommandField.setVisible(false);
            if (quickCmdSuggestor != null) quickCmdSuggestor.clearWindow();
            input.setFocused(true);
            setFocused(input);
            return;
        }

        if (quickCmdEditIndex >= 0 && quickCmdEditIndex < getActiveQuickCommandList().size()) {
            var cmd = getActiveQuickCommandList().get(quickCmdEditIndex);
            cmd.display = display;
            cmd.command = command;
        } else {
            ChatBubbleConfig.getOrCreateModeCommands(getCurrentModeKey()).add(new ChatBubbleConfig.QuickCommand(display, command));
        }
        ChatBubbleConfig.save();

        quickCmdEditing = false;
        quickCmdEditIndex = -1;
        quickCmdDisplayField.setVisible(false);
        quickCmdCommandField.setVisible(false);
        quickCmdDisplayField.setText("");
        quickCmdCommandField.setText("");
        if (quickCmdSuggestor != null) quickCmdSuggestor.clearWindow();
        input.setFocused(true);
        setFocused(input);
    }

    private void toggleQuickCmdPanel() {
        if (quickCmdPanelOpen) {
            closeQuickCmdPanel();
        } else {
            quickCmdPanelOpen = true;
            quickCmdEditing = false;
            quickCmdDisplayField.setVisible(false);
            quickCmdCommandField.setVisible(false);
            // 不预输入 /，也不激活命令提示；用户手动编辑输入框后才弹出补全
            if (commandSuggestions != null) {
                commandSuggestions.setWindowActive(false);
            }
            input.setFocused(true);
            setFocused(input);
        }
    }

    // 从右键菜单"添加到快捷指令"打开编辑表单，预填充消息内容
    private void openQuickCmdEditorWith(String presetText) {
        if (!quickCmdPanelOpen) {
            quickCmdPanelOpen = true;
        }
        quickCmdEditing = true;
        quickCmdEditIndex = -1;
        quickCmdDisplayField.setText("");
        quickCmdCommandField.setText(presetText);
        quickCmdDisplayField.setVisible(true);
        quickCmdCommandField.setVisible(true);
        quickCmdCommandField.setFocused(true);
        setFocused(quickCmdCommandField);
        if (quickCmdSuggestor != null) {
            quickCmdSuggestor.setWindowActive(presetText.startsWith("/"));
            quickCmdSuggestor.refresh();
        }
    }

    private void closeQuickCmdPanel() {
        quickCmdPanelOpen = false;
        quickCmdEditing = false;
        quickCmdDisplayField.setVisible(false);
        quickCmdCommandField.setVisible(false);
        if (quickCmdSuggestor != null) quickCmdSuggestor.clearWindow();
        input.setFocused(true);
        setFocused(input);
    }

    private void executeQuickCommand(ChatBubbleConfig.QuickCommand cmd) {
        String command = cmd.command;
        closeQuickCmdPanel();

        // Public chat with @*: place command in input box and trigger suggestions
        // instead of sending, so the user can pick a player name from completions.
        if (selectedContact == null && command.contains("@*")) {
            int idx = command.indexOf("@*");
            String before = command.substring(0, idx);
            String after = command.substring(idx + 2).replace("@*", "");
            input.setText(before + after);
            input.setFocused(true);
            setFocused(input);
            input.setCursorToEnd(false);
            if (command.startsWith("/")) {
                commandSuggestions.setWindowActive(true);
                commandSuggestions.refresh();
            }
            return;
        }

        // Replace @* with current whisper target if present
        if (selectedContact != null) {
            command = command.replace("@*", selectedContact);
        }

        // Quick commands are always executed as server commands, never routed through whisper text
        if (command.startsWith("/")) {
            client.player.networkHandler.sendChatCommand(command.substring(1));
        } else {
            client.player.networkHandler.sendChatMessage(command);
        }
        addToHistoryAndDeduplicate(command);

        // Show the command in the current chat history
        if (client.player != null) {
            Text cmdText = Text.literal(command);
            if (selectedContact != null) {
                WhisperHistory.addOutgoing(selectedContact, command);
            } else {
                ChatMessageStore.addMessage(cmdText, client.player.getUuid(), client.player.getName(), false);
            }
            scrollToBottom = true;
        }
    }

    private void executeQuickCommandForPlayer(ChatBubbleConfig.QuickCommand cmd, String player) {
        String command = cmd.command;
        if (player != null) {
            command = command.replace("@*", player);
        }
        if (command.startsWith("/")) {
            client.player.networkHandler.sendChatCommand(command.substring(1));
        } else {
            client.player.networkHandler.sendChatMessage(command);
        }
        addToHistoryAndDeduplicate(command);
        if (client.player != null) {
            ChatMessageStore.addMessage(Text.literal(command), client.player.getUuid(), client.player.getName(), false);
            scrollToBottom = true;
        }
    }

    private void openNicknameEditor(String target) {
        nicknameEditTarget = target;
        String current = WhisperHistory.getNickname(target);
        nicknameField.setText(current != null ? current : "");
        nicknameField.setVisible(true);
        nicknameField.setFocused(true);
        setFocused(nicknameField);
        editingNickname = true;
    }

    private void saveNickname() {
        String nick = nicknameField.getText().trim();
        if (nicknameEditTarget != null) {
            WhisperHistory.setNickname(nicknameEditTarget, nick);
        }
        editingNickname = false;
        nicknameField.setVisible(false);
        input.setFocused(true);
        setFocused(input);
    }

    private void jumpToMessage(int msgIndex) {
        var msgs = getCurrentMessages();
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

    private boolean isContactOnline(String name) {
        if (client.getNetworkHandler() == null) return false;
        for (var entry : client.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().name().equals(name)) return true;
        }
        return false;
    }

    private void sendMessage() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;

        if (replyTargetIndex >= 0) {
            ChatMessageStore.ChatMessage target = getMessageAt(replyTargetIndex);
            if (target != null) {
                ChatMessageStore.setPendingReply(target.content().getString(), target.senderName().getString());
                QuoteSyncPacket.send(target.senderName().getString(), target.content().getString(), text);
            }
            replyTargetIndex = -1;
        }

        boolean isWhisper = selectedContact != null;
        if (isWhisper) {
            if (!isContactOnline(selectedContact)) {
                WhisperHistory.addSystemMessage(selectedContact, "\u5bf9\u65b9\u5df2\u79bb\u7ebf");
                input.setText("");
                scrollToBottom = true;
                return;
            }
            client.player.networkHandler.sendChatCommand("w " + selectedContact + " " + text);
            WhisperHistory.addOutgoing(selectedContact, text);
        } else if (text.startsWith("/"))
            client.player.networkHandler.sendChatCommand(text.substring(1));
        else
            client.player.networkHandler.sendChatMessage(text);
        addToHistoryAndDeduplicate(text);

        if (!isWhisper) {
            ChatMessageStore.addMessage(Text.literal(text),
                client.player.getUuid(),
                Text.literal(client.player.getName().getString()),
                false);
        }
        ChatMessageStore.incrementPendingEcho(text);

        input.setText("");
        scrollToBottom = true;
    }

    // 展示手持物品
    // 策略：
    //   1. 单人游戏 → 用集成服务器的命令派发器执行 tellraw（带 hover 物品 tooltip）
    //   2. 多人游戏 → 发送 [item] 占位符到聊天
    //      （服务器若装了 Showcase / ChatItem / EssentialsX Chat 等插件会自动替换为
    //       带 hover 的物品展示；否则其他玩家只看到 "[item]" 文本）
    // 说明：1.19.1+ 协议禁止客户端发送带 hover_event 的富文本聊天消息，
    //      这是 Minecraft 设计限制，不是 OP 权限问题。要支持多人 hover 必须服务器侧装 mod。
    private void sendItemShowcase() {
        if (client.player == null) return;
        var mainHand = client.player.getMainHandStack();
        if (mainHand.isEmpty()) {
            return;
        }

        String playerName = client.player.getName().getString();
        String itemDisplay = mainHand.getName().getString();

        // 单人游戏：用 dispatcher 直接执行 tellraw（无长度/权限限制，带 hover）
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            try {
                var stackNbt = encodeItemStackNbt(mainHand);
                if (stackNbt != null) {
                    String snbt = buildTellrawSnbt(playerName, itemDisplay, stackNbt);
                    var server = client.getServer();
                    var source = server.getCommandSource();
                    var dispatcher = server.getCommandManager().getDispatcher();
                    dispatcher.execute("tellraw @a " + snbt, source);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 多人游戏：发送 [item] 占位符
        // 服务器装了 Showcase / ChatItem / EssentialsX Chat 等插件会自动替换为带 hover 的物品展示
        // 如果服务器没有任何支持，玩家只会看到 "[item]" 文本（但仍知道你在展示物品）
        client.player.networkHandler.sendChatMessage("[item]");
    }

    // 将 ItemStack 序列化为 NbtCompound（包含 id/count/components）
    private net.minecraft.nbt.NbtCompound encodeItemStackNbt(net.minecraft.item.ItemStack stack) {
        var result = net.minecraft.item.ItemStack.OPTIONAL_CODEC.encodeStart(
            net.minecraft.nbt.NbtOps.INSTANCE, stack);
        var opt = result.result();
        if (opt.isEmpty() || !(opt.get() instanceof net.minecraft.nbt.NbtCompound nbt)) return null;
        return nbt;
    }

    // 构建 tellraw 消息的 SNBT（本地执行版本，用于 dispatcher）
    private String buildTellrawSnbt(String playerName, String itemDisplay, net.minecraft.nbt.NbtCompound stackNbt) {
        net.minecraft.nbt.NbtList root = new net.minecraft.nbt.NbtList();

        net.minecraft.nbt.NbtCompound part1 = new net.minecraft.nbt.NbtCompound();
        part1.putString("text", "[" + playerName + "] ");
        part1.putString("color", "gray");
        root.add(part1);

        net.minecraft.nbt.NbtCompound part2 = new net.minecraft.nbt.NbtCompound();
        part2.putString("text", "\u5c55\u793a\u4e86 ");
        part2.putString("color", "gray");
        root.add(part2);

        net.minecraft.nbt.NbtCompound part3 = new net.minecraft.nbt.NbtCompound();
        part3.putString("text", "[" + itemDisplay + "]");
        part3.putString("color", "aqua");

        net.minecraft.nbt.NbtCompound hoverEvent = new net.minecraft.nbt.NbtCompound();
        hoverEvent.putString("action", "show_item");
        for (String key : stackNbt.getKeys()) {
            var el = stackNbt.get(key);
            if (el != null) hoverEvent.put(key, el);
        }
        part3.put("hover_event", hoverEvent);
        root.add(part3);

        return root.toString();
    }

    private void moveInHistory(int delta) {
        var history = client.inGameHud.getChatHud().getMessageHistory();
        int size = history.size();
        int newPos = MathHelper.clamp(historyPos + delta, 0, size);
        if (newPos != historyPos) {
            if (newPos == size) {
                historyPos = size;
                suppressHistoryReset = true;
                input.setText(historyBuffer);
                suppressHistoryReset = false;
            } else {
                if (historyPos == size) historyBuffer = input.getText();
                suppressHistoryReset = true;
                input.setText(history.get(newPos));
                suppressHistoryReset = false;
                historyPos = newPos;
            }
            historyListVisible = true;
        }
    }

    // 添加到发送历史，并对相同命令去重：仅保留每个命令最新发送的一条
    private void addToHistoryAndDeduplicate(String text) {
        client.inGameHud.getChatHud().addToMessageHistory(text);
        var history = client.inGameHud.getChatHud().getMessageHistory();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!seen.add(history.get(i))) {
                history.remove(i);
            }
        }
    }

    @Override
    public void removed() {
        ChatMessageStore.setScreenOpen(false);
        client.inGameHud.getChatHud().resetScroll();
        quickPanelOpen = false;
        quickCmdPanelOpen = false;
        avatarMenuPlayer = null;
        avatarSubMenuVisible = false;
        editingNickname = false;
        ChatBubbleConfig.save();
        // 关闭聊天框：禁用输入法（回到游戏内）
        com.opchat.ime.IMEBlocker.disableIME();
    }

    @Override
    public void close() {
        if (quickPanelOpen) {
            closeQuickPanel();
            return;
        }
        if (quickCmdPanelOpen) {
            closeQuickCmdPanel();
            return;
        }
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
