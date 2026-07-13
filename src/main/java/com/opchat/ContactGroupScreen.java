package com.opchat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ContactGroupScreen extends Screen {
    private final Screen lastScreen;
    private static final int LABEL_X = 40;
    private static final int INPUT_X = 175;
    private static final int INPUT_W = 200;
    private static final int ROW_H = 28;
    private static final int START_Y = 40;

    private int scrollOffset;
    private final List<ClickableWidget> scrollableWidgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();
    private TextFieldWidget newGroupField;
    private String pendingDelete = null;

    public ContactGroupScreen(Screen lastScreen) {
        super(Text.literal("\u8054\u7cfb\u4eba\u5206\u7ec4"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        scrollableWidgets.clear();
        widgetBaseY.clear();
        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());
        int y = START_Y - scrollOffset;

        for (var group : ChatBubbleConfig.CONTACT_GROUPS) {
            int fieldW = INPUT_W - 40;
            TextFieldWidget box = new TextFieldWidget(textRenderer, INPUT_X, y, fieldW, 20, Text.literal(""));
            box.setText(String.join(",", group.prefixes));
            box.setMaxLength(100);
            box.setPlaceholder(Text.literal("\u524d\u7f00\uff0c\u9017\u53f7\u5206\u9694"));
            final String groupName = group.name;
            box.setChangedListener(s -> {
                List<String> prefixes = new ArrayList<>();
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
            addDrawableChild(box);
            scrollableWidgets.add(box);
            widgetBaseY.add(y + scrollOffset);

            final String gName = group.name;
            boolean isConfirming = gName.equals(pendingDelete);
            ButtonWidget delBtn = ButtonWidget.builder(
                Text.literal(isConfirming ? "\u786e\u8ba4?" : "\u2715"), b -> {
                    if (gName.equals(pendingDelete)) {
                        ChatBubbleConfig.removeGroup(gName);
                        ChatBubbleConfig.save();
                        pendingDelete = null;
                        clearAndReinit();
                    } else {
                        pendingDelete = gName;
                        clearAndReinit();
                    }
                }).dimensions(INPUT_X + fieldW + 5, y, 35, 20).build();
            addDrawableChild(delBtn);
            scrollableWidgets.add(delBtn);
            widgetBaseY.add(y + scrollOffset);

            y += ROW_H;
        }

        int fieldW = INPUT_W - 40;
        newGroupField = new TextFieldWidget(textRenderer, INPUT_X, y, fieldW, 20, Text.literal(""));
        newGroupField.setMaxLength(20);
        newGroupField.setPlaceholder(Text.literal("\u65b0\u5206\u7ec4\u540d..."));
        addDrawableChild(newGroupField);
        scrollableWidgets.add(newGroupField);
        widgetBaseY.add(y + scrollOffset);

        ButtonWidget addBtn = ButtonWidget.builder(Text.literal("+"), b -> {
            String name = newGroupField.getText().trim();
            if (!name.isEmpty()) {
                ChatBubbleConfig.addGroup(name);
                ChatBubbleConfig.save();
                clearAndReinit();
            }
        }).dimensions(INPUT_X + fieldW + 5, y, 35, 20).build();
        addDrawableChild(addBtn);
        scrollableWidgets.add(addBtn);
        widgetBaseY.add(y + scrollOffset);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> close())
            .dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    private void clearAndReinit() {
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInGameBackground(context);
        renderDarkening(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

        int y = START_Y - scrollOffset;
        for (int i = 0; i < ChatBubbleConfig.CONTACT_GROUPS.size(); i++) {
            int rowY = y + i * ROW_H;
            if (rowY < -ROW_H || rowY > height) continue;
            var group = ChatBubbleConfig.CONTACT_GROUPS.get(i);
            String label = group.name + " (" + group.members.size() + ")";
            context.drawTextWithShadow(textRenderer, Text.literal(label), LABEL_X, rowY + 6, 0xFFCCCCCC);
        }

        int newGroupY = y + ChatBubbleConfig.CONTACT_GROUPS.size() * ROW_H;
        if (newGroupY > -ROW_H && newGroupY < height)
            context.drawTextWithShadow(textRenderer, Text.literal("\u65b0\u5efa\u5206\u7ec4"), LABEL_X, newGroupY + 6, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        ChatBubbleConfig.save();
        client.setScreen(lastScreen);
    }

    private int calcMaxScroll() {
        int total = START_Y + (ChatBubbleConfig.CONTACT_GROUPS.size() + 1) * ROW_H + 40;
        return Math.max(0, total - (height - 40));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (verticalAmount * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        for (int i = 0; i < scrollableWidgets.size() && i < widgetBaseY.size(); i++) {
            scrollableWidgets.get(i).setY(widgetBaseY.get(i) - scrollOffset);
        }
        return true;
    }

    @Override
    protected void setInitialFocus() {
        if (newGroupField != null) {
            this.setFocused(newGroupField);
        }
    }

    @Override
    public void removed() {
        ChatBubbleConfig.save();
    }
}
