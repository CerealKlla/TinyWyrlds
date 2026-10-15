package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Save..." sub-screen -- a name field plus a Type toggle (ROOM/CONNECTOR/ENTRANCE), same shape
 *  {@code debug.DebugCommands#saveWyrldPrint} already accepts. Closes fully on Save (matching
 *  Blueprynts' {@code ConstructionBoxActionsScreen} convention) since the result is reported in
 *  chat, not reflected back into this screen. */
public class WyrldWorkbenchSaveScreen extends Screen {

    private static final String[] TYPES = {"room", "connector", "entrance"};

    private final Screen parent;
    private final BlockPos workbenchPos;
    private final String defaultName;
    private EditBox nameBox;
    private int typeIndex = 0;
    private Button typeButton;

    public WyrldWorkbenchSaveScreen(WyrldWorkbenchScreen parent) {
        super(Component.literal("Save WyrldPrint"));
        this.parent = parent;
        this.workbenchPos = parent.data().workbenchPos();
        this.defaultName = parent.data().loadedName();
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 40;

        nameBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.literal("Name")));
        // Defaults to whatever's currently loaded in the crafting zone, if anything -- explicit
        // user spec, 2026-10-15: "if we load one the UI should default to the name of the file we
        // loaded. If we clear, that default can also be cleared." Still a plain editable field, not
        // read-only -- this is just a starting point, not a lock.
        if (!defaultName.isEmpty()) {
            nameBox.setValue(defaultName);
        }
        y += 28;

        typeButton = addRenderableWidget(Button.builder(Component.literal("Type: " + TYPES[typeIndex]), b -> cycleType())
                .bounds(x, y, 200, 20).build());
        y += 32;

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 104, y, 96, 20).build());
    }

    private void cycleType() {
        typeIndex = (typeIndex + 1) % TYPES.length;
        typeButton.setMessage(Component.literal("Type: " + TYPES[typeIndex]));
    }

    private void save() {
        if (nameBox.getValue().isBlank()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new SaveWyrldPrintPayload(workbenchPos, nameBox.getValue(), TYPES[typeIndex])));
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
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 60, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
