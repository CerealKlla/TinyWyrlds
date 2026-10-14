package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.space.FreeRect;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintCell;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintPoint;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Real connector-aware "Generate One-off": starts from a random ROOM, then repeatedly picks an
 * open connector point ("frontier"), finds another saved WyrldPrint with a compatible point
 * (same wall/floor kind; a wall point also needs an opposite {@link WyrldPrintPoint#facing()}
 * once rotated -- see that field's own doc for the outward-facing convention every sample piece
 * follows), tries all 4 {@link Rotation}s until one lines the two points up with no bounding-box
 * overlap against anything already placed, and stamps it in. Keeps going until the target room
 * count is reached or every frontier dead-ends. A CONNECTOR piece is preferred between two rooms
 * (65% of the time, when any are saved) purely for variety; direct room-to-room is also valid.
 *
 * <p>Everything is planned in a local (forward, lateral, Y) space first -- no real block is
 * touched until the whole layout is decided -- then translated once into a freshly reserved Wyrld
 * sized to fit the final bounding box. Final stamping sorts every cell from every piece by
 * ascending world Y together (not just within one piece), so a door's upper half always finds its
 * support already placed regardless of which piece placed it.
 *
 * <p><b>Honest scope</b>: this does not yet read a WyrldRecipe (loot pools, quest tie-ins -- none
 * of that exists), and doesn't try to match {@link com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize}
 * between two points -- any wall point can join any other wall point regardless of size, same for
 * floor. A size-aware match (and a recipe-driven room/connector pool) is real, flagged follow-up
 * work, not an oversight.
 */
public final class OneOffGenerator {

    private OneOffGenerator() {
    }

    private static final int MARGIN = 5;
    private static final double PREFER_CONNECTOR_CHANCE = 0.65;
    private static final Rotation[] ALL_ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

    /** @return the created Wyrld instance, or {@code null} if no ROOM WyrldPrints are saved at all. */
    public static WyrldInstanceRecord generate(MinecraftServer server, int minRooms, int maxRooms) {
        List<WyrldPrintRecord> rooms = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ROOM).toList();
        List<WyrldPrintRecord> connectors = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.CONNECTOR).toList();
        if (rooms.isEmpty()) {
            return null;
        }

        RandomSource random = RandomSource.create();
        int lowRooms = Math.min(minRooms, maxRooms);
        int highRooms = Math.max(minRooms, maxRooms);
        int targetRooms = lowRooms + random.nextInt(highRooms - lowRooms + 1);

        List<Placement> placements = new ArrayList<>();
        List<Frontier> openFrontiers = new ArrayList<>();

        Placement first = new Placement(rooms.get(random.nextInt(rooms.size())), Rotation.NONE, 0, 0, 0);
        placements.add(first);
        addFrontiers(openFrontiers, first, null);
        int roomsPlaced = 1;

        int attempts = 0;
        int attemptBudget = Math.max(50, targetRooms * 60);
        while (roomsPlaced < targetRooms && !openFrontiers.isEmpty() && attempts < attemptBudget) {
            attempts++;
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));

            Placement placed = null;
            if (!connectors.isEmpty() && random.nextDouble() < PREFER_CONNECTOR_CHANCE) {
                placed = tryPlace(frontier, shuffled(connectors, random), placements, random);
            }
            if (placed == null) {
                placed = tryPlace(frontier, shuffled(rooms, random), placements, random);
            }
            if (placed == null) {
                continue; // dead end -- the point stays a real, closed, unused door/trapdoor.
            }
            placements.add(placed);
            addFrontiers(openFrontiers, placed, frontier);
            if (placed.record().type() == WyrldPrintType.ROOM) {
                roomsPlaced++;
            }
        }

        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: placed {} piece(s) ({} room(s), target {}) in {} attempt(s)",
                placements.size(), roomsPlaced, targetRooms, attempts);
        return stamp(server, placements);
    }

    /** Tries every candidate (in the given, already-shuffled order) and every rotation, returning
     *  the first placement that both matches {@code frontier} and doesn't collide with anything
     *  already placed. */
    private static Placement tryPlace(Frontier frontier, List<WyrldPrintRecord> candidates, List<Placement> existing, RandomSource random) {
        for (WyrldPrintRecord candidate : candidates) {
            List<WyrldPrintPoint> ownPoints = shuffled(candidate.points().stream().filter(p -> p.wall() == frontier.wall()).toList(), random);
            for (WyrldPrintPoint point : ownPoints) {
                for (Rotation rotation : ALL_ROTATIONS) {
                    if (frontier.wall()) {
                        if (point.facing().isEmpty() || frontier.facing().isEmpty()) {
                            continue;
                        }
                        if (rotation.rotate(point.facing().get()) != frontier.facing().get().getOpposite()) {
                            continue;
                        }
                    }
                    int[] rotatedFL = rotateForwardLateral(point.relForward(), point.relLateral(), rotation);
                    int originForward = frontier.worldForward() - rotatedFL[0];
                    int originLateral = frontier.worldLateral() - rotatedFL[1];
                    int originY = frontier.worldY() - point.relY();
                    Placement candidatePlacement = new Placement(candidate, rotation, originForward, originLateral, originY);
                    if (!collidesWithAny(candidatePlacement, existing)) {
                        return candidatePlacement;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Two genuinely connected pieces always share exactly one cell's worth of seam along
     * whichever axis their matched connector point sits on -- that cell is each piece's own door/
     * trapdoor, counted as part of both pieces' own bounding box (see {@link #localBounds}), so a
     * plain AABB overlap test would always flag a valid connection as a false collision. Shrinking
     * each box by 1 on every horizontal/vertical side before comparing absorbs exactly that
     * expected seam, while still catching any real multi-cell interior overlap.
     */
    private static boolean collidesWithAny(Placement placement, List<Placement> existing) {
        int[] box = shrunkForCollision(worldBoundingBox(placement));
        for (Placement other : existing) {
            int[] otherBox = shrunkForCollision(worldBoundingBox(other));
            boolean overlapForward = box[0] < otherBox[1] && otherBox[0] < box[1];
            boolean overlapLateral = box[2] < otherBox[3] && otherBox[2] < box[3];
            boolean overlapY = box[4] < otherBox[5] && otherBox[4] < box[5];
            if (overlapForward && overlapLateral && overlapY) {
                return true;
            }
        }
        return false;
    }

    private static int[] shrunkForCollision(int[] box) {
        int[] shrunk = box.clone();
        for (int axis = 0; axis < 3; axis++) {
            int lo = axis * 2, hi = axis * 2 + 1;
            if (shrunk[lo] + 1 < shrunk[hi] - 1) {
                shrunk[lo] += 1;
                shrunk[hi] -= 1;
            }
        }
        return shrunk;
    }

    /** {@code [minForward, maxForwardExclusive, minLateral, maxLateralExclusive, minY, maxYExclusive]} in world space. */
    private static int[] worldBoundingBox(Placement placement) {
        int[] local = localBounds(placement.record());
        int[] c1 = rotateForwardLateral(local[0], local[2], placement.rotation());
        int[] c2 = rotateForwardLateral(local[1], local[2], placement.rotation());
        int[] c3 = rotateForwardLateral(local[0], local[3], placement.rotation());
        int[] c4 = rotateForwardLateral(local[1], local[3], placement.rotation());
        int minF = Math.min(Math.min(c1[0], c2[0]), Math.min(c3[0], c4[0]));
        int maxF = Math.max(Math.max(c1[0], c2[0]), Math.max(c3[0], c4[0]));
        int minL = Math.min(Math.min(c1[1], c2[1]), Math.min(c3[1], c4[1]));
        int maxL = Math.max(Math.max(c1[1], c2[1]), Math.max(c3[1], c4[1]));
        return new int[]{
                placement.originForward() + minF, placement.originForward() + maxF + 1,
                placement.originLateral() + minL, placement.originLateral() + maxL + 1,
                placement.originY() + placement.record().minY(), placement.originY() + placement.record().maxY() + 1
        };
    }

    /** {@code [minForward, maxForward, minLateral, maxLateral]}, scanned once from cells+points. */
    private static int[] localBounds(WyrldPrintRecord record) {
        int minF = Integer.MAX_VALUE, maxF = Integer.MIN_VALUE, minL = Integer.MAX_VALUE, maxL = Integer.MIN_VALUE;
        for (WyrldPrintCell cell : record.cells()) {
            minF = Math.min(minF, cell.relForward());
            maxF = Math.max(maxF, cell.relForward());
            minL = Math.min(minL, cell.relLateral());
            maxL = Math.max(maxL, cell.relLateral());
        }
        for (WyrldPrintPoint point : record.points()) {
            minF = Math.min(minF, point.relForward());
            maxF = Math.max(maxF, point.relForward());
            minL = Math.min(minL, point.relLateral());
            maxL = Math.max(maxL, point.relLateral());
        }
        return new int[]{minF, maxF, minL, maxL};
    }

    /** Standard Minecraft Y-axis rotation of a (forward, lateral) offset pair, matching {@link
     *  Rotation#rotate(Direction)}'s own convention (confirmed against its real source: {@code
     *  CLOCKWISE_90} == {@code Direction#getClockWise()}). */
    static int[] rotateForwardLateral(int forward, int lateral, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[]{forward, lateral};
            case CLOCKWISE_90 -> new int[]{-lateral, forward};
            case CLOCKWISE_180 -> new int[]{-forward, -lateral};
            case COUNTERCLOCKWISE_90 -> new int[]{lateral, -forward};
        };
    }

    private static void addFrontiers(List<Frontier> frontiers, Placement placement, Frontier consumedFrontier) {
        for (WyrldPrintPoint point : placement.record().points()) {
            int[] rotatedFL = rotateForwardLateral(point.relForward(), point.relLateral(), placement.rotation());
            int worldForward = placement.originForward() + rotatedFL[0];
            int worldLateral = placement.originLateral() + rotatedFL[1];
            int worldY = placement.originY() + point.relY();
            if (consumedFrontier != null && worldForward == consumedFrontier.worldForward()
                    && worldLateral == consumedFrontier.worldLateral() && worldY == consumedFrontier.worldY()) {
                continue; // this is the point that was just used to attach -- not a new frontier.
            }
            Optional<Direction> worldFacing = point.facing().map(f -> placement.rotation().rotate(f));
            frontiers.add(new Frontier(worldForward, worldLateral, worldY, point.wall(), worldFacing));
        }
    }

    private static <T> List<T> shuffled(List<T> source, RandomSource random) {
        List<T> copy = new ArrayList<>(source);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(copy, i, j);
        }
        return copy;
    }

    /** {@code BlockState#rotate(Rotation)} (no level/position context) is deprecated in favor of a
     *  context-aware overload -- not needed here, since every block this mod ever captures
     *  (doors, cobblestone, wool, candles, etc.) rotates correctly with no world context. */
    @SuppressWarnings("deprecation")
    private static WyrldInstanceRecord stamp(MinecraftServer server, List<Placement> placements) {
        int minF = Integer.MAX_VALUE, maxF = Integer.MIN_VALUE, minL = Integer.MAX_VALUE, maxL = Integer.MIN_VALUE;
        for (Placement placement : placements) {
            int[] box = worldBoundingBox(placement);
            minF = Math.min(minF, box[0]);
            maxF = Math.max(maxF, box[1]);
            minL = Math.min(minL, box[2]);
            maxL = Math.max(maxL, box[3]);
        }

        WyrldInstanceRecord instance = WyrldManager.createWyrld(server, (maxF - minF) + 2 * MARGIN, (maxL - minL) + 2 * MARGIN);
        ServerLevel level = server.getLevel(WyrldSpaceDimension.KEY);
        if (level == null) {
            return instance;
        }
        FreeRect inner = instance.innerRect();
        int worldForwardOffset = inner.x1() + MARGIN - minF;
        int worldLateralOffset = inner.z1() + MARGIN - minL;

        record PendingCell(BlockPos pos, BlockState state, int y) {
        }
        List<PendingCell> pending = new ArrayList<>();
        for (Placement placement : placements) {
            for (WyrldPrintCell cell : placement.record().cells()) {
                int[] rotatedFL = rotateForwardLateral(cell.relForward(), cell.relLateral(), placement.rotation());
                int worldX = worldForwardOffset + placement.originForward() + rotatedFL[0];
                int worldZ = worldLateralOffset + placement.originLateral() + rotatedFL[1];
                int worldY = WyrldManager.BASELINE_Y + placement.originY() + cell.relY();
                pending.add(new PendingCell(new BlockPos(worldX, worldY, worldZ), cell.state().rotate(placement.rotation()), worldY));
            }
        }
        pending.sort(Comparator.comparingInt(PendingCell::y));
        for (PendingCell cell : pending) {
            level.setBlock(cell.pos(), cell.state(), 3);
        }
        return instance;
    }

    private record Placement(WyrldPrintRecord record, Rotation rotation, int originForward, int originLateral, int originY) {
    }

    private record Frontier(int worldForward, int worldLateral, int worldY, boolean wall, Optional<Direction> facing) {
    }
}
