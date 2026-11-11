package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Manage Tags..." sub-screen, gamemaster-only (see {@code WyrldWorkbenchScreen#init}'s own gate)
 *  -- one row per pending tag request: the text, an {@code EditBox} pre-filled with it so the admin
 *  can correct it before approving, an Approve button, and a Deny button. Closes fully on either
 *  action (matching every other Workbench sub-screen's convention) -- the main screen gets resent
 *  fresh afterward ({@code TinyWyrldsMod}'s own handler), so re-opening this one shows the updated
 *  pending list rather than this screen trying to patch its own row list in place. */
public class WyrldWorkbenchTagReviewScreen extends Screen {

    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private final BlockPos workbenchPos;
    private final List<String> pendingTags;

    public WyrldWorkbenchTagReviewScreen(WyrldWorkbenchScreen parent) {
        super(Component.literal("Manage Tags"));
        this.parent = parent;
        this.workbenchPos = parent.data().workbenchPos();
        this.pendingTags = parent.data().pendingTags();
    }

    @Override
    protected void init() {
        int x = width / 2 - 140;
        int y = height / 2 - (pendingTags.size() * ROW_HEIGHT) / 2 - 20;

        for (String tag : pendingTags) {
            EditBox editBox = addRenderableWidget(new EditBox(font, x, y, 150, 20, Component.literal("Tag")));
            editBox.setValue(tag);
            addRenderableWidget(Button.builder(Component.literal("Approve"), b -> approve(tag, editBox.getValue()))
                    .bounds(x + 156, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Deny"), b -> deny(tag))
                    .bounds(x + 230, y, 60, 20).build());
            y += ROW_HEIGHT;
        }

        y += 10;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x, y, 290, 20).build());
    }

    private void approve(String originalTag, String approvedTagInput) {
        String approvedTag = approvedTagInput.isBlank() ? originalTag : approvedTagInput.trim();
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new ApproveTagPayload(workbenchPos, originalTag, approvedTag)));
        onClose();
    }

    private void deny(String originalTag) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new DenyTagPayload(workbenchPos, originalTag)));
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - (pendingTags.size() * ROW_HEIGHT) / 2 - 40, 0xFFFFFFFF);
        if (pendingTags.isEmpty()) {
            Component none = Component.literal("No pending tag requests.");
            graphics.text(font, none, width / 2 - font.width(none) / 2, height / 2 - 10, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
