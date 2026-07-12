package com.opchat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;
    private static final int LABEL_X = 40;
    private static final int INPUT_X = 175;
    private static final int INPUT_W = 130;
    private static final int PREVIEW_X = 315;
    private static final int ROW_H = 26;
    private static final int SECTION_H = 24;
    private static final int START_Y = 36;

    private int scrollOffset;
    private final List<Element> scrollWidgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();

    // kind: 0=section_header, 1=label, 2=label_with_color_preview
    private record RenderRow(int baseY, String text, int kind, Supplier<String> colorSupplier) {}
    private final List<RenderRow> renderRows = new ArrayList<>();

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Text.translatable("opchat.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        scrollWidgets.clear();
        widgetBaseY.clear();
        renderRows.clear();
        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());
        int y = START_Y - scrollOffset;

        y = addSection(y, "\u57fa\u7840\u8bbe\u7f6e");
        y = addToggle(y, "opchat.config.enabled", ChatBubbleConfig.ENABLED, val -> ChatBubbleConfig.ENABLED = val);
        y = addToggle(y, "opchat.config.red_dot", ChatBubbleConfig.RED_DOT_ENABLED, val -> ChatBubbleConfig.RED_DOT_ENABLED = val);
        y = addToggle(y, "opchat.config.animation", ChatBubbleConfig.ANIMATION_ENABLED, val -> ChatBubbleConfig.ANIMATION_ENABLED = val);

        y = addSection(y, "\u6d88\u606f\u63d0\u793a");
        y = addToggle(y, "opchat.config.strong_hint", ChatBubbleConfig.STRONG_HINT_ENABLED, val -> ChatBubbleConfig.STRONG_HINT_ENABLED = val);
        y = addToggle(y, "opchat.config.mention_strong_hint", ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED, val -> ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED = val);
        y = addToggle(y, "opchat.config.anti_spam", ChatBubbleConfig.ANTI_SPAM, val -> ChatBubbleConfig.ANTI_SPAM = val);

        y = addSection(y, "\u6d88\u606f\u9884\u89c8");
        y = addToggle(y, "opchat.config.preview_enabled", ChatBubbleConfig.PREVIEW_ENABLED, val -> ChatBubbleConfig.PREVIEW_ENABLED = val);
        y = addCycle(y, "opchat.config.preview_lines", String.valueOf(ChatBubbleConfig.PREVIEW_LINES), b -> {
            ChatBubbleConfig.PREVIEW_LINES = (ChatBubbleConfig.PREVIEW_LINES % 3) + 1;
            b.setMessage(Text.literal(String.valueOf(ChatBubbleConfig.PREVIEW_LINES)));
            ChatBubbleConfig.save();
        });
        y = addIntInput(y, "opchat.config.preview_width", String.valueOf(ChatBubbleConfig.PREVIEW_WIDTH), 50, 400, val -> ChatBubbleConfig.PREVIEW_WIDTH = val);

        y = addSection(y, "\u6c14\u6ce1\u5916\u89c2");
        y = addHexInput(y, "opchat.config.own_bubble_color", ChatBubbleConfig.OWN_BUBBLE_COLOR, val -> ChatBubbleConfig.OWN_BUBBLE_COLOR = val);
        y = addHexInput(y, "opchat.config.other_bubble_color", ChatBubbleConfig.OTHER_BUBBLE_COLOR, val -> ChatBubbleConfig.OTHER_BUBBLE_COLOR = val);
        y = addHexInput(y, "opchat.config.own_text_color", ChatBubbleConfig.OWN_TEXT_COLOR, val -> ChatBubbleConfig.OWN_TEXT_COLOR = val);
        y = addHexInput(y, "opchat.config.other_text_color", ChatBubbleConfig.OTHER_TEXT_COLOR, val -> ChatBubbleConfig.OTHER_TEXT_COLOR = val);
        y = addIntInput(y, "opchat.config.panel_opacity", String.valueOf(ChatBubbleConfig.PANEL_OPACITY), 0, 100, val -> ChatBubbleConfig.PANEL_OPACITY = val);

        y = addSection(y, "\u79c1\u804a\u4e0e\u5386\u53f2");
        y = addIntInput(y, "opchat.config.whisper_history_days", String.valueOf(ChatBubbleConfig.WHISPER_HISTORY_DAYS), 0, 365, val -> ChatBubbleConfig.WHISPER_HISTORY_DAYS = val);

        y = addSection(y, "\u517c\u5bb9\u6027");
        y = addToggle(y, "opchat.config.system_chat_as_bubble", ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE, val -> ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE = val);
        y = addToggle(y, "opchat.config.chat_report_compat", ChatBubbleConfig.CHAT_REPORT_COMPAT, val -> ChatBubbleConfig.CHAT_REPORT_COMPAT = val);

        y = addSection(y, "\u8054\u7cfb\u4eba\u5206\u7ec4");
        for (var group : ChatBubbleConfig.CONTACT_GROUPS) {
            renderRows.add(new RenderRow(y + scrollOffset, group.name + " \u524d\u7f00", 1, null));
            TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, INPUT_W, 20, Text.literal(""));
            box.setText(String.join(",", group.prefixes));
            box.setMaxLength(100);
            final String groupName = group.name;
            box.setChangedListener(s -> {
                java.util.List<String> prefixes = new java.util.ArrayList<>();
                for (String p : s.split(",")) {
                    p = p.trim();
                    if (!p.isEmpty()) prefixes.add(p);
                }
                for (var g : ChatBubbleConfig.CONTACT_GROUPS) {
                    if (g.name.equals(groupName)) {
                        g.prefixes = prefixes;
                        ChatBubbleConfig.save();
                        break;
                    }
                }
            });
            scrollWidgets.add(addDrawableChild(box));
            widgetBaseY.add(y + scrollOffset);
            y += ROW_H;
        }

        renderRows.add(new RenderRow(y + scrollOffset, "\u65b0\u5efa\u5206\u7ec4", 1, null));
        {
            TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, INPUT_W - 36, 20, Text.literal(""));
            box.setMaxLength(20);
            box.setPlaceholder(Text.literal("\u5206\u7ec4\u540d..."));
            scrollWidgets.add(addDrawableChild(box));
            widgetBaseY.add(y + scrollOffset);
            final TextFieldWidget boxRef = box;
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
                String name = boxRef.getText().trim();
                if (!name.isEmpty()) {
                    ChatBubbleConfig.addGroup(name);
                    boxRef.setText("");
                    init();
                }
            }).dimensions(INPUT_X + INPUT_W - 31, y, 31, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> close())
            .dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    private int addSection(int y, String title) {
        renderRows.add(new RenderRow(y + scrollOffset, title, 0, null));
        return y + SECTION_H;
    }

    private int addToggle(int y, String key, boolean value, java.util.function.Consumer<Boolean> setter) {
        renderRows.add(new RenderRow(y + scrollOffset, key, 1, null));
        final boolean[] cur = {value};
        ButtonWidget btn = ButtonWidget.builder(
            Text.literal(cur[0] ? "ON" : "OFF"),
            b -> {
                cur[0] = !cur[0];
                setter.accept(cur[0]);
                b.setMessage(Text.literal(cur[0] ? "ON" : "OFF"));
                ChatBubbleConfig.save();
            }).dimensions(INPUT_X, y, INPUT_W, 20).build();
        scrollWidgets.add(addDrawableChild(btn));
        widgetBaseY.add(y + scrollOffset);
        return y + ROW_H;
    }

    private int addCycle(int y, String key, String current, java.util.function.Consumer<ButtonWidget> onClick) {
        renderRows.add(new RenderRow(y + scrollOffset, key, 1, null));
        ButtonWidget btn = ButtonWidget.builder(Text.literal(current), b -> onClick.accept(b))
            .dimensions(INPUT_X, y, INPUT_W, 20).build();
        scrollWidgets.add(addDrawableChild(btn));
        widgetBaseY.add(y + scrollOffset);
        return y + ROW_H;
    }

    private int addIntInput(int y, String key, String initial, int min, int max, java.util.function.Consumer<Integer> setter) {
        renderRows.add(new RenderRow(y + scrollOffset, key, 1, null));
        TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, INPUT_W, 20, Text.literal(""));
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
        scrollWidgets.add(addDrawableChild(box));
        widgetBaseY.add(y + scrollOffset);
        return y + ROW_H;
    }

    private int addHexInput(int y, String key, String initial, java.util.function.Consumer<String> setter) {
        final TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, INPUT_W, 20, Text.literal(""));
        renderRows.add(new RenderRow(y + scrollOffset, key, 2, box::getText));
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
        scrollWidgets.add(addDrawableChild(box));
        widgetBaseY.add(y + scrollOffset);
        return y + ROW_H;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInGameBackground(context);
        renderDarkening(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

        for (var row : renderRows) {
            int y = row.baseY() - scrollOffset;
            if (y < -SECTION_H || y > height) continue;
            if (row.kind() == 0) {
                context.drawTextWithShadow(textRenderer, Text.literal(row.text()), LABEL_X, y, 0xFFFFAA00);
                context.fill(LABEL_X, y + 11, width - LABEL_X, y + 12, 0x44FFAA00);
            } else if (row.kind() == 1) {
                context.drawTextWithShadow(textRenderer, Text.translatable(row.text()), LABEL_X, y + 6, 0xFFAAAAAA);
            } else if (row.kind() == 2) {
                context.drawTextWithShadow(textRenderer, Text.translatable(row.text()), LABEL_X, y + 6, 0xFFAAAAAA);
                String colorStr = row.colorSupplier() != null ? row.colorSupplier().get() : "";
                int color = ChatBubbleConfig.parseHexColor(colorStr, 0xFF000000);
                context.fill(PREVIEW_X, y + 3, PREVIEW_X + 14, y + 17, 0xFF444444);
                context.fill(PREVIEW_X + 1, y + 4, PREVIEW_X + 13, y + 16, color);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        ChatBubbleConfig.save();
        client.setScreen(lastScreen);
    }

    private int calcMaxScroll() {
        int sections = 7;
        int items = 17 + ChatBubbleConfig.CONTACT_GROUPS.size() + 1;
        int total = START_Y + sections * SECTION_H + items * ROW_H + 20;
        return Math.max(0, total - (height - 40));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (verticalAmount * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        for (int i = 0; i < scrollWidgets.size() && i < widgetBaseY.size(); i++) {
            if (scrollWidgets.get(i) instanceof net.minecraft.client.gui.widget.ClickableWidget cw) {
                cw.setY(widgetBaseY.get(i) - scrollOffset);
            }
        }
        return true;
    }

    @Override
    public void removed() {
        ChatBubbleConfig.save();
    }
}
