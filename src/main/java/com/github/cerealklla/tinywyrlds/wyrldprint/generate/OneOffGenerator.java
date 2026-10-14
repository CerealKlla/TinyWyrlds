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
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintSpawn;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.FloorConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.WallConnectorPointBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Real connector-aware "Generate One-off": starts from a saved ENTRANCE when any exist (exactly
 * one, always -- see {@link WyrldPrintType}'s own doc -- falling back to a random ROOM only when
 * no ENTRANCE is saved yet), then repeatedly picks an open connector point ("frontier"), finds
 * another saved WyrldPrint with a compatible point
 * (same wall/floor kind; a wall point also needs an opposite {@link WyrldPrintPoint#facing()}
 * once rotated -- see that field's own doc for the outward-facing convention every sample piece
 * follows), tries all 4 {@link Rotation}s until one lines the two points up with no bounding-box
 * overlap against anything already placed, and stamps it in. Grows until the target room count is
 * reached or growth runs out of attempts ({@code minRooms}/{@code maxRooms} count only {@code ROOM}
 * pieces -- a {@code CONNECTOR} placed in between is extra, not counted toward that target, and
 * neither is the {@code ENTRANCE} itself: "Entrance is a brand new type of prynt, there is no
 * 'always' in that context. [it] does not count against the room count. If I want the possibility
 * of only an entrance I'll set it to 0" -- explicit user spec, 2026-10-14, correcting an initial,
 * wrong assumption that it should count like a ROOM does). A CONNECTOR is preferred between two
 * rooms (65% of the time, when any are saved) purely for variety, with direct room-to-room also
 * valid -- but a connector's own frontier is <b>never</b> extended with another connector (explicit
 * user spec, 2026-10-14: "connectors are meant to sit between rooms"), only with a ROOM, so two
 * connectors can never chain back to back. The ENTRANCE's own frontier is stricter still: it
 * <b>always</b> tries a connector first, never a direct room join, unlike an ordinary ROOM's 65%
 * preference (explicit user spec, 2026-10-14: "there should be a connector attached to the spawn
 * room, not straight into a room").
 *
 * <p><b>There is never a real dead end</b> (explicit user spec, 2026-10-14: "There should never be
 * an unused/dead-end... This inherently might allow a dungeon to exceed the max rooms, that is
 * ok."): once growth stops, a second closing pass resolves every still-open frontier with a 1-door
 * "endcap" ROOM (a saved room with exactly one connector point) instead of leaving a real, unused,
 * closed door behind. An endcap attachment never opens a new frontier (its only point is the one
 * being consumed), so the closing pass is guaranteed to actually terminate, and can push the real
 * room count past {@code maxRooms} -- accepted and expected, not a bug.
 *
 * <p>Everything is planned in a local (forward, lateral, Y) space first -- no real block is
 * touched until the whole layout is decided -- then translated once into a freshly reserved Wyrld
 * sized to fit the final bounding box. Final stamping sorts every cell from every piece by
 * ascending world Y together (not just within one piece), so a door's upper half always finds its
 * support already placed regardless of which piece placed it.
 *
 * <p>{@link com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize} must match
 * exactly between two joining points (explicit user spec, 2026-10-14: "enforce connector size
 * matches") -- {@code Frontier} carries the gauge of the point it came from, and {@code tryPlace}
 * only ever considers a candidate point of that exact same size, for both wall and floor kinds.
 *
 * <p><b>Honest scope</b>: this does not yet read a WyrldRecipe (loot pools, quest tie-ins -- none
 * of that exists yet); a recipe-driven room/connector pool (rather than uniform random selection)
 * is real, flagged follow-up work, not an oversight.
 */
public final class OneOffGenerator {

    private OneOffGenerator() {
    }

    private static final int MARGIN = 5;
    private static final double PREFER_CONNECTOR_CHANCE = 0.65;
    private static final Rotation[] ALL_ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

    /** @return the created Wyrld instance (plus its real spawn position, if an ENTRANCE was
     *  placed), or {@code null} if no ROOM/ENTRANCE WyrldPrints are saved at all. */
    public static GenerateResult generate(MinecraftServer server, int minRooms, int maxRooms) {
        PlanResult plan = planPlacements(minRooms, maxRooms);
        if (plan == null) {
            return null;
        }
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: placed {} piece(s) ({} room(s), target {}) in {} attempt(s)",
                plan.placements().size(), plan.roomsPlaced(), plan.targetRooms(), plan.attempts());
        return stamp(server, plan.placements(), plan.entrancePlacement(), plan.doorSeals());
    }

    /** Debug-only (see {@code DebugCommands}'s {@code selectiontest}): runs the exact same planning
     *  logic as {@link #generate} without ever touching a real level/Wyrld instance -- {@link
     *  #stamp} is the only part of {@link #generate} that needs one. Lets a selection-frequency
     *  diagnostic run hundreds of trials cheaply, added 2026-10-14 after a real report ("my room
     *  hasn't been selected once") that turned out to need actual counts, not assumptions, to
     *  investigate. @return each placed piece as {@code "<type>:<name>"}, or an empty list if no
     *  ROOM/ENTRANCE WyrldPrints are saved at all. */
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
        List<WyrldPrintRecord> entrances = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ENTRANCE).toList();
        // "There should never be an unused/dead-end. there will be rooms with only 1 door, those
        // are how we will end those paths" (explicit user spec, 2026-10-14) -- a ROOM with exactly
        // one connector point is the dedicated closing piece for the second pass below. Attaching
        // one never opens a new frontier (its only point is the one being consumed), guaranteeing
        // the closing pass actually terminates rather than just relocating the dead end.
        List<WyrldPrintRecord> endcapRooms = rooms.stream().filter(r -> r.points().size() == 1).toList();
        // During growth, an endcap room is only tried as a last resort, never preferred over a
        // real multi-door room -- explicit user spec, 2026-10-14: "I don't want it to 'reserve
        // them for the closing pass', so long as it's not the last option and there are more
        // needed." Without this split, picking one by sheer chance while it's the only open
        // frontier could terminate a whole chain early (confirmed live: an entrance -> connector ->
        // 1-door room result that stopped at 1/10 rooms with attempt budget barely touched).
        List<WyrldPrintRecord> multiDoorRooms = rooms.stream().filter(r -> r.points().size() > 1).toList();
        if (rooms.isEmpty() && entrances.isEmpty()) {
            return null;
        }

        RandomSource random = RandomSource.create();
        int lowRooms = Math.min(minRooms, maxRooms);
        int highRooms = Math.max(minRooms, maxRooms);
        int targetRooms = lowRooms + random.nextInt(highRooms - lowRooms + 1);

        List<Placement> placements = new ArrayList<>();
        List<Frontier> openFrontiers = new ArrayList<>();
        Map<LocalPos, Boolean> occupied = new HashMap<>();
        // Every frontier that genuinely never got resolved -- not just "closing pass never got to
        // it," but "every candidate (connector, multi-door room, endcap room) was tried and nothing
        // fit," most often a corridor curling back close enough to already-placed structure that
        // nothing else can occupy the gap. Fed into the door-sealing pass below instead of being
        // silently discarded.
        List<Frontier> unresolvedFrontiers = new ArrayList<>();

        // "Treated same as a room, but there is only 1" (explicit user spec, 2026-10-14): an
        // ENTRANCE, when any are saved, is always the guaranteed anchor/first piece -- never a
        // random ROOM instead, and (since it's excluded from the `rooms` list by construction)
        // never placed anywhere else in the layout either. Falls back to a random ROOM when no
        // ENTRANCE is saved yet, so existing content/behavior keeps working.
        Placement first = new Placement(entrances.isEmpty() ? rooms.get(random.nextInt(rooms.size()))
                : entrances.get(random.nextInt(entrances.size())), Rotation.NONE, 0, 0, 0);
        placements.add(first);
        occupied.putAll(cellStates(first));
        addFrontiers(openFrontiers, first);
        Placement entrancePlacement = first.record().type() == WyrldPrintType.ENTRANCE ? first : null;
        // The ENTRANCE does NOT count toward minRooms/maxRooms -- explicit user spec, 2026-10-14:
        // "Entrance is a brand new type of prynt, there is no 'always' in that context. Please
        // change it so that the entrance does not count against the room count. If I want the
        // possibility of only an entrance I'll set it to 0." Only counts here when no ENTRANCE was
        // saved and `first` fell back to a random ROOM instead (see above).
        int roomsPlaced = entrancePlacement == null ? 1 : 0;
        logPlacement(first);

        int attempts = 0;
        int attemptBudget = Math.max(50, targetRooms * 60);
        while (roomsPlaced < targetRooms && !openFrontiers.isEmpty() && attempts < attemptBudget) {
            attempts++;
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));

            Placement placed = null;
            // Connectors must always sit between two rooms, never chain to another connector --
            // explicit user spec, 2026-10-14. A frontier whose own piece IS a connector only ever
            // tries ROOM candidates; only a room's (or the Entrance's) own frontier gets the
            // connector preference at all.
            boolean frontierOwnedByRoom = frontier.ownerType() == WyrldPrintType.ROOM;
            boolean frontierOwnedByEntrance = frontier.ownerType() == WyrldPrintType.ENTRANCE;
            // "there should be a connector attached to the spawn room, not straight into a room"
            // (explicit user spec, 2026-10-14) -- unlike an ordinary ROOM's 65%-of-the-time
            // preference, the ENTRANCE's own frontier *always* tries a connector first (still
            // falling back to a direct room join below if no connector fits or none are saved).
            boolean tryConnectorFirst = frontierOwnedByEntrance || (frontierOwnedByRoom && random.nextDouble() < PREFER_CONNECTOR_CHANCE);
            if (tryConnectorFirst && !connectors.isEmpty()) {
                placed = tryPlace(frontier, shuffled(connectors, random), occupied, random);
            }
            if (placed == null) {
                placed = tryPlace(frontier, shuffled(multiDoorRooms, random), occupied, random);
            }
            if (placed == null) {
                // Last resort, not a preference -- see endcapRooms' own doc above.
                placed = tryPlace(frontier, shuffled(endcapRooms, random), occupied, random);
            }
            if (placed == null) {
                unresolvedFrontiers.add(frontier); // connector, multi-door room, and endcap room all failed -- genuinely nothing fits here.
                continue;
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(openFrontiers, placed);
            if (placed.record().type() == WyrldPrintType.ROOM) {
                roomsPlaced++;
            }
            logPlacement(placed);
        }

        // Closing pass: once growth stops (room target hit, or the growth budget ran out), every
        // *remaining* open frontier must still be resolved with a 1-door endcap room -- never left
        // as a real, unused door. Explicit user spec, 2026-10-14: "There should never be an unused/
        // dead-end... This inherently might allow a dungeon to exceed the max rooms, that is ok."
        // Deliberately never tries a CONNECTOR or a multi-door ROOM here -- either would just open
        // a new frontier instead of closing this one.
        int closingBudget = Math.max(50, openFrontiers.size() * 30);
        int closingAttempts = 0;
        while (!openFrontiers.isEmpty() && closingAttempts < closingBudget) {
            closingAttempts++;
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));
            if (endcapRooms.isEmpty()) {
                unresolvedFrontiers.add(frontier); // no 1-door room saved at all.
                continue;
            }
            Placement placed = tryPlace(frontier, shuffled(endcapRooms, random), occupied, random);
            if (placed == null) {
                unresolvedFrontiers.add(frontier); // couldn't fit any endcap room here either.
                continue;
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(openFrontiers, placed); // always zero new frontiers -- its one point was just consumed.
            roomsPlaced++; // endcapRooms are ROOM-type by construction.
            logPlacement(placed);
        }
        attempts += closingAttempts;

        // Door sealing: a wall-type CONNECTOR frontier that truly never found anything to attach
        // to (the "curled in on itself" case, confirmed live -- a corridor looping back close
        // enough to already-placed structure that nothing else can occupy the remaining gap)
        // shouldn't stand out as a real, functional, leads-nowhere door. Explicit user spec,
        // 2026-10-14: "I think in that case it should replace the Connector with whatever blocks
        // are surrounding it." Samples the real block immediately through the door (1 step along
        // its own outward facing, same direction convention as the touch-not-overlap fix) from the
        // now-final placements list, per door half, and hands the override to stamp() to use
        // instead of air. Scoped to wall-type only -- a floor point has no stored facing to know
        // which direction "through the door" even means, so it keeps the existing air fallback.
        Map<LocalPos, BlockState> doorSeals = new HashMap<>();
        if (!unresolvedFrontiers.isEmpty()) {
            Map<LocalPos, BlockState> realBlocks = buildRealBlockMap(placements);
            for (Frontier frontier : unresolvedFrontiers) {
                if (frontier.ownerType() != WyrldPrintType.CONNECTOR || !frontier.wall() || frontier.facing().isEmpty()) {
                    continue;
                }
                Direction outward = frontier.facing().get();
                for (int dy = 0; dy <= 1; dy++) {
                    LocalPos doorCell = new LocalPos(frontier.worldForward(), frontier.worldLateral(), frontier.worldY() + dy);
                    LocalPos neighbor = new LocalPos(frontier.worldForward() + outward.getStepX(),
                            frontier.worldLateral() + outward.getStepZ(), frontier.worldY() + dy);
                    BlockState neighborState = realBlocks.get(neighbor);
                    if (neighborState != null && !neighborState.isAir()) {
                        doorSeals.put(doorCell, neighborState);
                    }
                }
            }
        }

        return new PlanResult(placements, roomsPlaced, targetRooms, attempts, entrancePlacement, doorSeals);
    }

    /** Every real cell any placement would stamp, keyed by its final (pre-world-translation) local
     *  position, keeping the actual {@code BlockState} rather than just a solid/air flag -- used
     *  only by the door-sealing pass above, built fresh from the finished plan rather than
     *  repurposing the collision-planning {@code occupied} map, to keep that delicate, already
     *  carefully-tuned logic untouched. */
    @SuppressWarnings("deprecation")
    private static Map<LocalPos, BlockState> buildRealBlockMap(List<Placement> placements) {
        Map<LocalPos, BlockState> result = new HashMap<>();
        for (Placement placement : placements) {
            for (WyrldPrintCell cell : placement.record().cells()) {
                int[] rotatedFL = rotateForwardLateral(cell.relForward(), cell.relLateral(), placement.rotation());
                LocalPos pos = new LocalPos(placement.originForward() + rotatedFL[0], placement.originLateral() + rotatedFL[1],
                        placement.originY() + cell.relY());
                result.put(pos, cell.state().rotate(placement.rotation()));
            }
        }
        return result;
    }

    /** Diagnostic logging added 2026-10-14 alongside {@code tryPlace}'s own collision-sample
     *  logging -- that only captures FAILED attempts; this captures every real, accepted placement
     *  (including its real local bounding box), so a dead-ended layout's full picture (what's
     *  actually occupying the planning space, not just what collided) can be reconstructed from the
     *  log after the fact. */
    private static void logPlacement(Placement placement) {
        int[] box = worldBoundingBox(placement);
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: placed '{}' ({}) rotation={} origin=(f={},l={},y={}) bbox=(f=[{},{}),l=[{},{}),y=[{},{}))",
                placement.record().name(), placement.record().type(), placement.rotation(),
                placement.originForward(), placement.originLateral(), placement.originY(),
                box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    private record PlanResult(List<Placement> placements, int roomsPlaced, int targetRooms, int attempts,
                               Placement entrancePlacement, Map<LocalPos, BlockState> doorSeals) {
    }

    /** @param spawnPoints every real-world spot a player could land -- the ENTRANCE's own captured
     *  {@code WyrldPrintSpawn} marker(s), translated into real world coordinates. More than one is
     *  expected and fine (explicit user spec, 2026-10-14: "if a party arrives they all need their
     *  own spawn point"); empty if no ENTRANCE was available/placed (caller falls back to the
     *  Wyrld's own generic inner-rect center). */
    public record GenerateResult(WyrldInstanceRecord instance, List<BlockPos> spawnPoints) {
    }

    /**
     * Tries every candidate (in the given, already-shuffled order) and every rotation, returning
     * the first placement that both matches {@code frontier} and doesn't collide with anything
     * already placed.
     *
     * <p><b>Joined pieces touch, they don't overlap (explicit user spec, 2026-10-14: "connectors
     * of two prynts should touch, not overlap")</b>: a wall-type join no longer aligns the new
     * piece's own door to land in the *same cell* as the existing door -- that forced the two
     * pieces' entire shared wall plane into coincidence (every wall cell along that plane had to
     * agree, not just the door), which made spurious collisions far more likely than necessary
     * (e.g. a round room's curved-away corner cell landing on top of a straight piece's solid wall
     * cell one step further along the same plane, even though the actual doorway itself lined up
     * fine). The new piece's door is instead aligned 1 cell further out, along the existing door's
     * own real-world outward {@code facing} ({@code worldForward}/{@code worldLateral} are
     * literally real-world +X/EAST and +Z/SOUTH -- see {@code WyrldPrintArea.FRONT_DIRECTION}'s own
     * doc -- so {@code Direction#getStepX/Z} map onto this local space with no extra conversion),
     * so the two doors land in genuinely adjacent cells instead of the identical one. Floor-type
     * joins are unaffected (no {@code facing} to shift along -- a floor connector's own vertical
     * extent already keeps the two pieces from overlapping without needing one).
     *
     * <p><b>Collision rule, and why it's still air/solid-aware, not a flat "no shared cells"
     * rule</b>: even with pieces only touching now, a position is only a genuine problem if one
     * piece wants it *solid* and the other wants it *air* -- that's the one case that can silently
     * block a passage or erase content depending on stamp order. Solid-vs-solid and air-vs-air are
     * both harmless and explicitly allowed; this still matters for the (now much rarer, but not
     * impossible) case of a piece's geometry bulging into a neighboring piece's own footprint
     * beyond just the touching doorway.
     */
    private static Placement tryPlace(Frontier frontier, List<WyrldPrintRecord> candidates, Map<LocalPos, Boolean> occupied, RandomSource random) {
        int noMatchingKindPoint = 0;
        int facingMismatches = 0;
        int collisions = 0;
        boolean sampleLogged = false;
        for (WyrldPrintRecord candidate : candidates) {
            List<WyrldPrintPoint> ownPoints = shuffled(candidate.points().stream()
                    .filter(p -> p.wall() == frontier.wall() && p.size() == frontier.size()).toList(), random);
            if (ownPoints.isEmpty()) {
                noMatchingKindPoint++;
                continue;
            }
            for (WyrldPrintPoint point : ownPoints) {
                for (Rotation rotation : ALL_ROTATIONS) {
                    if (frontier.wall()) {
                        if (point.facing().isEmpty() || frontier.facing().isEmpty()) {
                            facingMismatches++;
                            continue;
                        }
                        if (rotation.rotate(point.facing().get()) != frontier.facing().get().getOpposite()) {
                            facingMismatches++;
                            continue;
                        }
                    }
                    // Two joined wall pieces should touch at their doorways, not overlap along the
                    // whole shared wall plane -- explicit user spec, 2026-10-14: "connectors of two
                    // prynts should touch, not overlap." Shift the alignment target 1 cell further
                    // out along the existing door's own outward-facing direction (worldForward is
                    // real-world +X/EAST, worldLateral is real-world +Z/SOUTH -- see
                    // WyrldPrintArea.FRONT_DIRECTION's own doc -- so Direction#getStepX/Z map
                    // directly onto this local space with no extra conversion needed), so the two
                    // doors land in adjacent cells instead of the same one.
                    int targetForward = frontier.worldForward();
                    int targetLateral = frontier.worldLateral();
                    if (frontier.wall()) {
                        Direction outward = frontier.facing().get();
                        targetForward += outward.getStepX();
                        targetLateral += outward.getStepZ();
                    }
                    int[] rotatedFL = rotateForwardLateral(point.relForward(), point.relLateral(), rotation);
                    int originForward = targetForward - rotatedFL[0];
                    int originLateral = targetLateral - rotatedFL[1];
                    int originY = frontier.worldY() - point.relY();
                    Placement candidatePlacement = new Placement(candidate, rotation, originForward, originLateral, originY, point);
                    List<LocalPos> conflicts = findCollisions(candidatePlacement, occupied);
                    if (conflicts.isEmpty()) {
                        return candidatePlacement;
                    }
                    collisions++;
                    // Diagnostic logging added 2026-10-14 after a real report of every candidate
                    // colliding for an L-Bend connector's second point -- logs the exact geometry
                    // of the FIRST failed attempt (candidate, rotation, origin, up to 3 real
                    // conflicting positions) so the actual overlap can be seen instead of guessed.
                    if (!sampleLogged) {
                        sampleLogged = true;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(3, conflicts.size()); i++) {
                            LocalPos p = conflicts.get(i);
                            sb.append(String.format("(f=%d,l=%d,y=%d) ", p.forward(), p.lateral(), p.y()));
                        }
                        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                                "tryPlace: first collision sample -- candidate='{}' rotation={} origin=(f={},l={},y={}) "
                                        + "ownPoint=(f={},l={},y={}) frontierWorld=(f={},l={},y={}) {} conflict(s), first few: {}",
                                candidate.name(), rotation, originForward, originLateral, originY,
                                point.relForward(), point.relLateral(), point.relY(),
                                frontier.worldForward(), frontier.worldLateral(), frontier.worldY(),
                                conflicts.size(), sb.toString().trim());
                    }
                }
            }
        }
        // Diagnostic logging added 2026-10-14 after a real report of a generate call dead-ending
        // almost immediately (target 10 rooms, got 1) -- without this, "no match" gave no way to
        // tell apart a facing/kind mismatch (expected, benign) from every real candidate colliding
        // (which would suggest a real geometry/collision bug worth chasing).
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "tryPlace: no match for frontier (wall={}, facing={}, ownerType={}) among {} candidate(s) "
                        + "-- {} had no {}-kind point, {} facing mismatch(es), {} collision(s)",
                frontier.wall(), frontier.facing(), frontier.ownerType(), candidates.size(),
                noMatchingKindPoint, frontier.wall() ? "wall" : "floor", facingMismatches, collisions);
        return null;
    }

    private static List<LocalPos> findCollisions(Placement placement, Map<LocalPos, Boolean> occupied) {
        List<LocalPos> conflicts = new ArrayList<>();
        for (Map.Entry<LocalPos, Boolean> entry : cellStates(placement).entrySet()) {
            Boolean existing = occupied.get(entry.getKey());
            if (existing != null && !existing.equals(entry.getValue())) {
                conflicts.add(entry.getKey());
            }
        }
        return conflicts;
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

    /** Adds every one of {@code placement}'s own connector points as a new open frontier, except
     *  the one it was just attached with ({@code placement.consumedPoint()}, identified by
     *  identity). Used to compare world positions against the old, now-consumed frontier instead --
     *  that broke the moment joined pieces stopped sharing the same cell (see {@code tryPlace}'s own
     *  "touch, not overlap" doc): the newly placed piece's own door is now a cell away from where
     *  the old frontier was, so it could never match by position again, and every wall-type join
     *  started silently re-opening its own just-used door as a brand new frontier -- a real,
     *  confirmed-live bug (a generate call repeatedly re-placing the identical piece at the
     *  identical spot, inflating the reported room count with duplicates of itself). */
    private static void addFrontiers(List<Frontier> frontiers, Placement placement) {
        for (WyrldPrintPoint point : placement.record().points()) {
            if (point == placement.consumedPoint()) {
                continue; // this is the point that was just used to attach -- not a new frontier.
            }
            int[] rotatedFL = rotateForwardLateral(point.relForward(), point.relLateral(), placement.rotation());
            int worldForward = placement.originForward() + rotatedFL[0];
            int worldLateral = placement.originLateral() + rotatedFL[1];
            int worldY = placement.originY() + point.relY();
            Optional<Direction> worldFacing = point.facing().map(f -> placement.rotation().rotate(f));
            frontiers.add(new Frontier(worldForward, worldLateral, worldY, point.wall(), worldFacing, placement.record().type(), point.size()));
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
    private static GenerateResult stamp(MinecraftServer server, List<Placement> placements, Placement entrancePlacement,
                                         Map<LocalPos, BlockState> doorSeals) {
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
            return new GenerateResult(instance, List.of());
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
                int localForward = placement.originForward() + rotatedFL[0];
                int localLateral = placement.originLateral() + rotatedFL[1];
                int localY = placement.originY() + cell.relY();
                int worldX = worldForwardOffset + localForward;
                int worldZ = worldLateralOffset + localLateral;
                int worldY = WyrldManager.BASELINE_Y + localY;
                BlockState seal = doorSeals.get(new LocalPos(localForward, localLateral, localY));
                BlockState finalState;
                if (seal != null) {
                    finalState = seal;
                } else {
                    BlockState rotated = cell.state().rotate(placement.rotation());
                    finalState = isConnectorArtifact(rotated) ? Blocks.AIR.defaultBlockState() : rotated;
                }
                pending.add(new PendingCell(new BlockPos(worldX, worldY, worldZ), finalState, worldY));
            }
        }
        pending.sort(Comparator.comparingInt(PendingCell::y));
        for (PendingCell cell : pending) {
            level.setBlock(cell.pos(), cell.state(), 3);
        }

        List<BlockPos> spawnPoints = new ArrayList<>();
        if (entrancePlacement != null) {
            for (WyrldPrintSpawn spawn : entrancePlacement.record().spawnPoints()) {
                int[] rotatedFL = rotateForwardLateral(spawn.relForward(), spawn.relLateral(), entrancePlacement.rotation());
                int worldX = worldForwardOffset + entrancePlacement.originForward() + rotatedFL[0];
                int worldZ = worldLateralOffset + entrancePlacement.originLateral() + rotatedFL[1];
                int worldY = WyrldManager.BASELINE_Y + entrancePlacement.originY() + spawn.relY();
                spawnPoints.add(new BlockPos(worldX, worldY, worldZ));
            }
        }
        return new GenerateResult(instance, spawnPoints);
    }

    /** A real generated dungeon never shows the raw Connector Point door/trapdoor or its glass
     *  fill -- explicit user spec, 2026-10-14: "the connector door and glass need to NOT be built
     *  into the world." Those are purely authoring-time matching/visual aids; once the generator
     *  has used a point to join two pieces, the real stamped result should just be an open passage
     *  (air) there instead. In practice the glass half of this is already a no-op for any normally
     *  captured record (`WyrldPrintCapture` already clears it back to air at save time), but this
     *  check stays defense-in-depth rather than relying on that alone. Only reached for a door cell
     *  that isn't already overridden by {@code doorSeals} -- a door that never found anything to
     *  join gets real neighboring material instead of air (see {@code planPlacements}'s own
     *  door-sealing pass). */
    private static boolean isConnectorArtifact(BlockState state) {
        if (state.getBlock() instanceof WallConnectorPointBlock || state.getBlock() instanceof FloorConnectorPointBlock) {
            return true;
        }
        for (ConnectorPointSize size : ConnectorPointSize.values()) {
            if (state.getBlock() == size.glassBlock()) {
                return true;
            }
        }
        return false;
    }

    /** {@code consumedPoint} is the exact {@code WyrldPrintPoint} instance (from {@code
     *  record.points()}) that was used to attach this placement to whatever frontier it joined --
     *  {@code null} for the very first/anchor placement, which wasn't attached to anything.
     *  Identity, not a recomputed world position, is what {@code addFrontiers} uses to recognize
     *  "this door is already spoken for" -- see that method's own doc for why a position comparison
     *  stopped working once joined pieces only touch instead of sharing the same cell. */
    private record Placement(WyrldPrintRecord record, Rotation rotation, int originForward, int originLateral, int originY,
                              WyrldPrintPoint consumedPoint) {
        Placement(WyrldPrintRecord record, Rotation rotation, int originForward, int originLateral, int originY) {
            this(record, rotation, originForward, originLateral, originY, null);
        }
    }

    /** {@code ownerType} is the type of the piece this point belongs to -- used to enforce
     *  connectors always sitting between two rooms (see the main planning loop). {@code size} is
     *  the connector point's own gauge -- only a candidate point of the exact same {@code
     *  ConnectorPointSize} is ever tried against this frontier (explicit user spec, 2026-10-14:
     *  "enforce connector size matches" -- a small door can no longer join a large one). */
    private record Frontier(int worldForward, int worldLateral, int worldY, boolean wall, Optional<Direction> facing,
                             WyrldPrintType ownerType, ConnectorPointSize size) {
    }

    /** A single cell position in the shared local (forward, lateral, Y) planning space every
     *  placement is computed in -- not yet a real {@code BlockPos}, see {@code stamp}. */
    private record LocalPos(int forward, int lateral, int y) {
    }
}
