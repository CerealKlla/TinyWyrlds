package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.FloorConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.WallConnectorPointBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Scans the WyrldPrint crafting zone's current content into a {@link WyrldPrintRecord}. No
 * "Begin Design"/footprint-marking ceremony the way Blueprynts' Construction Site needs one --
 * the crafting zone is void to start with (nothing to level/clear), so the real footprint is
 * simply whatever bounding box the built content's own non-air cells (plus connector points)
 * turn out to occupy, trimmed tight. Coordinates are stored relative to {@link
 * WyrldPrintArea#FRONT_DIRECTION} -- forward 0-based from the bounding box's own low edge,
 * lateral centered on 0 -- see {@link WyrldPrintCell}'s own doc.
 */
public final class WyrldPrintCapture {

    private WyrldPrintCapture() {
    }

    /** @return the captured record, or an error message (never both). */
    public static CaptureResult capture(ServerLevel level, String name, String author, WyrldPrintType type) {
        int minY = WyrldManager.BASELINE_Y - WyrldPrintArea.CAPTURE_BELOW_BASELINE;
        int maxY = WyrldManager.BASELINE_Y + WyrldPrintArea.CAPTURE_ABOVE_BASELINE;

        // Pass 1: find every connector point's lower/base cell (wall points track their own lower
        // half only, to avoid double-detecting the same point at its upper half too), plus any
        // Spawn Location marker.
        List<DetectedPoint> detectedPoints = new ArrayList<>();
        List<BlockPos> detectedSpawns = new ArrayList<>();
        for (int x = WyrldPrintArea.CRAFT_MIN_X; x < WyrldPrintArea.CRAFT_MAX_X; x++) {
            for (int z = WyrldPrintArea.MIN_Z; z < WyrldPrintArea.MAX_Z; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof WallConnectorPointBlock wall) {
                        if (state.getValue(WallConnectorPointBlock.HALF) == DoubleBlockHalf.LOWER) {
                            detectedPoints.add(new DetectedPoint(pos, true, wall.size(), state.getValue(WallConnectorPointBlock.FACING)));
                        }
                    } else if (state.getBlock() instanceof FloorConnectorPointBlock floor) {
                        detectedPoints.add(new DetectedPoint(pos, false, floor.size(), null));
                    } else if (state.getBlock() instanceof SpawnLocationBlock) {
                        detectedSpawns.add(pos);
                    }
                }
            }
        }

        if (detectedPoints.size() < type.minimumConnectorPoints()) {
            return CaptureResult.error("A " + type.name() + " needs at least " + type.minimumConnectorPoints()
                    + " connector point(s) -- only found " + detectedPoints.size() + ".");
        }
        if (type == WyrldPrintType.ENTRANCE && detectedSpawns.isEmpty()) {
            return CaptureResult.error("An ENTRANCE needs at least one Spawn Location marker -- found none.");
        }

        // Positions that are this point's own "suggested fill" glass and should capture as air
        // instead of the glass block, unless the player already built over them -- see this
        // class's own doc.
        Map<BlockPos, Boolean> clearToAir = new HashMap<>();
        for (DetectedPoint point : detectedPoints) {
            for (BlockPos fillPos : point.fillFootprint()) {
                BlockState there = level.getBlockState(fillPos);
                if (there.getBlock() == point.size.glassBlock()) {
                    clearToAir.put(fillPos, true);
                }
            }
        }
        // The Spawn Location marker itself is a pure build-time aid, same precedent -- never
        // stamped as a real block in a generated dungeon (OneOffGenerator's own floor cell, already
        // captured beneath it, is what a player actually lands on).
        for (BlockPos spawnPos : detectedSpawns) {
            clearToAir.put(spawnPos, true);
        }

        // Pass 2: tight bounding box over every non-air cell (after the glass-to-air substitution)
        // and every detected point's own position.
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int boxMinY = Integer.MAX_VALUE, boxMaxY = Integer.MIN_VALUE;
        boolean anyContent = false;
        for (int x = WyrldPrintArea.CRAFT_MIN_X; x < WyrldPrintArea.CRAFT_MAX_X; x++) {
            for (int z = WyrldPrintArea.MIN_Z; z < WyrldPrintArea.MAX_Z; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean nowAir = clearToAir.containsKey(pos);
                    boolean hasContent = !nowAir && !level.getBlockState(pos).isAir();
                    if (hasContent) {
                        anyContent = true;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                        boxMinY = Math.min(boxMinY, y);
                        boxMaxY = Math.max(boxMaxY, y);
                    }
                }
            }
        }
        for (DetectedPoint point : detectedPoints) {
            minX = Math.min(minX, point.pos.getX());
            maxX = Math.max(maxX, point.pos.getX());
            minZ = Math.min(minZ, point.pos.getZ());
            maxZ = Math.max(maxZ, point.pos.getZ());
            boxMinY = Math.min(boxMinY, point.pos.getY());
            // +1 cell of real captured headroom above the point's own top cell (the upper door
            // half for a wall point, the point's own single cell for a floor point) -- without
            // this, a connector point sitting at the very top of a piece's own bounding box (e.g.
            // the upper end of a vertical shaft, with nothing else built higher) has no captured
            // cell at all directly above it. WyrldPrintValidator's two-tall clearance check then
            // can't tell "genuinely open" apart from "outside what was ever captured" and
            // conservatively treats both as blocked -- a real false failure found live on every
            // Ladder Shaft/Trapdoor Shaft (floor points) piece, even though the real stamped world
            // has open air there the whole time.
            boxMaxY = Math.max(boxMaxY, point.pos.getY() + (point.wall ? 2 : 1));
        }
        for (BlockPos spawnPos : detectedSpawns) {
            minX = Math.min(minX, spawnPos.getX());
            maxX = Math.max(maxX, spawnPos.getX());
            minZ = Math.min(minZ, spawnPos.getZ());
            maxZ = Math.max(maxZ, spawnPos.getZ());
            boxMinY = Math.min(boxMinY, spawnPos.getY());
            boxMaxY = Math.max(boxMaxY, spawnPos.getY());
        }
        if (!anyContent && detectedPoints.isEmpty() && detectedSpawns.isEmpty()) {
            return CaptureResult.error("Nothing built in the crafting zone to save.");
        }

        int originLateral = (minZ + maxZ) / 2;

        List<WyrldPrintCell> cells = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = boxMinY; y <= boxMaxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = clearToAir.containsKey(pos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
                    cells.add(new WyrldPrintCell(x - minX, z - originLateral, y - WyrldManager.BASELINE_Y, state));
                }
            }
        }

        List<WyrldPrintPoint> points = new ArrayList<>();
        for (DetectedPoint point : detectedPoints) {
            points.add(new WyrldPrintPoint(point.pos.getX() - minX, point.pos.getZ() - originLateral,
                    point.pos.getY() - WyrldManager.BASELINE_Y, point.wall, point.size, Optional.ofNullable(point.facing)));
        }

        List<WyrldPrintSpawn> spawnPoints = new ArrayList<>();
        for (BlockPos spawnPos : detectedSpawns) {
            spawnPoints.add(new WyrldPrintSpawn(spawnPos.getX() - minX, spawnPos.getZ() - originLateral,
                    spawnPos.getY() - WyrldManager.BASELINE_Y));
        }

        WyrldPrintRecord record = new WyrldPrintRecord(WyrldPrintRecord.CURRENT_TEMPLATE_VERSION, name, author, type,
                maxX - minX + 1, maxZ - minZ + 1, boxMinY - WyrldManager.BASELINE_Y, boxMaxY - WyrldManager.BASELINE_Y,
                cells, points, spawnPoints);
        return CaptureResult.success(record);
    }

    private record DetectedPoint(BlockPos pos, boolean wall, ConnectorPointSize size, net.minecraft.core.Direction facing) {
        List<BlockPos> fillFootprint() {
            List<BlockPos> result = new ArrayList<>();
            int footprint = size.footprint();
            if (footprint <= 1) {
                return result;
            }
            int half = footprint / 2;
            if (wall) {
                net.minecraft.core.Direction side = facing.getClockWise();
                for (int s = -half; s <= half; s++) {
                    for (int y = 0; y < footprint; y++) {
                        if (s == 0 && (y == 0 || y == 1)) {
                            continue;
                        }
                        result.add(pos.relative(side, s).above(y));
                    }
                }
            } else {
                for (int dx = -half; dx <= half; dx++) {
                    for (int dz = -half; dz <= half; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        result.add(pos.offset(dx, 0, dz));
                    }
                }
            }
            return result;
        }
    }

    public record CaptureResult(Optional<WyrldPrintRecord> record, Optional<String> error) {
        static CaptureResult success(WyrldPrintRecord record) {
            return new CaptureResult(Optional.of(record), Optional.empty());
        }

        static CaptureResult error(String message) {
            return new CaptureResult(Optional.empty(), Optional.of(message));
        }
    }
}
