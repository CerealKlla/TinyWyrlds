package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Regenerate..." sub-screen -- a dungeon-id text field, same UUID string {@code /tinywyrlds
 *  regenerate <id>} already takes (reported back to the player via chat/the server log, no list of
 *  known ids exists client-side to pick from yet). */
public class WyrldWorkbenchRegenerateScreen extends Screen {

    private final Screen parent;
    private final BlockPos workbenchPos;
    private EditBox idBox;

    public WyrldWorkbenchRegenerateScreen(WyrldWorkbenchScreen parent) {
        super(Component.literal("Regenerate Dungeon"));
        this.parent = parent;
        this.workbenchPos = parent.data().workbenchPos();
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 30;

        idBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.literal("Dungeon id")));
        y += 32;

        addRenderableWidget(Button.builder(Component.literal("Regenerate"), b -> regenerate()).bounds(x, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 104, y, 96, 20).build());
    }

    private void regenerate() {
        if (idBox.getValue().isBlank()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new RegenerateDungeonPayload(workbenchPos, idBox.getValue().trim())));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 50, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
