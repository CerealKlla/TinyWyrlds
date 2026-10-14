package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Explicit user spec (2026-10-14): "When placed, will fill around with the appropriate number of
 * colored glass blocks to indicate the area where the connector will attach." Only ever fills
 * currently-air cells (never overwrites real content) -- these are placed in the void WyrldPrint
 * crafting zone, so in practice that's everything except the connector block itself.
 */
public final class ConnectorPointFill {

    private ConnectorPointFill() {
    }

    /** {@code doorBasePos} is the door's lower half. Width runs along the wall (perpendicular to
     *  {@code facing}, via {@link Direction#getClockWise()}); height runs straight up from the
     *  base, since the door already occupies the center column's own two cells (y=0, y=1). */
    public static void fillWall(Level level, BlockPos doorBasePos, Direction facing, ConnectorPointSize size) {
        int footprint = size.footprint();
        if (footprint <= 1) {
            return;
        }
        int half = footprint / 2;
        Direction side = facing.getClockWise();
        BlockState glass = size.glassBlock().defaultBlockState();
        for (int s = -half; s <= half; s++) {
            for (int y = 0; y < footprint; y++) {
                if (s == 0 && (y == 0 || y == 1)) {
                    continue; // the door's own footprint
                }
                BlockPos pos = doorBasePos.relative(side, s).above(y);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, glass, 3);
                }
            }
        }
    }

    /** {@code trapdoorPos} is the trapdoor's own position. A flat NxN square on the X/Z plane,
     *  centered on it, with the trapdoor's own cell left alone. */
    public static void fillFloor(Level level, BlockPos trapdoorPos, ConnectorPointSize size) {
        int footprint = size.footprint();
        if (footprint <= 1) {
            return;
        }
        int half = footprint / 2;
        BlockState glass = size.glassBlock().defaultBlockState();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos pos = trapdoorPos.offset(dx, 0, dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, glass, 3);
                }
            }
        }
    }
}
