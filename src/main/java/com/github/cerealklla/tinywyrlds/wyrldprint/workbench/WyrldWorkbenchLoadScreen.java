package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Load..." sub-screen -- pages through saved WyrldPrints one at a time (no preview-image system
 *  exists for these yet, unlike Blueprynts' Blueprints), same Prev/Next cycle-button shape as
 *  {@code blueprynts.construction.BlueprintPickerScreen}. Loading closes fully (the crafting zone
 *  itself is the result, there's nothing left in this screen to show). */
public class WyrldWorkbenchLoadScreen extends Screen {

    private final Screen parent;
    private final OpenWyrldWorkbenchScreenPayload data;
    private int index;
    private Button loadButton;

    public WyrldWorkbenchLoadScreen(WyrldWorkbenchScreen parent) {
        super(Component.literal("Load WyrldPrint"));
        this.parent = parent;
        this.data = parent.data();
    }

    @Override
    protected void init() {
        boolean hasEntries = !data.savedNames().isEmpty();
        int centerX = width / 2;
        int y = height / 2 - 10;

        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1)).bounds(centerX - 110, y, 20, 20).build()).active = hasEntries;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1)).bounds(centerX + 90, y, 20, 20).build()).active = hasEntries;

        loadButton = addRenderableWidget(Button.builder(Component.literal("Load"), b -> load()).bounds(centerX - 50, y + 28, 100, 20).build());
        loadButton.active = hasEntries;

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(centerX - 50, y + 52, 100, 20).build());
    }

    private void page(int delta) {
        int count = data.savedNames().size();
        if (count == 0) {
            return;
        }
        index = Math.floorMod(index + delta, count);
    }

    private void load() {
        if (data.savedNames().isEmpty()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new LoadWyrldPrintPayload(data.workbenchPos(), data.savedNames().get(index))));
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
        graphics.text(font, title, width / 2 - titleWidth / 2, 20, 0xFFFFFFFF);

        int centerX = width / 2;
        int y = height / 2 - 10;
        if (data.savedNames().isEmpty()) {
            String empty = "No saved WyrldPrints yet.";
            graphics.text(font, empty, centerX - font.width(empty) / 2, y + 5, 0xFFAAAAAA);
            return;
        }
        String name = data.savedNames().get(index) + " (" + data.savedTypes().get(index) + ")";
        String position = "(" + (index + 1) + " / " + data.savedNames().size() + ")";
        graphics.text(font, name, centerX - font.width(name) / 2, y + 5, 0xFFFFFFFF);
        graphics.text(font, position, centerX - font.width(position) / 2, y - 10, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
