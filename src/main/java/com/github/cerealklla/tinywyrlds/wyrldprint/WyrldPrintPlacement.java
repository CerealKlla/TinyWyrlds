package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;

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

    public static void clearCraftingZone(ServerLevel level) {
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
    }
}
