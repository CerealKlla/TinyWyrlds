package com.github.cerealklla.tinywyrlds.wyrldprint.interactive.client;

import com.github.cerealklla.tinywyrlds.wyrldprint.interactive.SetTagPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** Shift-right-click's own tag prompt -- one text field, same shape as {@code workbench.WyrldWorkbenchSaveScreen}. Submitting an empty field clears the tag entirely, not an error (an author changing their mind about a switch). */
public class TagPromptScreen extends Screen {

    private final BlockPos pos;
    private final String currentTag;
    private EditBox tagBox;

    public TagPromptScreen(BlockPos pos, String currentTag) {
        super(Component.literal("Tag this switch"));
        this.pos = pos;
        this.currentTag = currentTag;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 20;

        tagBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.literal("Tag")));
        tagBox.setMaxLength(64);
        if (!currentTag.isEmpty()) {
            tagBox.setValue(currentTag);
        }
        y += 28;

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 104, y, 96, 20).build());
    }

    private void save() {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new SetTagPayload(pos, tagBox.getValue())));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 40, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
