package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointFill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Clears the WyrldPrint crafting zone and pastes a record back into it, anchored flush against the
 * cobblestone staging boundary (real user spec, 2026-10-14: "when WorldPrynts are loaded can you
 * please load them next to the cobblestone, not on the opposite side") rather than the crafting
 * zone's far west edge -- a fixed, reproducible location for "Load for editing," not yet a real
 * dungeon-generation placement (that needs rotation support, not built yet -- see decisions.md).
 */
public final class WyrldPrintPlacement {

    private WyrldPrintPlacement() {
    }

    /** The name of whatever saved WyrldPrint the crafting zone's current content corresponds to, if
     *  any -- shown as the Save screen's default name so re-saving an edited print doesn't require
     *  retyping it. Explicit user spec, 2026-10-15: "if we load one the UI should default to the
     *  name of the file we loaded. If we clear, that default can also be cleared." Global, not
     *  per-player -- the crafting zone itself is one shared space, same as everything else here. */
    private static String loadedName;

    public static java.util.Optional<String> loadedName() {
        return java.util.Optional.ofNullable(loadedName);
    }

    public static void setLoadedName(String name) {
        loadedName = name;
    }

    public static void clearCraftingZone(ServerLevel level) {
        loadedName = null;
        int minY = WyrldManager.BASELINE_Y - WyrldPrintArea.CAPTURE_BELOW_BASELINE;
        int maxY = WyrldManager.BASELINE_Y + WyrldPrintArea.CAPTURE_ABOVE_BASELINE;
        for (int x = WyrldPrintArea.CRAFT_MIN_X; x < WyrldPrintArea.CRAFT_MAX_X; x++) {
            for (int z = WyrldPrintArea.MIN_Z; z < WyrldPrintArea.MAX_Z; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    public static void loadIntoCraftingZone(ServerLevel level, WyrldPrintRecord record) {
        clearCraftingZone(level);
        loadedName = record.name();
        // Anchored so the content's own far (highest-relForward) edge lands flush against the
        // boundary, i.e. right next to the cobblestone staging strip, instead of clear across the
        // void at the crafting zone's far west edge.
        int anchorX = WyrldPrintArea.BOUNDARY_X - record.forwardSize();
        // Lowest Y first -- a door's upper half (or any other support-dependent block) must find
        // its support already placed, same ordering lesson Blueprynts' own loadBlueprint learned.
        List<WyrldPrintCell> sorted = new ArrayList<>(record.cells());
        sorted.sort((a, b) -> Integer.compare(a.relY(), b.relY()));
        for (WyrldPrintCell cell : sorted) {
            BlockPos pos = new BlockPos(anchorX + cell.relForward(), WyrldManager.BASELINE_Y + cell.relY(), cell.relLateral());
            level.setBlock(pos, cell.state(), 3);
        }

        // Re-apply the glass-fill visual aid around every connector point -- real user report,
        // 2026-10-14: "when I loaded a print with a 3x3 connector the door is here but no glass."
        // A saved record's own cells never include the glass (WyrldPrintCapture clears it back to
        // air at save time, same "don't bake the suggestion into real content" precedent as the
        // generator never stamping it either -- see stamp()'s own doc), so loading a print for
        // editing needs to redo the fill itself, same call each block's own setPlacedBy makes when
        // a player places one fresh.
        for (WyrldPrintPoint point : record.points()) {
            BlockPos basePos = new BlockPos(anchorX + point.relForward(), WyrldManager.BASELINE_Y + point.relY(), point.relLateral());
            if (point.wall()) {
                point.facing().ifPresent(facing -> ConnectorPointFill.fillWall(level, basePos, facing, point.size()));
            } else {
                ConnectorPointFill.fillFloor(level, basePos, point.size());
            }
        }

        // Same precedent for every Spawn Location marker -- its own cell is cleared to air at
        // capture time too (see WyrldPrintCapture's own doc), so put the real gold marker block
        // back for editing. Multiple are expected on a real ENTRANCE (one per party member) --
        // explicit user spec, 2026-10-14: "If a party arrives they all need their own spawn point."
        for (WyrldPrintSpawn spawn : record.spawnPoints()) {
            BlockPos pos = new BlockPos(anchorX + spawn.relForward(), WyrldManager.BASELINE_Y + spawn.relY(), spawn.relLateral());
            level.setBlock(pos, ModBlocks.SPAWN_LOCATION.get().defaultBlockState(), 3);
        }
    }
}
