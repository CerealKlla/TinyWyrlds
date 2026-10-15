package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The WyrldPrint Workbench's main menu -- the long-deferred "right-click to bring up a menu"
 * spec item, a real GUI front end over the exact same actions {@code debug.DebugCommands} already
 * exposed as chat commands (Save/Load/Clear/Validate All/Generate One-off/Regenerate). Purely
 * payload-driven, no container menu -- same convention as Blueprynts' {@code
 * ConstructionSiteScreen}. "Clear" and "Validate All" are direct one-shot actions from here; every
 * other button opens a small focused sub-screen, same split Blueprynts' {@code
 * ConstructionBoxActionsScreen} established.
 */
public class WyrldWorkbenchScreen extends Screen {

    private final OpenWyrldWorkbenchScreenPayload data;

    public WyrldWorkbenchScreen(OpenWyrldWorkbenchScreenPayload data) {
        super(Component.literal("Wyrld Workbench"));
        this.data = data;
    }

    public OpenWyrldWorkbenchScreenPayload data() {
        return data;
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int x = width / 2 - buttonWidth / 2;
        int y = 50;

        addRenderableWidget(Button.builder(Component.literal("Save..."),
                b -> Minecraft.getInstance().setScreen(new WyrldWorkbenchSaveScreen(this)))
                .bounds(x, y, buttonWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Load... (" + data.savedNames().size() + " saved)"),
                b -> Minecraft.getInstance().setScreen(new WyrldWorkbenchLoadScreen(this)))
                .bounds(x, y, buttonWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Clear Crafting Zone"), b -> send(new ClearCraftZonePayload(data.workbenchPos())))
                .bounds(x, y, buttonWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Validate All"), b -> send(new ValidateAllWyrldPrintsPayload(data.workbenchPos())))
                .bounds(x, y, buttonWidth, 20).build());
        y += 32;
        addRenderableWidget(Button.builder(Component.literal("Generate One-off..."),
                b -> Minecraft.getInstance().setScreen(new WyrldWorkbenchGenerateScreen(this)))
                .bounds(x, y, buttonWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Regenerate..."),
                b -> Minecraft.getInstance().setScreen(new WyrldWorkbenchRegenerateScreen(this)))
                .bounds(x, y, buttonWidth, 20).build());
        y += 32;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x, y, buttonWidth, 20).build());
    }

    /** "Clear"/"Validate All" close the menu after firing -- both report their result in chat
     *  rather than needing a refreshed screen state. */
    private void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
