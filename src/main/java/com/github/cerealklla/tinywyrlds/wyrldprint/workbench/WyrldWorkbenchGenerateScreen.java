package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Generate One-off..." sub-screen -- minRooms/maxRooms fields, same two integers {@code
 *  /tinywyrlds generate <minRooms> <maxRooms>} already takes. Closes fully on Generate: a
 *  successful generation teleports the player straight into the new Wyrld, so there's nothing
 *  left in this screen to show afterward. */
public class WyrldWorkbenchGenerateScreen extends Screen {

    private final Screen parent;
    private final BlockPos workbenchPos;
    private EditBox minBox;
    private EditBox maxBox;

    public WyrldWorkbenchGenerateScreen(WyrldWorkbenchScreen parent) {
        super(Component.literal("Generate One-off"));
        this.parent = parent;
        this.workbenchPos = parent.data().workbenchPos();
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 40;

        minBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.literal("Min rooms")));
        minBox.setValue("1");
        y += 24;
        maxBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.literal("Max rooms")));
        maxBox.setValue("5");
        y += 32;

        addRenderableWidget(Button.builder(Component.literal("Generate"), b -> generate()).bounds(x, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 104, y, 96, 20).build());
    }

    private void generate() {
        Integer min = parse(minBox.getValue());
        Integer max = parse(maxBox.getValue());
        if (min == null || max == null || min < 0 || max < min) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new GenerateOneOffPayload(workbenchPos, min, max)));
        Minecraft.getInstance().setScreen(null);
    }

    private static Integer parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 60, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
