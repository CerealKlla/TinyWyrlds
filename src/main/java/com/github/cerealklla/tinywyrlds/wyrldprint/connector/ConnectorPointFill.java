package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Explicit user spec (2026-10-14): "When placed, will fill around with the appropriate number of
 * colored glass blocks to indicate the area where the connector will attach." Only ever fills
 * currently-air cells (never overwrites real content) -- these are placed in the void WyrldPrint
 * crafting zone, so in practice that's everything except the connector block itself.
 *
 * <p>{@code clearWall}/{@code clearFloor} are the symmetric counterpart, added 2026-10-14 after a
 * real live report ("when I break a connector it doesn't break the surrounding glass") -- removes
 * only cells still holding that exact size's own glass block (mirroring {@code
 * WyrldPrintCapture}'s own "leave it alone if the player built over it" caution), called from each
 * connector block's {@code affectNeighborsAfterRemoval}.
 */
public final class ConnectorPointFill {

    private ConnectorPointFill() {
    }

    /** {@code doorBasePos} is the door's lower half. Width runs along the wall (perpendicular to
     *  {@code facing}, via {@link Direction#getClockWise()}); height runs straight up from the
     *  base, since the door already occupies the center column's own two cells (y=0, y=1). */
    public static void fillWall(Level level, BlockPos doorBasePos, Direction facing, ConnectorPointSize size) {
        forEachWallFootprintCell(doorBasePos, facing, size, pos -> {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, size.glassBlock().defaultBlockState(), 3);
            }
        });
    }

    /** {@code trapdoorPos} is the trapdoor's own position. A flat NxN square on the X/Z plane,
     *  centered on it, with the trapdoor's own cell left alone. */
    public static void fillFloor(Level level, BlockPos trapdoorPos, ConnectorPointSize size) {
        forEachFloorFootprintCell(trapdoorPos, size, pos -> {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, size.glassBlock().defaultBlockState(), 3);
            }
        });
    }

    /** Clears any cell in the wall footprint still holding this size's own glass block back to
     *  air -- leaves anything else (including a different glass color, or real content the
     *  player built over the suggestion) untouched. */
    public static void clearWall(Level level, BlockPos doorBasePos, Direction facing, ConnectorPointSize size) {
        forEachWallFootprintCell(doorBasePos, facing, size, pos -> clearIfStillGlass(level, pos, size.glassBlock()));
    }

    /** Floor counterpart of {@link #clearWall}. */
    public static void clearFloor(Level level, BlockPos trapdoorPos, ConnectorPointSize size) {
        forEachFloorFootprintCell(trapdoorPos, size, pos -> clearIfStillGlass(level, pos, size.glassBlock()));
    }

    private static void clearIfStillGlass(Level level, BlockPos pos, Block glass) {
        if (level.getBlockState(pos).getBlock() == glass) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void forEachWallFootprintCell(BlockPos doorBasePos, Direction facing, ConnectorPointSize size, java.util.function.Consumer<BlockPos> action) {
        int footprint = size.footprint();
        if (footprint <= 1) {
            return;
        }
        int half = footprint / 2;
        Direction side = facing.getClockWise();
        for (int s = -half; s <= half; s++) {
            for (int y = 0; y < footprint; y++) {
                if (s == 0 && (y == 0 || y == 1)) {
                    continue; // the door's own footprint
                }
                action.accept(doorBasePos.relative(side, s).above(y));
            }
        }
    }

    private static void forEachFloorFootprintCell(BlockPos trapdoorPos, ConnectorPointSize size, java.util.function.Consumer<BlockPos> action) {
        int footprint = size.footprint();
        if (footprint <= 1) {
            return;
        }
        int half = footprint / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                action.accept(trapdoorPos.offset(dx, 0, dz));
            }
        }
    }
}
