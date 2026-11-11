package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Save..." sub-screen -- a name field, a Type toggle (ROOM/CONNECTOR/ENTRANCE/TUTORIAL, same
 *  shape {@code debug.DebugCommands#saveWyrldPrint} already accepts), a tag picker, and a custom-tag
 *  text box (the Tag system, added 2026-11-11). Closes fully on Save (matching Blueprynts' {@code
 *  ConstructionBoxActionsScreen} convention) since the result is reported in chat, not reflected
 *  back into this screen. */
public class WyrldWorkbenchSaveScreen extends Screen {

    private static final String[] TYPES = {"room", "connector", "entrance", "tutorial"};
    private static final int TAG_BUTTON_WIDTH = 90;
    private static final int TAG_BUTTON_HEIGHT = 18;
    private static final int TAG_COLUMNS = 4;

    private final Screen parent;
    private final BlockPos workbenchPos;
    private final String defaultName;
    private final List<String> approvedTags;
    private final Set<String> selectedTags = new LinkedHashSet<>();
    private EditBox nameBox;
    private EditBox customTagBox;
    private int typeIndex = 0;
    private Button typeButton;

    public WyrldWorkbenchSaveScreen(WyrldWorkbenchScreen parent) {
        super(Component.literal("Save WyrldPrint"));
        this.parent = parent;
        this.workbenchPos = parent.data().workbenchPos();
        this.defaultName = parent.data().loadedName();
        this.approvedTags = parent.data().approvedTags();
        // Real bug, 2026-11-11: this used to always start at ROOM (index 0) regardless of what was
        // actually loaded, so re-saving a loaded CONNECTOR/ENTRANCE/TUTORIAL print without
        // remembering to cycle the toggle back silently overwrote its real type.
        String loadedType = parent.data().loadedType();
        if (!loadedType.isEmpty()) {
            int index = java.util.Arrays.asList(TYPES).indexOf(loadedType);
            if (index >= 0) {
                this.typeIndex = index;
            }
        }
        // Real bug, 2026-11-11, found right after the one above: the picker had no concept of
        // "which tags does the currently-loaded print already have," so re-saving a tagged print
        // without re-selecting every tag by hand silently wiped them. Includes any tag the print
        // has even if it's not currently approved (still pending review) -- that tag just has no
        // checkbox to show it checked, but it must still survive an unrelated re-save untouched.
        this.selectedTags.addAll(parent.data().loadedTags());
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 100;

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
        y += 28;

        // Tag picker -- a plain flow-wrapped toggle grid rather than a scrollable list; the
        // approved vocabulary starts at 13 built-ins and grows only by deliberate admin approval,
        // so this stays legible without scrolling machinery for the foreseeable term.
        int tagGridX = width / 2 - (TAG_COLUMNS * TAG_BUTTON_WIDTH) / 2;
        int column = 0;
        for (String tag : approvedTags) {
            int buttonX = tagGridX + column * TAG_BUTTON_WIDTH;
            Button tagButton = addRenderableWidget(Button.builder(tagLabel(tag), b -> toggleTag(tag, b))
                    .bounds(buttonX, y, TAG_BUTTON_WIDTH - 2, TAG_BUTTON_HEIGHT).build());
            tagButton.active = true;
            column++;
            if (column >= TAG_COLUMNS) {
                column = 0;
                y += TAG_BUTTON_HEIGHT + 2;
            }
        }
        if (column != 0) {
            y += TAG_BUTTON_HEIGHT + 2;
        }
        y += 6;

        customTagBox = addRenderableWidget(new EditBox(font, x, y, 200, 20,
                Component.literal("New tags (comma-separated)")));
        y += 28;

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 104, y, 96, 20).build());
    }

    private Component tagLabel(String tag) {
        return Component.literal((selectedTags.contains(tag) ? "[x] " : "[ ] ") + tag);
    }

    private void toggleTag(String tag, Button button) {
        if (!selectedTags.remove(tag)) {
            selectedTags.add(tag);
        }
        button.setMessage(tagLabel(tag));
    }

    private void cycleType() {
        typeIndex = (typeIndex + 1) % TYPES.length;
        typeButton.setMessage(Component.literal("Type: " + TYPES[typeIndex]));
    }

    private void save() {
        if (nameBox.getValue().isBlank()) {
            return;
        }
        List<String> finalTags = mergedTags();
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new SaveWyrldPrintPayload(workbenchPos, nameBox.getValue(), TYPES[typeIndex], finalTags)));
        Minecraft.getInstance().setScreen(null);
    }

    /** Every selected approved tag, union the parsed/trimmed custom-box tokens, deduplicated --
     *  the server doesn't need to know which of these are "new" vs "already approved," it just
     *  checks each one against the shared vocabulary itself. */
    private List<String> mergedTags() {
        Set<String> merged = new LinkedHashSet<>(selectedTags);
        for (String token : customTagBox.getValue().split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                merged.add(trimmed);
            }
        }
        return new ArrayList<>(merged);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 120, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
