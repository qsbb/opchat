package com.opchat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;
    private static final int LEFT_W = 120;
    private static final int LEFT_ITEM_H = 22;
    private static final int LABEL_X_OFFSET = 20;
    private static final int INPUT_X_OFFSET = 175;
    private static final int INPUT_W = 130;
    private static final int PREVIEW_X_OFFSET = 315;
    private static final int ROW_H = 28;
    private static final int START_Y = 50;

    private int selectedCategory = 0;
    private static final String[] CATEGORIES = {
        "\u57fa\u7840", "\u6d88\u606f\u63d0\u793a", "\u6d88\u606f\u9884\u89c8",
        "\u5916\u89c2", "\u79c1\u804a\u4e0e\u5386\u53f2", "\u5feb\u6377\u6307\u4ee4",
        "\u517c\u5bb9\u6027", "\u8054\u7cfb\u4eba\u5206\u7ec4"
    };

    private final List<ClickableWidget> rightWidgets = new ArrayList<>();
    private final List<RenderEntry> renderEntries = new ArrayList<>();

    private record RenderEntry(int y, String text, int kind, Supplier<String> colorSupplier) {}
    // kind: 0=section_title, 1=label, 2=label_with_color_preview

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Text.translatable("opchat.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        rightWidgets.clear();
        renderEntries.clear();
        int leftX = 0;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            int itemY = START_Y + i * LEFT_ITEM_H;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(CATEGORIES[i]), b -> {
                selectedCategory = idx;
                clearChildren();
                init();
            }).dimensions(leftX + 2, itemY, LEFT_W - 4, LEFT_ITEM_H - 2).build();
            btn.active = (i != selectedCategory);
            addDrawableChild(btn);
        }

        int rightBase = LEFT_W + LABEL_X_OFFSET;
        int inputX = LEFT_W + INPUT_X_OFFSET;
        int previewX = LEFT_W + PREVIEW_X_OFFSET;
        int y = START_Y;

        switch (selectedCategory) {
            case 0 -> {
                y = addSection(y, rightBase, "\u57fa\u7840\u8bbe\u7f6e");
                y = addToggle(y, rightBase, inputX, "opchat.config.enabled", ChatBubbleConfig.ENABLED, v -> ChatBubbleConfig.ENABLED = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.red_dot", ChatBubbleConfig.RED_DOT_ENABLED, v -> ChatBubbleConfig.RED_DOT_ENABLED = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.animation", ChatBubbleConfig.ANIMATION_ENABLED, v -> ChatBubbleConfig.ANIMATION_ENABLED = v);
            }
            case 1 -> {
                y = addSection(y, rightBase, "\u6d88\u606f\u63d0\u793a");
                y = addToggle(y, rightBase, inputX, "opchat.config.strong_hint", ChatBubbleConfig.STRONG_HINT_ENABLED, v -> ChatBubbleConfig.STRONG_HINT_ENABLED = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.mention_strong_hint", ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED, v -> ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.anti_spam", ChatBubbleConfig.ANTI_SPAM, v -> ChatBubbleConfig.ANTI_SPAM = v);
            }
            case 2 -> {
                y = addSection(y, rightBase, "\u6d88\u606f\u9884\u89c8");
                y = addToggle(y, rightBase, inputX, "opchat.config.preview_enabled", ChatBubbleConfig.PREVIEW_ENABLED, v -> ChatBubbleConfig.PREVIEW_ENABLED = v);
                y = addCycle(y, rightBase, inputX, "opchat.config.preview_lines", String.valueOf(ChatBubbleConfig.PREVIEW_LINES), b -> {
                    ChatBubbleConfig.PREVIEW_LINES = (ChatBubbleConfig.PREVIEW_LINES % 3) + 1;
                    b.setMessage(Text.literal(String.valueOf(ChatBubbleConfig.PREVIEW_LINES)));
                    ChatBubbleConfig.save();
                });
                y = addIntInput(y, rightBase, inputX, "opchat.config.preview_width", String.valueOf(ChatBubbleConfig.PREVIEW_WIDTH), 50, 400, v -> ChatBubbleConfig.PREVIEW_WIDTH = v);
            }
            case 3 -> {
                y = addSection(y, rightBase, "\u5916\u89c2");
                y = addHexInput(y, rightBase, inputX, previewX, "opchat.config.own_bubble_color", ChatBubbleConfig.OWN_BUBBLE_COLOR, v -> ChatBubbleConfig.OWN_BUBBLE_COLOR = v);
                y = addHexInput(y, rightBase, inputX, previewX, "opchat.config.other_bubble_color", ChatBubbleConfig.OTHER_BUBBLE_COLOR, v -> ChatBubbleConfig.OTHER_BUBBLE_COLOR = v);
                y = addHexInput(y, rightBase, inputX, previewX, "opchat.config.own_text_color", ChatBubbleConfig.OWN_TEXT_COLOR, v -> ChatBubbleConfig.OWN_TEXT_COLOR = v);
                y = addHexInput(y, rightBase, inputX, previewX, "opchat.config.other_text_color", ChatBubbleConfig.OTHER_TEXT_COLOR, v -> ChatBubbleConfig.OTHER_TEXT_COLOR = v);
                y = addIntInput(y, rightBase, inputX, "opchat.config.panel_opacity", String.valueOf(ChatBubbleConfig.PANEL_OPACITY), 0, 100, v -> ChatBubbleConfig.PANEL_OPACITY = v);
            }
            case 4 -> {
                y = addSection(y, rightBase, "\u79c1\u804a\u4e0e\u5386\u53f2");
                y = addIntInput(y, rightBase, inputX, "opchat.config.whisper_history_days", String.valueOf(ChatBubbleConfig.WHISPER_HISTORY_DAYS), 0, 365, v -> ChatBubbleConfig.WHISPER_HISTORY_DAYS = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.send_history_preview", ChatBubbleConfig.SEND_HISTORY_PREVIEW, v -> ChatBubbleConfig.SEND_HISTORY_PREVIEW = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.chat_history_save", ChatBubbleConfig.CHAT_HISTORY_SAVE, v -> {
                    ChatBubbleConfig.CHAT_HISTORY_SAVE = v;
                    ChatMessageStore.setHistorySavingEnabled(v);
                });
            }
            case 5 -> {
                y = addSection(y, rightBase, "\u5feb\u6377\u6307\u4ee4");
                y = addToggle(y, rightBase, inputX, "opchat.config.multi_mode_commands", ChatBubbleConfig.MULTI_MODE_COMMANDS, v -> ChatBubbleConfig.MULTI_MODE_COMMANDS = v);
            }
            case 6 -> {
                y = addSection(y, rightBase, "\u517c\u5bb9\u6027");
                y = addToggle(y, rightBase, inputX, "opchat.config.system_chat_as_bubble", ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE, v -> ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.chat_report_compat", ChatBubbleConfig.CHAT_REPORT_COMPAT, v -> ChatBubbleConfig.CHAT_REPORT_COMPAT = v);
                y = addToggle(y, rightBase, inputX, "opchat.config.ime_blocker", ChatBubbleConfig.IME_BLOCKER_ENABLED, v -> {
                    ChatBubbleConfig.IME_BLOCKER_ENABLED = v;
                    // 开关切换后立即应用到当前状态
                    if (v) {
                        com.opchat.ime.IMEBlocker.enableIME();
                    }
                });
            }
            case 7 -> {
                y = addSection(y, rightBase, "\u8054\u7cfb\u4eba\u5206\u7ec4");
                renderEntries.add(new RenderEntry(y, "opchat.config.contact_groups", 1, null));
                ButtonWidget manageBtn = ButtonWidget.builder(Text.literal("\u7ba1\u7406\u5206\u7ec4 \u2192"), b ->
                    client.setScreen(new ContactGroupScreen(this))
                ).dimensions(inputX, y, INPUT_W, 20).build();
                rightWidgets.add(manageBtn);
                addDrawableChild(manageBtn);
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> close())
            .dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    private int addSection(int y, int rightBase, String title) {
        renderEntries.add(new RenderEntry(y, title, 0, null));
        return y + 28;
    }

    private int addToggle(int y, int rightBase, int inputX, String key, boolean value, Consumer<Boolean> setter) {
        renderEntries.add(new RenderEntry(y, key, 1, null));
        final boolean[] cur = {value};
        ButtonWidget btn = ButtonWidget.builder(
            Text.literal(cur[0] ? "ON" : "OFF"),
            b -> {
                cur[0] = !cur[0];
                setter.accept(cur[0]);
                b.setMessage(Text.literal(cur[0] ? "ON" : "OFF"));
                ChatBubbleConfig.save();
            }).dimensions(inputX, y, INPUT_W, 20).build();
        rightWidgets.add(btn);
        addDrawableChild(btn);
        return y + ROW_H;
    }

    private int addCycle(int y, int rightBase, int inputX, String key, String current, Consumer<ButtonWidget> onClick) {
        renderEntries.add(new RenderEntry(y, key, 1, null));
        ButtonWidget btn = ButtonWidget.builder(Text.literal(current), b -> onClick.accept(b))
            .dimensions(inputX, y, INPUT_W, 20).build();
        rightWidgets.add(btn);
        addDrawableChild(btn);
        return y + ROW_H;
    }

    private int addIntInput(int y, int rightBase, int inputX, String key, String initial, int min, int max, Consumer<Integer> setter) {
        renderEntries.add(new RenderEntry(y, key, 1, null));
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(3);
        box.setChangedListener(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) {
                    setter.accept(v);
                    ChatBubbleConfig.save();
                }
            } catch (NumberFormatException ignored) {}
        });
        rightWidgets.add(box);
        addDrawableChild(box);
        return y + ROW_H;
    }

    private int addHexInput(int y, int rightBase, int inputX, int previewX, String key, String initial, Consumer<String> setter) {
        final TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        renderEntries.add(new RenderEntry(y, key, 2, box::getText));
        box.setText(initial);
        box.setMaxLength(7);
        box.setChangedListener(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) {
                box.setText("#" + s);
                setter.accept("#" + s);
                ChatBubbleConfig.save();
            } else if (s.length() == 7) {
                setter.accept(s);
                ChatBubbleConfig.save();
            }
        });
        rightWidgets.add(box);
        addDrawableChild(box);
        return y + ROW_H;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInGameBackground(context);
        renderDarkening(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

        context.fill(0, 0, LEFT_W, height, 0x80000000);
        context.fill(LEFT_W, 0, LEFT_W + 1, height, 0xFF333333);

        int catY = START_Y + selectedCategory * LEFT_ITEM_H;
        context.fill(0, catY - 1, LEFT_W, catY + LEFT_ITEM_H - 1, 0xFF2A4A7A);

        for (int i = 0; i < CATEGORIES.length; i++) {
            int itemY = START_Y + i * LEFT_ITEM_H;
            int textColor = (i == selectedCategory) ? 0xFFFFFFFF : 0xFFAAAAAA;
            context.drawTextWithShadow(textRenderer, Text.literal(CATEGORIES[i]), 8,
                itemY + (LEFT_ITEM_H - textRenderer.fontHeight) / 2, textColor);
        }

        int rightBase = LEFT_W + LABEL_X_OFFSET;
        for (var entry : renderEntries) {
            int ey = entry.y();
            if (ey < 0 || ey > height) continue;
            if (entry.kind() == 0) {
                context.drawTextWithShadow(textRenderer, Text.literal(entry.text()), rightBase, ey, 0xFFFFAA00);
                context.fill(rightBase, ey + 12, width - 20, ey + 13, 0x44FFAA00);
            } else if (entry.kind() == 1) {
                context.drawTextWithShadow(textRenderer, Text.translatable(entry.text()), rightBase, ey + 6, 0xFFAAAAAA);
            } else if (entry.kind() == 2) {
                context.drawTextWithShadow(textRenderer, Text.translatable(entry.text()), rightBase, ey + 6, 0xFFAAAAAA);
                int previewX = LEFT_W + PREVIEW_X_OFFSET;
                String colorStr = entry.colorSupplier() != null ? entry.colorSupplier().get() : "";
                int color = ChatBubbleConfig.parseHexColor(colorStr, 0xFF000000);
                context.fill(previewX, ey + 3, previewX + 14, ey + 17, 0xFF444444);
                context.fill(previewX + 1, ey + 4, previewX + 13, ey + 16, color);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        ChatBubbleConfig.save();
        client.setScreen(lastScreen);
    }

    @Override
    public void removed() {
        ChatBubbleConfig.save();
    }
}
