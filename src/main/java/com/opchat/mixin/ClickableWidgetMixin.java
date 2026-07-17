package com.opchat.mixin;

import com.opchat.ChatBubbleConfig;
import com.opchat.ime.IMEBlocker;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 监听文本输入框获得焦点：仅当可输入文本框获得焦点时才启用输入法。
 * 其他 UI（按钮、滑块等）不会唤醒输入法。
 */
@Mixin(ClickableWidget.class)
public class ClickableWidgetMixin {
    @Inject(method = "setFocused", at = @At("HEAD"))
    private void opchat$onSetFocus(boolean focused, CallbackInfo ci) {
        if (!ChatBubbleConfig.IME_BLOCKER_ENABLED || !IMEBlocker.isSupported()) return;
        if ((Object) this instanceof TextFieldWidget && focused) {
            IMEBlocker.enableIME();
        }
    }
}
