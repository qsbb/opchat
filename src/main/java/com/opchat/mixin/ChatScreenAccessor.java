package com.opchat.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("originalChatText")
    String opchat$getOriginalChatText();

    @Accessor("chatField")
    TextFieldWidget opchat$getChatField();
}
