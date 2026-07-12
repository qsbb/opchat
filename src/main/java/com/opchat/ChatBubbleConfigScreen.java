package com.opchat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;
    private static final int LABEL_X = 40;
    private static final int INPUT_X = 165;
    private static final int INPUT_W = 135;
    private static final int PREVIEW_X = 308;
    private static final int ROW_H = 28;
    private static final int START_Y = 38;

    private int scrollOffset;
    private final List<Element> scrollWidgets = new ArrayList<>();

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Text.translatable("opchat.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        scrollWidgets.clear();
        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());
        int y = START_Y - scrollOffset;

        addToggleOption(y, "opchat.config.enabled", ChatBubbleConfig.ENABLED, val -> ChatBubbleConfig.ENABLED = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.red_dot", ChatBubbleConfig.RED_DOT_ENABLED, val -> ChatBubbleConfig.RED_DOT_ENABLED = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.animation", ChatBubbleConfig.ANIMATION_ENABLED, val -> ChatBubbleConfig.ANIMATION_ENABLED = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.strong_hint", ChatBubbleConfig.STRONG_HINT_ENABLED, val -> ChatBubbleConfig.STRONG_HINT_ENABLED = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.mention_strong_hint", ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED, val -> ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.anti_spam", ChatBubbleConfig.ANTI_SPAM, val -> ChatBubbleConfig.ANTI_SPAM = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.preview_enabled", ChatBubbleConfig.PREVIEW_ENABLED, val -> ChatBubbleConfig.PREVIEW_ENABLED = val);
        y += ROW_H;

        // Preview lines (1-3)
        {
            int yy = y;
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal(String.valueOf(ChatBubbleConfig.PREVIEW_LINES)),
                b -> {
                    ChatBubbleConfig.PREVIEW_LINES = (ChatBubbleConfig.PREVIEW_LINES % 3) + 1;
                    b.setMessage(Text.literal(String.valueOf(ChatBubbleConfig.PREVIEW_LINES)));
                    ChatBubbleConfig.save();
                }).dimensions(INPUT_X, yy, INPUT_W, 20).build();
            scrollWidgets.add(addDrawableChild(btn));
        }
        y += ROW_H;

        // Preview width
        {
            TextFieldWidget box = mkIntBox(y, String.valueOf(ChatBubbleConfig.PREVIEW_WIDTH), 50, 400, val -> ChatBubbleConfig.PREVIEW_WIDTH = val);
            scrollWidgets.add(addDrawableChild(box));
        }
        y += ROW_H;

        // Own bubble color
        {
            TextFieldWidget box = mkHexBox(y, ChatBubbleConfig.OWN_BUBBLE_COLOR, val -> ChatBubbleConfig.OWN_BUBBLE_COLOR = val);
            scrollWidgets.add(addDrawableChild(box));
        }
        y += ROW_H;

        // Other bubble color
        {
            TextFieldWidget box = mkHexBox(y, ChatBubbleConfig.OTHER_BUBBLE_COLOR, val -> ChatBubbleConfig.OTHER_BUBBLE_COLOR = val);
            scrollWidgets.add(addDrawableChild(box));
        }
        y += ROW_H;

        // Own text color
        {
            TextFieldWidget box = mkHexBox(y, ChatBubbleConfig.OWN_TEXT_COLOR, val -> ChatBubbleConfig.OWN_TEXT_COLOR = val);
            scrollWidgets.add(addDrawableChild(box));
        }
        y += ROW_H;

        // Other text color
        {
            TextFieldWidget box = mkHexBox(y, ChatBubbleConfig.OTHER_TEXT_COLOR, val -> ChatBubbleConfig.OTHER_TEXT_COLOR = val);
            scrollWidgets.add(addDrawableChild(box));
        }
        y += ROW_H;

        // Panel opacity (0-100)
        {
            TextFieldWidget box = mkIntBox(y, String.valueOf(ChatBubbleConfig.PANEL_OPACITY), 0, 100, val -> ChatBubbleConfig.PANEL_OPACITY = val);
            scrollWidgets.add(addDrawableChild(box));
        }
        y += ROW_H;

        addToggleOption(y, "opchat.config.system_chat_as_bubble", ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE, val -> ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE = val);
        y += ROW_H;

        addToggleOption(y, "opchat.config.chat_report_compat", ChatBubbleConfig.CHAT_REPORT_COMPAT, val -> ChatBubbleConfig.CHAT_REPORT_COMPAT = val);
        y += ROW_H;

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> close())
            .dimensions(width / 2 - 100, height - 32, 200, 20).build());
    }

    private void addToggleOption(int y, String translationKey, boolean value, java.util.function.Consumer<Boolean> setter) {
        final boolean[] currentValue = {value};
        ButtonWidget btn = ButtonWidget.builder(
            Text.literal(currentValue[0] ? "ON" : "OFF"),
            b -> {
                currentValue[0] = !currentValue[0];
                setter.accept(currentValue[0]);
                b.setMessage(Text.literal(currentValue[0] ? "ON" : "OFF"));
                ChatBubbleConfig.save();
            }).dimensions(INPUT_X, y, INPUT_W, 20).build();
        scrollWidgets.add(addDrawableChild(btn));
    }

    private TextFieldWidget mkHexBox(int y, String initial, java.util.function.Consumer<String> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(7);
        box.setChangedListener(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) {
                box.setText("#" + s);
                onChange.accept("#" + s);
                ChatBubbleConfig.save();
            } else if (s.length() == 7) {
                onChange.accept(s);
                ChatBubbleConfig.save();
            }
        });
        return box;
    }

    private TextFieldWidget mkIntBox(int y, String initial, int min, int max, java.util.function.Consumer<Integer> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(3);
        box.setChangedListener(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) {
                    onChange.accept(v);
                    ChatBubbleConfig.save();
                }
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInGameBackground(context);
        renderDarkening(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

        int y = START_Y + 6 - scrollOffset;
        // Section header
        if (y > -ROW_H && y < height)
            context.drawTextWithShadow(textRenderer, Text.literal("常规选项"), LABEL_X, y - 12, 0xFFFFAA00);

        String[] labels = {"opchat.config.enabled", "opchat.config.red_dot", "opchat.config.animation",
            "opchat.config.strong_hint", "opchat.config.mention_strong_hint",
            "opchat.config.anti_spam",
            "opchat.config.preview_enabled", "opchat.config.preview_lines", "opchat.config.preview_width",
            "opchat.config.own_bubble_color", "opchat.config.other_bubble_color", "opchat.config.own_text_color", "opchat.config.other_text_color",
            "opchat.config.panel_opacity",
            "opchat.config.system_chat_as_bubble",
            "opchat.config.chat_report_compat",
        };
        for (String label : labels) {
            if (y > -ROW_H && y < height)
                context.drawTextWithShadow(textRenderer, Text.translatable(label), LABEL_X, y, 0xFFAAAAAA);
            y += ROW_H;
        }

        // Section header for compat
        int compatHeaderY = START_Y + 6 + 14 * ROW_H - scrollOffset - 12;
        if (compatHeaderY > -ROW_H && compatHeaderY < height)
            context.drawTextWithShadow(textRenderer, Text.literal("兼容性选项"), LABEL_X, compatHeaderY, 0xFFFFAA00);

        int py = START_Y + ROW_H * 9 + 4 - scrollOffset;
        drawPreview(context, py, ChatBubbleConfig.OWN_BUBBLE_COLOR); py += ROW_H;
        drawPreview(context, py, ChatBubbleConfig.OTHER_BUBBLE_COLOR); py += ROW_H;
        drawPreview(context, py, ChatBubbleConfig.OWN_TEXT_COLOR); py += ROW_H;
        drawPreview(context, py, ChatBubbleConfig.OTHER_TEXT_COLOR); py += ROW_H;
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPreview(DrawContext context, int y, String hex) {
        int color = ChatBubbleConfig.parseHexColor(hex, 0xFF000000);
        context.fill(PREVIEW_X, y, PREVIEW_X + 14, y + 14, 0xFF444444);
        context.fill(PREVIEW_X + 1, y + 1, PREVIEW_X + 13, y + 13, color);
    }

    @Override
    public void close() {
        ChatBubbleConfig.save();
        client.setScreen(lastScreen);
    }

    private int calcMaxScroll() {
        int contentBottom = START_Y + 16 * ROW_H + 10;
        return Math.max(0, contentBottom - (height - 42));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (verticalAmount * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        // Update widget positions
        int idx = 0;
        for (Element w : scrollWidgets) {
            if (w instanceof net.minecraft.client.gui.widget.ClickableWidget cw) {
                cw.setY(START_Y + idx * ROW_H - scrollOffset);
            }
            idx++;
        }
        return true;
    }

    @Override
    public void removed() {
        ChatBubbleConfig.save();
    }
}
