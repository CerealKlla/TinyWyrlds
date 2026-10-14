package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * count is reached or every frontier dead-ends ({@code minRooms}/{@code maxRooms} count only ROOM
 * pieces -- a CONNECTOR placed in between is extra, not counted toward that target). A CONNECTOR
 * is preferred between two rooms (65% of the time, when any are saved) purely for variety, with
 * direct room-to-room also valid -- but a connector's own frontier is <b>never</b> extended with
 * another connector (explicit user spec, 2026-10-14: "connectors are meant to sit between rooms"),
 * only with a ROOM, so two connectors can never chain back to back.
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
        PlanResult plan = planPlacements(minRooms, maxRooms);
        if (plan == null) {
            return null;
        }
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: placed {} piece(s) ({} room(s), target {}) in {} attempt(s)",
                plan.placements().size(), plan.roomsPlaced(), plan.targetRooms(), plan.attempts());
        return stamp(server, plan.placements());
    }

    /** Debug-only (see {@code DebugCommands}'s {@code selectiontest}): runs the exact same planning
     *  logic as {@link #generate} without ever touching a real level/Wyrld instance -- {@link
     *  #stamp} is the only part of {@link #generate} that needs one. Lets a selection-frequency
     *  diagnostic run hundreds of trials cheaply, added 2026-10-14 after a real report ("my room
     *  hasn't been selected once") that turned out to need actual counts, not assumptions, to
     *  investigate. @return each placed piece as {@code "<type>:<name>"}, or an empty list if no
     *  ROOM WyrldPrints are saved at all. */
    public static List<String> planOnceForDebug(int minRooms, int maxRooms) {
        PlanResult plan = planPlacements(minRooms, maxRooms);
        if (plan == null) {
            return List.of();
        }
        return plan.placements().stream().map(p -> p.record().type() + ":" + p.record().name()).toList();
    }

    private static PlanResult planPlacements(int minRooms, int maxRooms) {
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
        Map<LocalPos, Boolean> occupied = new HashMap<>();

        Placement first = new Placement(rooms.get(random.nextInt(rooms.size())), Rotation.NONE, 0, 0, 0);
        placements.add(first);
        occupied.putAll(cellStates(first));
        addFrontiers(openFrontiers, first, null);
        int roomsPlaced = 1;

        int attempts = 0;
        int attemptBudget = Math.max(50, targetRooms * 60);
        while (roomsPlaced < targetRooms && !openFrontiers.isEmpty() && attempts < attemptBudget) {
            attempts++;
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));

            Placement placed = null;
            // Connectors must always sit between two rooms, never chain to another connector --
            // explicit user spec, 2026-10-14. A frontier whose own piece IS a connector only ever
            // tries ROOM candidates; only a room's own frontier gets the connector preference.
            boolean frontierOwnedByRoom = frontier.ownerType() == WyrldPrintType.ROOM;
            if (frontierOwnedByRoom && !connectors.isEmpty() && random.nextDouble() < PREFER_CONNECTOR_CHANCE) {
                placed = tryPlace(frontier, shuffled(connectors, random), occupied, random);
            }
            if (placed == null) {
                placed = tryPlace(frontier, shuffled(rooms, random), occupied, random);
            }
            if (placed == null) {
                continue; // dead end -- the point stays a real, closed, unused door/trapdoor.
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(openFrontiers, placed, frontier);
            if (placed.record().type() == WyrldPrintType.ROOM) {
                roomsPlaced++;
            }
        }

        return new PlanResult(placements, roomsPlaced, targetRooms, attempts);
    }

    private record PlanResult(List<Placement> placements, int roomsPlaced, int targetRooms, int attempts) {
    }

    /**
     * Tries every candidate (in the given, already-shuffled order) and every rotation, returning
     * the first placement that both matches {@code frontier} and doesn't collide with anything
     * already placed.
     *
     * <p><b>Collision rule, and why it's air/solid-aware, not a flat "no shared cells" rule</b>:
     * two genuinely joined pieces' adjoining walls are *expected* to coincide exactly along their
     * whole shared plane (not just at the one connector cell) -- e.g. two rooms sharing a common
     * wall with one doorway between them, where every other cell along that wall is solid on both
     * sides. A plain "any shared cell = collision" check (tried first, see decisions.md) rejected
     * every real join because of this, since only the 1-2 door cells were ever exempted, leaving
     * the rest of the shared wall plane to false-flag. A plain bounding-box check (tried before
     * that) had the opposite problem: shrinking it enough to stop flagging the shared seam also
     * let real overlaps through for thin pieces. The actual invariant that matters for walkability
     * is simpler than either: a position is only a genuine problem if one piece wants it *solid*
     * and the other wants it *air* -- that's the one case that can silently block a passage or
     * erase content depending on stamp order. Solid-vs-solid (a shared wall) and air-vs-air (two
     * open spaces overlapping) are both harmless and explicitly allowed.
     */
    private static Placement tryPlace(Frontier frontier, List<WyrldPrintRecord> candidates, Map<LocalPos, Boolean> occupied, RandomSource random) {
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
                    if (!collides(candidatePlacement, occupied)) {
                        return candidatePlacement;
                    }
                }
            }
        }
        return null;
    }

    private static boolean collides(Placement placement, Map<LocalPos, Boolean> occupied) {
        for (Map.Entry<LocalPos, Boolean> entry : cellStates(placement).entrySet()) {
            Boolean existing = occupied.get(entry.getKey());
            if (existing != null && !existing.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Every cell this placement would occupy, mapped to whether it's solid (not air). */
    private static Map<LocalPos, Boolean> cellStates(Placement placement) {
        Map<LocalPos, Boolean> result = new HashMap<>(placement.record().cells().size() * 2);
        for (WyrldPrintCell cell : placement.record().cells()) {
            int[] rotatedFL = rotateForwardLateral(cell.relForward(), cell.relLateral(), placement.rotation());
            LocalPos pos = new LocalPos(placement.originForward() + rotatedFL[0], placement.originLateral() + rotatedFL[1],
                    placement.originY() + cell.relY());
            result.put(pos, !cell.state().isAir());
        }
        return result;
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
            frontiers.add(new Frontier(worldForward, worldLateral, worldY, point.wall(), worldFacing, placement.record().type()));
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

    /** {@code ownerType} is the type of the piece this point belongs to -- used to enforce
     *  connectors always sitting between two rooms (see the main planning loop). */
    private record Frontier(int worldForward, int worldLateral, int worldY, boolean wall, Optional<Direction> facing, WyrldPrintType ownerType) {
    }

    /** A single cell position in the shared local (forward, lateral, Y) planning space every
     *  placement is computed in -- not yet a real {@code BlockPos}, see {@code stamp}. */
    private record LocalPos(int forward, int lateral, int y) {
    }
}
