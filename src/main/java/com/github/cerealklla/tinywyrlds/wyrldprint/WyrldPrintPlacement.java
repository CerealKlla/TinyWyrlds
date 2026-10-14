package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Clears the WyrldPrint crafting zone and pastes a record back into it, always anchored at the
 * crafting zone's own west edge ({@code CRAFT_MIN_X}), centered in Z -- a fixed, reproducible
 * location for "Load for editing," not yet a real dungeon-generation placement (that needs
 * rotation support, not built yet -- see decisions.md).
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
        // Lowest Y first -- a door's upper half (or any other support-dependent block) must find
        // its support already placed, same ordering lesson Blueprynts' own loadBlueprint learned.
        List<WyrldPrintCell> sorted = new ArrayList<>(record.cells());
        sorted.sort((a, b) -> Integer.compare(a.relY(), b.relY()));
        for (WyrldPrintCell cell : sorted) {
            BlockPos pos = new BlockPos(WyrldPrintArea.CRAFT_MIN_X + cell.relForward(), WyrldManager.BASELINE_Y + cell.relY(), cell.relLateral());
            level.setBlock(pos, cell.state(), 3);
        }
    }
}
