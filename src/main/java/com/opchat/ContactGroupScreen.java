package com.opchat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ContactGroupScreen extends Screen {
    private final Screen lastScreen;
    private static final int LABEL_X = 40;
    private static final int INPUT_X = 175;
    private static final int INPUT_W = 200;
    private static final int HEADER_H = 26;
    private static final int MEMBER_HINT_H = 12;
    private static final int MEMBER_ROW_H = 16;
    private static final int ADD_ROW_H = 24;
    private static final int GROUP_GAP = 6;
    private static final int START_Y = 40;
    private static final int FIELD_W = INPUT_W - 40;

    private int scrollOffset;
    private final List<ClickableWidget> scrollableWidgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();
    private TextFieldWidget newGroupField;
    private String pendingDelete = null;
    private final Set<String> expandedGroups = new HashSet<>();
    private String selectedGroup = null;
    private String selectedMember = null;

    private final List<String> memberRowGroup = new ArrayList<>();
    private final List<String> memberRowName = new ArrayList<>();
    private final List<Integer> memberRowBaseY = new ArrayList<>();
    private final List<String> headerGroupName = new ArrayList<>();
    private final List<Integer> headerBaseY = new ArrayList<>();
    private final List<String> addFieldGroup = new ArrayList<>();
    private final List<TextFieldWidget> addFields = new ArrayList<>();

    public ContactGroupScreen(Screen lastScreen) {
        super(Text.literal("\u8054\u7cfb\u4eba\u5206\u7ec4"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        scrollableWidgets.clear();
        widgetBaseY.clear();
        memberRowGroup.clear();
        memberRowName.clear();
        memberRowBaseY.clear();
        headerGroupName.clear();
        headerBaseY.clear();
        addFieldGroup.clear();
        addFields.clear();

        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());
        int y = START_Y - scrollOffset;

        for (var group : ChatBubbleConfig.CONTACT_GROUPS) {
            headerGroupName.add(group.name);
            headerBaseY.add(y + scrollOffset);

            TextFieldWidget prefixBox = new TextFieldWidget(textRenderer, INPUT_X, y, FIELD_W, 20, Text.literal(""));
            prefixBox.setText(String.join(",", group.prefixes));
            prefixBox.setMaxLength(100);
            prefixBox.setPlaceholder(Text.literal("\u524d\u7f00\uff0c\u9017\u53f7\u5206\u9694"));
            final String groupName = group.name;
            prefixBox.setChangedListener(s -> {
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
            addDrawableChild(prefixBox);
            scrollableWidgets.add(prefixBox);
            widgetBaseY.add(y + scrollOffset);

            final String gName = group.name;
            boolean isConfirming = gName.equals(pendingDelete);
            ButtonWidget delBtn = ButtonWidget.builder(
                Text.literal(isConfirming ? "\u786e\u8ba4?" : "\u2715"), b -> {
                    if (gName.equals(pendingDelete)) {
                        if (gName.equals(selectedGroup)) { selectedGroup = null; selectedMember = null; }
                        expandedGroups.remove(gName);
                        ChatBubbleConfig.removeGroup(gName);
                        ChatBubbleConfig.save();
                        pendingDelete = null;
                        clearAndReinit();
                    } else {
                        pendingDelete = gName;
                        clearAndReinit();
                    }
                }).dimensions(INPUT_X + FIELD_W + 5, y, 35, 20).build();
            addDrawableChild(delBtn);
            scrollableWidgets.add(delBtn);
            widgetBaseY.add(y + scrollOffset);

            y += HEADER_H;

            if (expandedGroups.contains(group.name)) {
                y += MEMBER_HINT_H;
                for (String member : group.members) {
                    memberRowGroup.add(group.name);
                    memberRowName.add(member);
                    memberRowBaseY.add(y + scrollOffset);
                    y += MEMBER_ROW_H;
                }

                TextFieldWidget addBox = new TextFieldWidget(textRenderer, INPUT_X, y, FIELD_W, 20, Text.literal(""));
                addBox.setMaxLength(32);
                addBox.setPlaceholder(Text.literal("\u8f93\u5165\u540d\u5b57\uff0cEnter\u6dfb\u52a0"));
                addDrawableChild(addBox);
                scrollableWidgets.add(addBox);
                widgetBaseY.add(y + scrollOffset);
                addFieldGroup.add(group.name);
                addFields.add(addBox);

                y += ADD_ROW_H;
            }
            y += GROUP_GAP;
        }

        newGroupField = new TextFieldWidget(textRenderer, INPUT_X, y, FIELD_W, 20, Text.literal(""));
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
                expandedGroups.add(name);
                clearAndReinit();
            }
        }).dimensions(INPUT_X + FIELD_W + 5, y, 35, 20).build();
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

    private int calcMaxScroll() {
        return Math.max(0, calcContentHeight() - (height - 40));
    }

    private int calcContentHeight() {
        int h = START_Y;
        for (var group : ChatBubbleConfig.CONTACT_GROUPS) {
            h += HEADER_H;
            if (expandedGroups.contains(group.name)) {
                h += MEMBER_HINT_H;
                h += group.members.size() * MEMBER_ROW_H;
                h += ADD_ROW_H;
            }
            h += GROUP_GAP;
        }
        h += HEADER_H + 40;
        return h;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInGameBackground(context);
        renderDarkening(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

        int y = START_Y - scrollOffset;

        for (var group : ChatBubbleConfig.CONTACT_GROUPS) {
            boolean expanded = expandedGroups.contains(group.name);
            String arrow = expanded ? "\u25bc" : "\u25b6";
            String label = arrow + " " + group.name + " (" + group.members.size() + ")";
            boolean hoverHeader = mouseY >= y && mouseY <= y + HEADER_H && mouseX >= LABEL_X && mouseX <= INPUT_X - 5;
            int labelColor = expanded ? 0xFFFFFFAA : (hoverHeader ? 0xFFFFFFFF : 0xFFCCCCCC);
            context.drawTextWithShadow(textRenderer, Text.literal(label), LABEL_X, y + 6, labelColor);

            y += HEADER_H;

            if (expanded) {
                context.drawTextWithShadow(textRenderer,
                    Text.literal("\u70b9\u51fb\u9009\u4e2d\u6210\u5458\uff0cDel\u5220\u9664"), LABEL_X + 12, y, 0xFF777777);
                y += MEMBER_HINT_H;

                for (int j = 0; j < group.members.size(); j++) {
                    String member = group.members.get(j);
                    boolean selected = group.name.equals(selectedGroup) && member.equals(selectedMember);
                    int rowY = y + j * MEMBER_ROW_H;
                    boolean hoverRow = mouseY >= rowY && mouseY <= rowY + MEMBER_ROW_H
                        && mouseX >= LABEL_X + 8 && mouseX <= INPUT_X + FIELD_W;
                    if (selected) {
                        context.fill(LABEL_X + 8, rowY, INPUT_X + FIELD_W, rowY + MEMBER_ROW_H, 0xFF3A5A8A);
                    } else if (hoverRow) {
                        context.fill(LABEL_X + 8, rowY, INPUT_X + FIELD_W, rowY + MEMBER_ROW_H, 0xFF2A2A2A);
                    }
                    int color = selected ? 0xFFFFFFFF : 0xFFDDDDDD;
                    int textY = rowY + (MEMBER_ROW_H - textRenderer.fontHeight) / 2;
                    context.drawTextWithShadow(textRenderer, Text.literal("\u2022 " + member), LABEL_X + 16, textY, color);
                }
                y += group.members.size() * MEMBER_ROW_H;
                y += ADD_ROW_H;
            }
            y += GROUP_GAP;
        }

        context.drawTextWithShadow(textRenderer, Text.literal("\u65b0\u5efa\u5206\u7ec4"), LABEL_X, y + 6, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();

        if (button == 0) {
            for (int i = 0; i < headerGroupName.size(); i++) {
                int rowY = headerBaseY.get(i) - scrollOffset;
                if (mouseX >= LABEL_X && mouseX <= INPUT_X - 5
                    && mouseY >= rowY && mouseY <= rowY + HEADER_H) {
                    String gName = headerGroupName.get(i);
                    if (expandedGroups.contains(gName)) {
                        expandedGroups.remove(gName);
                        if (gName.equals(selectedGroup)) { selectedGroup = null; selectedMember = null; }
                    } else {
                        expandedGroups.add(gName);
                    }
                    clearAndReinit();
                    return true;
                }
            }

            for (int i = 0; i < memberRowGroup.size(); i++) {
                int rowY = memberRowBaseY.get(i) - scrollOffset;
                if (mouseX >= LABEL_X + 8 && mouseX <= INPUT_X + FIELD_W
                    && mouseY >= rowY && mouseY <= rowY + MEMBER_ROW_H) {
                    selectedGroup = memberRowGroup.get(i);
                    selectedMember = memberRowName.get(i);
                    setFocused(null);
                    return true;
                }
            }
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        if (keyCode == 256) { close(); return true; }

        if (keyCode == 257 || keyCode == 335) {
            for (int i = 0; i < addFields.size(); i++) {
                if (addFields.get(i).isFocused()) {
                    String name = addFields.get(i).getText().trim();
                    if (!name.isEmpty()) {
                        ChatBubbleConfig.addToGroup(addFieldGroup.get(i), name);
                        ChatBubbleConfig.save();
                        clearAndReinit();
                    }
                    return true;
                }
            }
            if (newGroupField != null && newGroupField.isFocused()) {
                String name = newGroupField.getText().trim();
                if (!name.isEmpty()) {
                    ChatBubbleConfig.addGroup(name);
                    ChatBubbleConfig.save();
                    expandedGroups.add(name);
                    clearAndReinit();
                }
                return true;
            }
        }

        if (keyCode == 261 && selectedMember != null && selectedGroup != null) {
            ChatBubbleConfig.removeFromGroup(selectedGroup, selectedMember);
            ChatBubbleConfig.save();
            selectedMember = null;
            selectedGroup = null;
            clearAndReinit();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        ChatBubbleConfig.save();
        client.setScreen(lastScreen);
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
