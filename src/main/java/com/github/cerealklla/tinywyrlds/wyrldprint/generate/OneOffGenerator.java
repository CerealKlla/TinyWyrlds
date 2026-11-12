package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;
import com.github.cerealklla.tinywyrlds.space.FreeRect;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.ExitPortalPingMarkerEntity;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintCell;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintPoint;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintSpawn;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorArrowBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.FloorConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.marker.MarkerBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.marker.MarkerKind;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldEntityEntry;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldLootEntry;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

    /** How many independent random attempts {@link #planTunnelPlacements} makes at a shape-aware
     *  Tunnel layout before giving up and falling back to the shape-blind algorithm. */
    private static final int SHAPE_ATTEMPTS = 8;
    /** Once a shaped chain's own real-world-proportional minimum length is met, how many *extra*
     *  hops it may still randomly keep growing past that floor -- same "a minimum, not a forced,
     *  identical length" philosophy as the flat algorithm's own {@code minBranchLength}. */
    private static final int MAX_EXTRA_VARIANCE_HOPS = 3;
    private static final double EXTENSION_CHANCE = 0.35;
    /** Same ratio Settlemynts' own {@code tunnel.TunnelFinalizer#BLOCKS_PER_ROOM} already uses to
     *  size a Tunnel's overall room budget off its doors' real-world diameter -- reused here per
     *  edge directly, instead of splitting a shared budget, after a live-tested 6-door Tunnel
     *  proved that approach non-functional (see {@link #edgeTargetLength}'s own doc). */
    private static final double BLOCKS_PER_ROOM_FOR_SHAPE = 50.0;

    /** @return the created Wyrld instance (plus its real spawn position, if an ENTRANCE was
     *  placed), or {@code null} if no ROOM/ENTRANCE WyrldPrints are saved at all. The real layout
     *  (which pieces, at what rotation/position, plus the door-sealing result) is also saved to
     *  {@code GeneratedDungeonStorage} under a fresh id -- explicit user spec, 2026-10-14: "when a
     *  dungeon is generated I'd like the layout to be saved into a GeneratedDungeon folder... so we
     *  can regenerate the same layout." See {@link #regenerate} for the other half of this. */
    public static GenerateResult generate(MinecraftServer server, int minRooms, int maxRooms) {
        return generate(server, minRooms, maxRooms, null);
    }

    /** Recipe-driven generation (design-document.md's own WyrldRecipe subsystem) -- {@code
     *  minRooms}/{@code maxRooms} come from the recipe itself instead of being passed separately,
     *  and every marker block placed (see {@code wyrldprint.marker.MarkerKind}) gets a real
     *  population pass against the recipe's own loot/enemy/friendly pools -- see {@link #stamp}'s
     *  own doc for exactly what that means per marker kind. */
    public static GenerateResult generate(MinecraftServer server, WyrldRecipe recipe) {
        return generate(server, recipe.minRooms(), recipe.maxRooms(), recipe);
    }

    private static GenerateResult generate(MinecraftServer server, int minRooms, int maxRooms, WyrldRecipe recipe) {
        PlanResult plan = planPlacements(minRooms, maxRooms, recipe == null ? Optional.empty() : recipe.entranceName(),
                recipe != null && recipe.useLatestTutorialPrint(), recipe == null ? List.of() : recipe.tags());
        if (plan == null) {
            return null;
        }
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: placed {} piece(s) ({} room(s), target {}) in {} attempt(s)",
                plan.placements().size(), plan.roomsPlaced(), plan.targetRooms(), plan.attempts());

        UUID dungeonId = UUID.randomUUID();
        List<GeneratedDungeonRecord.SavedPlacement> savedPlacements = plan.placements().stream()
                .map(p -> new GeneratedDungeonRecord.SavedPlacement(p.record().name(), p.record().type(), p.rotation(),
                        p.originForward(), p.originLateral(), p.originY()))
                .toList();
        List<GeneratedDungeonRecord.SealedCell> sealedCells = plan.doorSeals().entrySet().stream()
                .map(e -> new GeneratedDungeonRecord.SealedCell(e.getKey().forward(), e.getKey().lateral(), e.getKey().y(), e.getValue()))
                .toList();
        GeneratedDungeonStorage.get().save(new GeneratedDungeonRecord(dungeonId, savedPlacements, sealedCells));

        return stamp(server, plan.placements(), plan.entrancePlacement(), plan.doorSeals(), dungeonId, recipe);
    }

    /** Re-stamps a previously saved layout (see {@link #generate}'s own doc) into a fresh Wyrld
     *  instance -- same real pieces (re-fetched from {@code WyrldPrintStorage} by name/type, so an
     *  edit to one of them since the original generate is reflected here), same rotations/origins,
     *  same door-sealing result, verbatim. Deliberately does not re-run any of the matching/growth
     *  logic -- there is nothing left to randomize structurally. A future mob/loot system rolling
     *  fresh random choices on every regenerate (explicit user spec: "Mob Spawn choices, loot box
     *  locations, loot pools, mob pools all remain randomized") has nothing to do with this record
     *  at all, by construction -- it was never saved here.
     *
     * @return {@code null} if no saved dungeon has this id.
     * @throws IllegalStateException if a referenced WyrldPrint no longer exists (renamed/deleted
     *     since the original generate) -- surfaced as a clear command failure rather than silently
     *     generating an incomplete dungeon. */
    public static GenerateResult regenerate(MinecraftServer server, UUID dungeonId) {
        return regenerate(server, dungeonId, null);
    }

    /** Same structural re-stamp as {@link #regenerate(MinecraftServer, UUID)}, but also re-runs the
     *  population pass against {@code recipe} -- exactly the explicit spec this class's own doc
     *  already quotes ("Mob Spawn choices, loot box locations, loot pools, mob pools all remain
     *  randomized"): the layout repeats, but what fills each marker is freshly rolled every time. */
    public static GenerateResult regenerate(MinecraftServer server, UUID dungeonId, WyrldRecipe recipe) {
        Optional<GeneratedDungeonRecord> saved = GeneratedDungeonStorage.get().load(dungeonId);
        if (saved.isEmpty()) {
            return null;
        }
        GeneratedDungeonRecord record = saved.get();

        List<Placement> placements = new ArrayList<>();
        for (GeneratedDungeonRecord.SavedPlacement savedPlacement : record.placements()) {
            WyrldPrintRecord piece = WyrldPrintStorage.get().load(savedPlacement.pieceName())
                    .filter(r -> r.type() == savedPlacement.pieceType())
                    .orElseThrow(() -> new IllegalStateException("WyrldPrint '" + savedPlacement.pieceName()
                            + "' (" + savedPlacement.pieceType() + ") no longer exists -- can't regenerate dungeon " + dungeonId));
            placements.add(new Placement(piece, savedPlacement.rotation(), savedPlacement.originForward(),
                    savedPlacement.originLateral(), savedPlacement.originY()));
        }
        Placement entrancePlacement = !placements.isEmpty() && placements.get(0).record().type().isAnchor()
                ? placements.get(0) : null;

        Map<LocalPos, BlockState> doorSeals = new HashMap<>();
        for (GeneratedDungeonRecord.SealedCell sealedCell : record.doorSeals()) {
            doorSeals.put(new LocalPos(sealedCell.forward(), sealedCell.lateral(), sealedCell.y()), sealedCell.state());
        }

        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: regenerated dungeon {} ({} piece(s))", dungeonId, placements.size());
        return stamp(server, placements, entrancePlacement, doorSeals, dungeonId, recipe);
    }

    /** Debug-only (see {@code DebugCommands}'s {@code selectiontest}): runs the exact same planning
     *  logic as {@link #generate} without ever touching a real level/Wyrld instance -- {@link
     *  #stamp} is the only part of {@link #generate} that needs one. Lets a selection-frequency
     *  diagnostic run hundreds of trials cheaply, added 2026-10-14 after a real report ("my room
     *  hasn't been selected once") that turned out to need actual counts, not assumptions, to
     *  investigate. @return each placed piece as {@code "<type>:<name>"}, or an empty list if no
     *  ROOM/ENTRANCE WyrldPrints are saved at all. */
    public static List<String> planOnceForDebug(int minRooms, int maxRooms) {
        PlanResult plan = planPlacements(minRooms, maxRooms, Optional.empty(), false, List.of());
        if (plan == null) {
            return List.of();
        }
        return plan.placements().stream().map(p -> p.record().type() + ":" + p.record().name()).toList();
    }

    /** AND semantics, confirmed with the user, 2026-11-11 (the Tag system): a Prynt only matches a
     *  non-empty {@code requiredTags} request if it has every single one of them, not just one.
     *  Empty {@code requiredTags} (the generator asked for nothing in particular) matches anything,
     *  tagged or not; a non-empty request never matches an untagged Prynt, even one tag's worth --
     *  both explicit in the user's own spec. */
    private static boolean matchesTags(WyrldPrintRecord record, List<String> requiredTags) {
        if (requiredTags.isEmpty()) {
            return true;
        }
        if (record.tags().isEmpty()) {
            return false;
        }
        return record.tags().containsAll(requiredTags);
    }

    @SuppressWarnings("deprecation")
    private static PlanResult planPlacements(int minRooms, int maxRooms, Optional<String> entranceName,
                                               boolean useLatestTutorialPrint, List<String> requiredTags) {
        List<WyrldPrintRecord> rooms = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ROOM).filter(r -> matchesTags(r, requiredTags)).toList();
        List<WyrldPrintRecord> connectors = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.CONNECTOR).filter(r -> matchesTags(r, requiredTags)).toList();
        List<WyrldPrintRecord> allEntrances = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ENTRANCE).filter(r -> matchesTags(r, requiredTags)).toList();
        List<WyrldPrintRecord> entrances;
        // WyrldPrintType#TUTORIAL's own resolution -- "whichever saved TUTORIAL is newest," never
        // a pinned name -- takes priority over (and is mutually exclusive with, in practice) the
        // entranceName pin below. Deliberately a HARD failure (return null), not a fallback to a
        // random ENTRANCE, if no TUTORIAL has ever been saved -- real incident, 2026-11-11: a
        // random substitute ENTRANCE has no tutorial content and no guaranteed exit (that's
        // player-placed only, and nobody places one inside a dungeon they never intended to
        // generate), so it silently traps whoever triggers it. Unlike entranceName's own
        // stale-reference fallback below, there is no safe substitute here.
        if (useLatestTutorialPrint) {
            Optional<WyrldPrintRecord> latestTutorial = WyrldPrintStorage.get().loadLatestOfType(WyrldPrintType.TUTORIAL);
            if (latestTutorial.isEmpty()) {
                com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.warn(
                        "OneOffGenerator: recipe wants the latest TUTORIAL print but none has ever been saved -- refusing to generate (no safe substitute)");
                return null;
            }
            entrances = List.of(latestTutorial.get());
        } else {
            // A recipe naming a specific entrance (e.g. Arryval's WelcomeTutorial, which must never
            // land a new player in an unrelated dungeon's entrance) gets pinned to just that one --
            // real bug found live, 2026-11-06: with more than one ENTRANCE WyrldPrint ever saved, every
            // recipe was picking uniformly at random among ALL of them, including ones meant for a
            // completely different recipe. Falls back to the saved piece's own type if the name exists
            // but isn't actually an ENTRANCE, and falls back to the full list (old random behavior) if
            // the name doesn't resolve at all -- logged, not a hard failure, since a stale/renamed
            // reference shouldn't brick generation entirely.
            entrances = allEntrances;
            if (entranceName.isPresent()) {
                List<WyrldPrintRecord> pinned = allEntrances.stream().filter(r -> r.name().equals(entranceName.get())).toList();
                if (pinned.isEmpty()) {
                    com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.warn(
                            "OneOffGenerator: recipe named entrance '{}' not found among saved ENTRANCE WyrldPrints -- falling back to random selection",
                            entranceName.get());
                } else {
                    entrances = pinned;
                }
            }
        }
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
        Placement entrancePlacement = first.record().type().isAnchor() ? first : null;
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
            boolean frontierOwnedByEntrance = frontier.ownerType().isAnchor();
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
        // are surrounding it." This went through three rounds of directional/depth-scanning
        // heuristics first (sample the real neighboring world state through the door, then
        // sideways, then scanned outward several cells, then across the whole fill footprint
        // instead of just the door) -- each one fixed a real, live-confirmed gap and then another
        // live-confirmed gap turned up anyway, because "look nearby and hope something useful is
        // there" has an edge case for every possible nearby layout. Explicit user feedback, same
        // day: "If you think your design will work fine, but it's still not working" -- replaced
        // entirely with something that can't have a geometric edge case because it doesn't look
        // around at all: every unresolved cell in the point's own fill footprint gets sealed with
        // that *same piece's own* most common solid wall material (`representativeWallMaterial`,
        // computed directly from its own saved cells, rotated to match this placement) -- always
        // available for any normally-built piece, regardless of what is or isn't nearby.
        //
        // <p>Originally scoped to CONNECTOR-owned frontiers only -- real user report, same day,
        // found that wrong: "this latest one generated with at least 6 holes," traced to plenty of
        // unresolved ROOM-owned frontiers in the log (a multi-door room with some doors simply never
        // attached to anything). A ROOM's/ENTRANCE's own unused door is stripped to air by the
        // ordinary `isConnectorArtifact` rule exactly the same as a CONNECTOR's, so it needs the
        // exact same sealing -- there was never a real reason to restrict this to one owner type.
        // Scoped to wall-type only -- a floor point has no stored facing to know "through the door,"
        // and this version doesn't need that concept at all anymore, but a floor connector's own
        // vertical shaft shape makes an air opening far less likely to read as a dead end regardless.
        Map<LocalPos, BlockState> doorSeals = new HashMap<>();
        if (!unresolvedFrontiers.isEmpty()) {
            Map<LocalPos, BlockState> realBlocks = buildRealBlockMap(placements);
            for (Frontier frontier : unresolvedFrontiers) {
                if (!frontier.wall() || frontier.facing().isEmpty()) {
                    continue;
                }
                BlockState material = representativeWallMaterial(frontier.ownerPlacement().record());
                if (material == null) {
                    continue; // degenerate connector with no real solid material anywhere in its own geometry -- falls back to air.
                }
                BlockState rotated = material.rotate(frontier.ownerPlacement().rotation());
                for (LocalPos cell : wallFootprintCells(frontier)) {
                    BlockState existing = realBlocks.get(cell);
                    // Real, live-confirmed bug: the door's own 2 cells always fail this check,
                    // since their "existing" content is the connector door/trapdoor block itself --
                    // a genuinely real, non-air block, captured as-is (only the glass fill gets
                    // cleared to air at capture time, never the door). That's not "the player built
                    // something here, leave it alone" the way an overridden buffer cell is -- it's
                    // the exact artifact stamp()'s own isConnectorArtifact rule is about to strip to
                    // air regardless, which is *why* it needs a seal in the first place. Confirmed
                    // directly against the log: a MEDIUM/LARGE connector's door cells logged "no
                    // seal available" while its own buffer cells (genuinely air, correctly skipped
                    // past this check) got sealed fine in the same run. Only a cell whose real
                    // content is something *other* than a connector artifact is genuinely
                    // player-built-over and worth leaving alone.
                    if (existing != null && !existing.isAir() && !isConnectorArtifact(existing)) {
                        continue;
                    }
                    doorSeals.put(cell, rotated);
                }
            }
        }

        return new PlanResult(placements, roomsPlaced, targetRooms, attempts, entrancePlacement, doorSeals);
    }

    /** The most common real (non-air, non-connector-artifact) {@code BlockState} among a
     *  WyrldPrint's own captured cells -- used to seal an unresolved connector point (on any piece
     *  type -- ROOM, ENTRANCE, or CONNECTOR) with its own wall material, deliberately independent of
     *  whatever is or isn't nearby in the final generated layout (see the door-sealing pass's own
     *  doc for why). {@code null} only for
     *  a genuinely degenerate connector with no solid material anywhere in its own geometry. */
    private static BlockState representativeWallMaterial(WyrldPrintRecord record) {
        Map<BlockState, Integer> counts = new HashMap<>();
        for (WyrldPrintCell cell : record.cells()) {
            BlockState state = cell.state();
            if (state.isAir() || isConnectorArtifact(state)) {
                continue;
            }
            counts.merge(state, 1, Integer::sum);
        }
        BlockState best = null;
        int bestCount = -1;
        for (Map.Entry<BlockState, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** Every cell in a wall-type frontier's own fill footprint, in local planning space -- same
     *  shape {@code connector.ConnectorPointFill#fillWall} marks out in the real world (a door's own
     *  2 cells, {@code INCLUDED here} -- unlike that method's own footprint helper, which exists
     *  purely to skip them -- plus the surrounding buffer area for a MEDIUM/LARGE {@code
     *  ConnectorPointSize}), so the door-sealing pass above can treat every one of them uniformly
     *  instead of handling the door separately from its own buffer cells. A no-op footprint (just
     *  the 2 door cells) for {@code SMALL}. */
    private static List<LocalPos> wallFootprintCells(Frontier frontier) {
        List<LocalPos> result = new ArrayList<>();
        int footprint = frontier.size().footprint();
        Direction side = frontier.facing().get().getClockWise();
        int half = Math.max(footprint / 2, 0);
        for (int s = -half; s <= half; s++) {
            for (int y = 0; y < Math.max(footprint, 2); y++) {
                result.add(new LocalPos(frontier.worldForward() + side.getStepX() * s,
                        frontier.worldLateral() + side.getStepZ() * s, frontier.worldY() + y));
            }
        }
        return result;
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

    /**
     * Tunnel generation -- added 2026-11-11, reworked the same day (before any live test) per
     * explicit correction: dead-end branches are completely fine in a Tunnel -- "all that defines it
     * as a tunnel is that there are 2 entrances and exits," not a strict single committed path. This
     * is now a genuine extension of {@link #planPlacements}'s own frontier-growth algorithm (the
     * same branching growth across every open frontier, the same closing pass, the same dead-end
     * door-sealing, all reused verbatim) -- seeded with one {@code ENTRANCE} anchor exactly like
     * normal generation, except growth isn't allowed to finish until a SECOND {@code ENTRANCE} has
     * also been woven in somewhere. That second entrance is tried opportunistically on every
     * frontier once {@code roomCount} real {@code ROOM}s have already been placed (never before, so
     * it can't short-circuit the room count by grabbing the first compatible frontier it sees), and
     * forced onto whatever's still open if growth runs out of frontiers/budget first. Reuses {@code
     * ENTRANCE}'s own existing guarantee (exactly 1 Exit Portal + 1+ Spawn Locations each) for free
     * -- 2 entrances placed inherently means 2 real entrances/exits, nothing extra to build.
     *
     * @return {@code null} if fewer than 2 ENTRANCE WyrldPrints exist at all (not literally checked
     *     up front -- it's simply impossible for the second one to ever find a home with only 1
     *     saved, since it would collide with itself), no CONNECTOR is saved (needed to join an
     *     ENTRANCE to anything else), (when {@code roomCount > 0}) no ROOM is saved, or the second
     *     ENTRANCE genuinely never finds anywhere left to attach.
     */
    private static TunnelPlanResult planTunnelPlacements(int roomCount, List<String> requiredTags, int entranceCount) {
        return planTunnelPlacements(roomCount, requiredTags, entranceCount, null);
    }

    /** @param dests real-world door positions, one per required entrance, in the exact order
     *  {@code stampTunnel} will later zip against -- used (for {@code entranceCount >= 3}) to shape
     *  generation so its underground layout's RELATIVE topology echoes the doors' own real-world
     *  arrangement, rather than being placed with no regard for it at all. {@code null} (or a size
     *  mismatch) falls straight through to the original shape-blind algorithm below, same as before
     *  this parameter existed. */
    private static TunnelPlanResult planTunnelPlacements(int roomCount, List<String> requiredTags, int entranceCount, List<GlobalPos> dests) {
        List<WyrldPrintRecord> rooms = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ROOM).filter(r -> matchesTags(r, requiredTags)).toList();
        List<WyrldPrintRecord> connectors = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.CONNECTOR).filter(r -> matchesTags(r, requiredTags)).toList();
        List<WyrldPrintRecord> entrances = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ENTRANCE).filter(r -> matchesTags(r, requiredTags)).toList();
        if (entrances.isEmpty() || connectors.isEmpty() || (roomCount > 0 && rooms.isEmpty())) {
            return null;
        }
        List<WyrldPrintRecord> endcapRooms = rooms.stream().filter(r -> r.points().size() == 1).toList();
        List<WyrldPrintRecord> multiDoorRooms = rooms.stream().filter(r -> r.points().size() > 1).toList();

        // Explicit user spec, 2026-11-12, after a live-tested 3-entrance Tunnel's underground layout
        // bore no resemblance to its doors' real-world arrangement ("if I make a big L for the
        // entrances, the tunnel exits within the tunnel should more or less be similar, not totally
        // random"): for 3+ real-world doors with known positions, try a shape-aware plan first (see
        // tryShapedTunnelPlan's own doc) -- several attempts, since a deliberately tree-shaped layout
        // is more collision-prone than the fully-opportunistic algorithm below. Falls through to the
        // original, already-proven algorithm if every attempt fails, or if there's no real-world
        // position data to shape from (e.g. a future non-door-based caller) -- shape is a cosmetic
        // improvement, never allowed to make a Tunnel that would otherwise succeed instead fail.
        if (entranceCount >= 3 && dests != null && dests.size() == entranceCount && !multiDoorRooms.isEmpty()) {
            List<WyrldPrintRecord> passThroughRooms = rooms.stream().filter(r -> r.points().size() == 2).toList();
            List<WyrldPrintRecord> forkRooms = rooms.stream().filter(r -> r.points().size() > 2).toList();
            if (!forkRooms.isEmpty()) {
                MergeNode tree = buildMergeTree(dests);
                for (int attempt = 0; attempt < SHAPE_ATTEMPTS; attempt++) {
                    TunnelPlanResult shaped = tryShapedTunnelPlan(tree, entranceCount,
                            connectors, passThroughRooms, forkRooms, endcapRooms, entrances, RandomSource.create());
                    if (shaped != null) {
                        return shaped;
                    }
                }
                com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info("OneOffGenerator: every shape-aware Tunnel attempt failed after {} tries -- falling back to the shape-blind algorithm.", SHAPE_ATTEMPTS);
            }
        }

        RandomSource random = RandomSource.create();
        List<Placement> placements = new ArrayList<>();
        List<Frontier> openFrontiers = new ArrayList<>();
        Map<LocalPos, Boolean> occupied = new HashMap<>();
        List<Frontier> unresolvedFrontiers = new ArrayList<>();

        Placement firstEntrance = new Placement(entrances.get(random.nextInt(entrances.size())), Rotation.NONE, 0, 0, 0);
        placements.add(firstEntrance);
        occupied.putAll(cellStates(firstEntrance));
        addFrontiers(openFrontiers, firstEntrance);
        logPlacement(firstEntrance);

        List<Placement> entranceAnchors = new ArrayList<>();
        entranceAnchors.add(firstEntrance);

        // Explicit user spec, 2026-11-12, after the clustered-entrances concern raised once
        // frontier starvation was fixed above: "fork length based on number of rooms / number of
        // doors... but take 1/2 that value... that way one fork can be a little longer than
        // another and it won't seem stilted." A hard minimum would make every branch an identical,
        // rigid length (its own kind of unnatural); a *minimum* set to half the even-split average
        // still guarantees no branch is absurdly short while leaving real room for natural
        // variance above that floor from ordinary random growth. Integer division cleanly allows
        // 0 (e.g. a short Tunnel with few rooms relative to its entrance count), which was an
        // explicit requirement, not an oversight.
        int minBranchLength = (roomCount / entranceCount) / 2;

        int roomsPlaced = 0;
        int attempts = 0;
        int attemptBudget = Math.max(50, (roomCount + entranceCount) * 60);
        while ((roomsPlaced < roomCount || entranceAnchors.size() < entranceCount) && !openFrontiers.isEmpty() && attempts < attemptBudget) {
            attempts++;
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));

            Placement placed = null;
            // Real live bug, 2026-11-12: a group of 3+ real-world doors could finalize fine (the
            // doors themselves place without issue) but then fail every single generation attempt
            // forever, confirmed via Production's own log -- only 2 of the required 3 ENTRANCE
            // anchors ever got placed before openFrontiers ran dry, so generation returned null
            // every time a player touched any of the 3 doors. Root cause: every ENTRANCE is a dead
            // end (it consumes exactly one frontier and opens zero new ones), so opportunistically
            // cashing one in the instant room growth finishes greedily spent the open-frontier pool
            // down toward zero with no reservation for however many entrances were still needed --
            // fine for the original, already-live-tested 2-entrance case (there's never a "third"
            // slot to starve), but not for 3+. Now only spends a frontier on an entrance here if
            // enough frontiers would still remain afterward to cover however many entrances are
            // still needed past this one -- otherwise this frontier goes to a connector/room
            // instead (which usually opens MORE frontiers), building the pool back up before trying
            // again next iteration. Identical behavior to before whenever entranceCount <= 2 (the
            // slack check is always satisfied then), so the already-confirmed-live 2-door case is
            // unaffected.
            int entranceAnchorsStillNeededAfterThis = entranceCount - entranceAnchors.size() - 1;
            boolean enoughFrontierSlackToSpendOnEntranceNow = openFrontiers.size() >= entranceAnchorsStillNeededAfterThis;
            boolean branchLongEnoughForEntranceNow = frontier.branchDepth() >= minBranchLength;
            if (entranceAnchors.size() < entranceCount && roomsPlaced >= roomCount
                    && enoughFrontierSlackToSpendOnEntranceNow && branchLongEnoughForEntranceNow) {
                placed = tryPlace(frontier, shuffled(entrances, random), occupied, random);
                if (placed != null) {
                    entranceAnchors.add(placed);
                }
            }
            boolean frontierOwnedByRoom = frontier.ownerType() == WyrldPrintType.ROOM;
            boolean frontierOwnedByEntrance = frontier.ownerType().isAnchor();
            boolean tryConnectorFirst = frontierOwnedByEntrance || (frontierOwnedByRoom && random.nextDouble() < PREFER_CONNECTOR_CHANCE);
            if (placed == null && tryConnectorFirst && !connectors.isEmpty()) {
                placed = tryPlace(frontier, shuffled(connectors, random), occupied, random);
            }
            if (placed == null) {
                placed = tryPlace(frontier, shuffled(multiDoorRooms, random), occupied, random);
            }
            if (placed == null) {
                placed = tryPlace(frontier, shuffled(endcapRooms, random), occupied, random);
            }
            if (placed == null) {
                unresolvedFrontiers.add(frontier); // connector, multi-door room, endcap room (and the entrance attempt, if tried) all failed here.
                continue;
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(openFrontiers, placed, frontier.branchDepth());
            if (placed.record().type() == WyrldPrintType.ROOM) {
                roomsPlaced++;
            }
            logPlacement(placed);
        }

        // Every remaining required ENTRANCE is non-negotiable -- unlike an ordinary dungeon's room
        // target, which is allowed to fall short if growth runs dry -- so if fewer than
        // entranceCount landed opportunistically above, force the rest onto whatever's still open
        // before giving up entirely.
        while (entranceAnchors.size() < entranceCount && !openFrontiers.isEmpty()) {
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));
            Placement placed = tryPlace(frontier, shuffled(entrances, random), occupied, random);
            if (placed == null) {
                unresolvedFrontiers.add(frontier);
                continue;
            }
            entranceAnchors.add(placed);
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(openFrontiers, placed); // always zero new frontiers -- only one entrance point is ever consumed per attempt here.
            logPlacement(placed);
        }
        if (entranceAnchors.size() < entranceCount) {
            return null; // genuinely nowhere left for every required entrance to attach -- can't call this a Tunnel.
        }

        // Closing pass: identical to planPlacements' own -- every remaining open frontier gets a
        // 1-door endcap room instead of being left as a real, unused dead end.
        int closingBudget = Math.max(50, openFrontiers.size() * 30);
        int closingAttempts = 0;
        while (!openFrontiers.isEmpty() && closingAttempts < closingBudget) {
            closingAttempts++;
            Frontier frontier = openFrontiers.remove(random.nextInt(openFrontiers.size()));
            if (endcapRooms.isEmpty()) {
                unresolvedFrontiers.add(frontier);
                continue;
            }
            Placement placed = tryPlace(frontier, shuffled(endcapRooms, random), occupied, random);
            if (placed == null) {
                unresolvedFrontiers.add(frontier);
                continue;
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(openFrontiers, placed);
            logPlacement(placed);
        }

        Map<LocalPos, BlockState> doorSeals = sealUnresolvedFrontiers(unresolvedFrontiers, placements);
        return new TunnelPlanResult(placements, entranceAnchors, doorSeals);
    }

    /** A node in the nearest-neighbor merge tree built from a Tunnel group's real-world door
     *  positions (horizontal X/Z only -- real-world Y differences between settlement doors aren't
     *  meaningful to the underground layout's shape, same reasoning {@code TunnelFinalizer}'s own
     *  centroid-facing math already uses). A leaf is a single real-world door, identified by its
     *  own index into the {@code dests} list {@code stampTunnel} will later zip against -- see
     *  {@link #tryShapedTunnelPlan}'s own doc for why that index has to survive all the way through.
     *  An internal node is the merge of its two children at {@code mergeDistance} (the real-world
     *  distance between them at the moment they were merged) -- the tree's own root is simply
     *  whichever merge happened last (always the largest {@code mergeDistance} in the tree, since
     *  nearest-neighbor clustering merges in non-decreasing distance order). */
    record MergeNode(Integer leafEntranceIndex, MergeNode left, MergeNode right, double mergeDistance,
                              double centroidX, double centroidZ) {
        boolean isLeaf() {
            return leafEntranceIndex != null;
        }

        static MergeNode leaf(int entranceIndex, double x, double z) {
            return new MergeNode(entranceIndex, null, null, 0, x, z);
        }

        static MergeNode fork(MergeNode left, MergeNode right, double mergeDistance) {
            return new MergeNode(null, left, right, mergeDistance,
                    (left.centroidX() + right.centroidX()) / 2.0, (left.centroidZ() + right.centroidZ()) / 2.0);
        }
    }

    /** Plain nearest-neighbor (single-linkage-by-centroid) agglomerative clustering: repeatedly
     *  merges whichever two clusters are currently closest, until one tree remains. {@code O(n^2)}
     *  per merge is more than fine at Tunnel-door counts (a handful, not hundreds). */
    static MergeNode buildMergeTree(List<GlobalPos> dests) {
        List<MergeNode> clusters = new ArrayList<>();
        for (int i = 0; i < dests.size(); i++) {
            BlockPos pos = dests.get(i).pos();
            clusters.add(MergeNode.leaf(i, pos.getX(), pos.getZ()));
        }
        while (clusters.size() > 1) {
            int bestI = 0;
            int bestJ = 1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double dx = clusters.get(i).centroidX() - clusters.get(j).centroidX();
                    double dz = clusters.get(i).centroidZ() - clusters.get(j).centroidZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }
            MergeNode merged = MergeNode.fork(clusters.get(bestI), clusters.get(bestJ), bestDist);
            clusters.remove(bestJ); // remove the higher index first so the lower one stays valid
            clusters.remove(bestI);
            clusters.add(merged);
        }
        return clusters.get(0);
    }

    /** Both of a fork's own two child edges get the SAME weight (its own {@code mergeDistance}) --
     *  see {@link #tryShapedTunnelPlan}'s own doc for why both sides of a merge should get a
     *  proportionally equal say in how long their own branch gets. Summed recursively over the
     *  whole tree so every edge's share of the total room budget can be computed as a pure ratio,
     *  independent of the real-world distances' own absolute scale (explicit user spec: "the scale
     *  does not matter to me, just the general shape and proportions"). */
    static double totalEdgeWeight(MergeNode node) {
        if (node.isLeaf()) {
            return 0;
        }
        return 2 * node.mergeDistance() + totalEdgeWeight(node.left()) + totalEdgeWeight(node.right());
    }

    /** The single formula every edge in the shaped tree uses to turn its own real-world merge
     *  distance into a soft-minimum chain length, factored out so root-level and nested forks can
     *  never drift out of sync with each other (real review finding -- this used to be duplicated
     *  verbatim in two places).
     *
     * <p><b>Reworked 2026-11-12</b> -- the original formula split the recipe's own shared
     * {@code roomCount} budget across every edge in proportion to {@code mergeDistance /
     * totalEdgeWeight}, then halved that share. Confirmed broken via a live 6-door Tunnel
     * (`min_rooms: 3`): with a tiny shared budget spread across 5 tree edges, EVERY edge's
     * proportional share rounded to 0 or 1 before the halving step, and halving 1 via integer
     * division is 0 too -- so every edge's floor was 0 regardless of real-world distance, making
     * the whole mechanism a no-op and the layout's shape purely random extra-hop noise (exactly
     * the user's live complaint: "I don't think your implementation to attempt to match
     * entrances/exits is very accurate at all").
     *
     * <p>Fixed by dropping the shared-budget split entirely -- each edge now converts its OWN real
     * {@code mergeDistance} straight into a room count via the same {@code 1 room per 50 blocks}
     * ratio Settlemynts' {@code tunnel.TunnelFinalizer} already uses to size a Tunnel's overall
     * room budget off its doors' diameter ({@link #BLOCKS_PER_ROOM_FOR_SHAPE}), then halves it (same
     * "a minimum, not a forced length" philosophy as before). No edge ever has to compete with its
     * siblings for a slice of a too-small shared number, so a short real-world hop can still
     * legitimately floor at 0 while a long one gets a real, distinct, non-zero floor. */
    private static int edgeTargetLength(double mergeDistance) {
        return (int) Math.round(mergeDistance / BLOCKS_PER_ROOM_FOR_SHAPE) / 2;
    }

    /**
     * One attempt at a Tunnel layout whose underground topology echoes its doors' own real-world
     * relative arrangement -- explicit user spec, 2026-11-12, after a live-tested 3-entrance Tunnel
     * came out "fairly jarring": "the entrances above ground and the entrances in the tunnel have
     * no correlation to each other... if I make a big L for the entrances, the tunnel exits within
     * the tunnel should more or less be similar, not totally random." Confirmed explicitly that
     * absolute scale doesn't matter, only shape/proportions -- which is exactly what a normalized
     * (ratio-based) room-budget split gives for free, independent of whether the real doors are 10
     * blocks or 10,000 blocks apart.
     *
     * <p>Unlike the shape-blind algorithm above (fully opportunistic: grow randomly anywhere until
     * enough frontiers exist, then grab entrances wherever convenient), this walks {@code tree}
     * top-down and places things deliberately: a FORK exactly at every merge node (never
     * opportunistically mid-chain), an ordinary chain of connectors/2-door rooms of a real-world-
     * proportional length along every edge, and the real ENTRANCE exactly at every leaf. The tree's
     * own root -- its last, largest merge -- becomes the very first placement (a fork at the local
     * origin, no parent frontier, same "seed" role {@code firstEntrance} plays in the algorithm
     * above), since starting there means every other node is reached by walking strictly downward,
     * with no need to ever walk back "up" past an ancestor.
     *
     * <p>Each edge's own target chain length comes straight from its own real-world
     * {@code mergeDistance} (see {@link #edgeTargetLength}'s own doc for why this is no longer a
     * shared-budget split across the tree's edges) -- so two close-together doors sharing one
     * small, nearby merge both get short branches off the SAME fork, while a door that only merges
     * in at the root gets routed down one long, separate corridor (see this method's own class doc
     * for the worked "L" example) -- then halved, same "a minimum, not a forced/identical length"
     * philosophy the flat algorithm's own {@code minBranchLength} already established, so ordinary
     * random growth still adds natural variance on top rather than every branch coming out a rigid,
     * identical length.
     *
     * <p>Deliberately bails out (returns {@code null}) rather than falling back to anything
     * mid-attempt on any placement failure -- a half-finished shaped layout is worse than none at
     * all, since {@link #planTunnelPlacements} already retries this whole method several times and
     * falls back to the proven shape-blind algorithm if every attempt fails. Shape is a cosmetic
     * improvement; a Tunnel failing to generate at all is never an acceptable price for it.
     */
    private static TunnelPlanResult tryShapedTunnelPlan(MergeNode tree, int entranceCount,
            List<WyrldPrintRecord> connectors, List<WyrldPrintRecord> passThroughRooms, List<WyrldPrintRecord> forkRooms,
            List<WyrldPrintRecord> endcapRooms, List<WyrldPrintRecord> entrances, RandomSource random) {
        List<Placement> placements = new ArrayList<>();
        Map<LocalPos, Boolean> occupied = new HashMap<>();
        List<Frontier> leftoverFrontiers = new ArrayList<>();
        Placement[] entranceByIndex = new Placement[entranceCount];
        int[] roomsPlaced = {0};

        Placement rootFork = new Placement(forkRooms.get(random.nextInt(forkRooms.size())), Rotation.NONE, 0, 0, 0);
        placements.add(rootFork);
        occupied.putAll(cellStates(rootFork));
        roomsPlaced[0]++;
        logPlacement(rootFork);
        List<Frontier> rootForkFrontiers = new ArrayList<>();
        addFrontiers(rootForkFrontiers, rootFork, 0);
        if (rootForkFrontiers.size() < 2) {
            return null; // the chosen fork piece doesn't actually have 2 usable points -- shouldn't happen given forkRooms' own filter, but never risk it.
        }
        List<Frontier> shuffledRootChildren = shuffled(rootForkFrontiers, random);
        if (shuffledRootChildren.size() > 2) {
            leftoverFrontiers.addAll(shuffledRootChildren.subList(2, shuffledRootChildren.size()));
        }

        int rootChildTarget = edgeTargetLength(tree.mergeDistance());
        boolean leftOk = growShapedEdge(tree.left(), shuffledRootChildren.get(0), rootChildTarget,
                connectors, passThroughRooms, forkRooms, placements, occupied, entranceByIndex, entrances, leftoverFrontiers, roomsPlaced, random);
        if (!leftOk) {
            return null;
        }
        boolean rightOk = growShapedEdge(tree.right(), shuffledRootChildren.get(1), rootChildTarget,
                connectors, passThroughRooms, forkRooms, placements, occupied, entranceByIndex, entrances, leftoverFrontiers, roomsPlaced, random);
        if (!rightOk) {
            return null;
        }
        for (Placement p : entranceByIndex) {
            if (p == null) {
                return null; // a leaf never actually got its real-world entrance placed -- shouldn't happen if growShapedEdge returned true for both sides, but never trust that silently.
            }
        }

        // Same closing pass as the shape-blind algorithm: every leftover frontier (an oversized
        // fork's unused extra point, or a defensive surplus) gets a 1-door endcap room, or is
        // sealed as a plain door if none exists/fits.
        List<Frontier> unresolvedFrontiers = new ArrayList<>();
        int closingBudget = Math.max(50, leftoverFrontiers.size() * 30);
        int closingAttempts = 0;
        while (!leftoverFrontiers.isEmpty() && closingAttempts < closingBudget) {
            closingAttempts++;
            Frontier frontier = leftoverFrontiers.remove(random.nextInt(leftoverFrontiers.size()));
            Placement placed = endcapRooms.isEmpty() ? null : tryPlace(frontier, shuffled(endcapRooms, random), occupied, random);
            if (placed == null) {
                unresolvedFrontiers.add(frontier);
                continue;
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            addFrontiers(leftoverFrontiers, placed, frontier.branchDepth());
        }

        Map<LocalPos, BlockState> doorSeals = sealUnresolvedFrontiers(unresolvedFrontiers, placements);
        return new TunnelPlanResult(placements, List.of(entranceByIndex), doorSeals);
    }

    /** Grows exactly one edge of the shaped tree -- an ordinary (non-forking) chain of connectors/
     *  2-door rooms of roughly {@code targetLength} hops off {@code entryFrontier}, terminating in
     *  either the real ENTRANCE for {@code node} (a leaf) or a nested FORK continuing into both of
     *  {@code node}'s own children (internal) -- see {@link #tryShapedTunnelPlan}'s own doc for the
     *  full design. Deliberately never tries a fork or an endcap room mid-chain (only connectors/
     *  2-door rooms) -- a fork may only ever appear exactly where the tree says one belongs, or the
     *  whole point of this method (a layout that actually matches the tree's own shape) is lost.
     *  @return {@code false} on any placement failure -- the caller abandons this whole attempt. */
    private static boolean growShapedEdge(MergeNode node, Frontier entryFrontier, int targetLength,
            List<WyrldPrintRecord> connectors, List<WyrldPrintRecord> passThroughRooms, List<WyrldPrintRecord> forkRooms,
            List<Placement> placements, Map<LocalPos, Boolean> occupied, Placement[] entranceByIndex, List<WyrldPrintRecord> entrances,
            List<Frontier> leftoverFrontiers, int[] roomsPlaced, RandomSource random) {
        Frontier frontier = entryFrontier;
        int chainBudgetRemaining = targetLength;
        int extraHops = 0;
        int attemptBudget = Math.max(30, (targetLength + MAX_EXTRA_VARIANCE_HOPS) * 20 + 30);
        int attempts = 0;
        while (attempts < attemptBudget) {
            boolean mustContinue = chainBudgetRemaining > 0;
            boolean mayContinue = !mustContinue && extraHops < MAX_EXTRA_VARIANCE_HOPS && random.nextDouble() < EXTENSION_CHANCE;
            if (!mustContinue && !mayContinue) {
                break;
            }
            attempts++;
            // Same invariant the shape-blind algorithm enforces (see this class's own top-level
            // doc): a connector never chains directly onto another connector, only onto a room's
            // (or an anchor's) own frontier -- real bug caught in review, where this used to try a
            // connector unconditionally on every hop regardless of what the frontier's own owner
            // piece was.
            boolean frontierOwnedByConnector = frontier.ownerType() == WyrldPrintType.CONNECTOR;
            Placement placed = frontierOwnedByConnector ? null : tryPlace(frontier, shuffled(connectors, random), occupied, random);
            if (placed == null) {
                placed = tryPlace(frontier, shuffled(passThroughRooms, random), occupied, random);
            }
            if (placed == null) {
                return false;
            }
            placements.add(placed);
            occupied.putAll(cellStates(placed));
            logPlacement(placed);
            if (placed.record().type() == WyrldPrintType.ROOM) {
                roomsPlaced[0]++;
            }
            List<Frontier> next = new ArrayList<>();
            addFrontiers(next, placed, frontier.branchDepth());
            if (next.isEmpty()) {
                return false; // picked a dead-end piece mid-chain -- connectors/2-door rooms should never do this, but never trust that silently.
            }
            frontier = next.get(0);
            if (next.size() > 1) {
                leftoverFrontiers.addAll(next.subList(1, next.size()));
            }
            if (mustContinue) {
                chainBudgetRemaining--;
            } else {
                extraHops++;
            }
        }

        if (node.isLeaf()) {
            Placement entrancePlacement = tryPlace(frontier, shuffled(entrances, random), occupied, random);
            if (entrancePlacement == null) {
                return false;
            }
            placements.add(entrancePlacement);
            occupied.putAll(cellStates(entrancePlacement));
            logPlacement(entrancePlacement);
            entranceByIndex[node.leafEntranceIndex()] = entrancePlacement;
            return true;
        }

        Placement fork = tryPlace(frontier, shuffled(forkRooms, random), occupied, random);
        if (fork == null) {
            return false;
        }
        placements.add(fork);
        occupied.putAll(cellStates(fork));
        logPlacement(fork);
        roomsPlaced[0]++;
        List<Frontier> forkChildren = new ArrayList<>();
        addFrontiers(forkChildren, fork, frontier.branchDepth());
        if (forkChildren.size() < 2) {
            return false;
        }
        List<Frontier> shuffledChildren = shuffled(forkChildren, random);
        if (shuffledChildren.size() > 2) {
            leftoverFrontiers.addAll(shuffledChildren.subList(2, shuffledChildren.size()));
        }
        int childTarget = edgeTargetLength(node.mergeDistance());
        boolean leftOk = growShapedEdge(node.left(), shuffledChildren.get(0), childTarget,
                connectors, passThroughRooms, forkRooms, placements, occupied, entranceByIndex, entrances, leftoverFrontiers, roomsPlaced, random);
        if (!leftOk) {
            return false;
        }
        return growShapedEdge(node.right(), shuffledChildren.get(1), childTarget,
                connectors, passThroughRooms, forkRooms, placements, occupied, entranceByIndex, entrances, leftoverFrontiers, roomsPlaced, random);
    }

    /** Identical sealing logic to {@link #planPlacements}'s own door-sealing pass (see its doc for
     *  the full rationale) -- factored out so the linear Tunnel chain above can reuse it exactly,
     *  rather than duplicating it a second time. */
    private static Map<LocalPos, BlockState> sealUnresolvedFrontiers(List<Frontier> unresolvedFrontiers, List<Placement> placements) {
        Map<LocalPos, BlockState> doorSeals = new HashMap<>();
        if (unresolvedFrontiers.isEmpty()) {
            return doorSeals;
        }
        Map<LocalPos, BlockState> realBlocks = buildRealBlockMap(placements);
        for (Frontier frontier : unresolvedFrontiers) {
            if (!frontier.wall() || frontier.facing().isEmpty()) {
                continue;
            }
            BlockState material = representativeWallMaterial(frontier.ownerPlacement().record());
            if (material == null) {
                continue;
            }
            BlockState rotated = material.rotate(frontier.ownerPlacement().rotation());
            for (LocalPos cell : wallFootprintCells(frontier)) {
                BlockState existing = realBlocks.get(cell);
                if (existing != null && !existing.isAir() && !isConnectorArtifact(existing)) {
                    continue;
                }
                doorSeals.put(cell, rotated);
            }
        }
        return doorSeals;
    }

    private record TunnelPlanResult(List<Placement> placements, List<Placement> entranceAnchors,
                                     Map<LocalPos, BlockState> doorSeals) {
    }

    /** @param instance the generated Tunnel's own Wyrld, already persisted with its lifecycle and
     *  every exit binding baked in (see {@code instance.WyrldManager#resolveExitDestination}).
     *  {@code spawns} is where a player lands entering through each real-world door, indexed by
     *  that door's own {@code roleIndex} -- the caller (the door's own right-click handler) picks
     *  whichever entry matches which door was actually clicked. */
    public record TunnelGenerateResult(WyrldInstanceRecord instance, List<BlockPos> spawns) {
    }

    /**
     * The real entry point for a Tunnel -- builds the whole thing (every anchor, the connecting
     * chain, and every exit-portal-to-main-world binding) in one generation. {@code recipe.minRooms()}
     * is read as the exact room count ({@code recipe.tunnel()} requires {@code minRooms ==
     * maxRooms}, enforced by the caller when the recipe is authored, not re-checked here).
     * {@code dests} is the list of real main-world door positions this Tunnel connects, one per
     * entrance anchor in generation order -- baked into the generated instance's own exit bindings
     * so each end's exit always leads back to its own door, never "wherever the player entered
     * from" (see {@code WyrldExitBinding}'s own doc for the explicit user reasoning). Widened from a
     * fixed two-door {@code destA}/{@code destB} pair to an arbitrary-length list, 2026-11-12, per
     * explicit user spec: "certain settlements could have multiple entrances to the same
     * underground sewer system." @return {@code null} if no valid chain could be built (see
     * {@link #planTunnelPlacements}). */
    public static TunnelGenerateResult generateTunnel(MinecraftServer server, WyrldRecipe recipe,
                                                        List<net.minecraft.core.GlobalPos> dests,
                                                        com.github.cerealklla.tinywyrlds.instance.WyrldLifecycle lifecycle) {
        TunnelPlanResult plan = planTunnelPlacements(recipe.minRooms(), recipe.tags(), dests.size(), dests);
        if (plan == null) {
            return null;
        }
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(
                "OneOffGenerator: generated Tunnel '{}' -- {} piece(s), {} room(s) between {} entrances, lifecycle={}",
                recipe.name(), plan.placements().size(), recipe.minRooms(), dests.size(), lifecycle);
        return stampTunnel(server, plan, dests, lifecycle, recipe);
    }

    @SuppressWarnings("deprecation")
    private static TunnelGenerateResult stampTunnel(MinecraftServer server, TunnelPlanResult plan,
                                                      List<net.minecraft.core.GlobalPos> dests,
                                                      com.github.cerealklla.tinywyrlds.instance.WyrldLifecycle lifecycle, WyrldRecipe recipe) {
        int minF = Integer.MAX_VALUE, maxF = Integer.MIN_VALUE, minL = Integer.MAX_VALUE, maxL = Integer.MIN_VALUE;
        for (Placement placement : plan.placements()) {
            int[] box = worldBoundingBox(placement);
            minF = Math.min(minF, box[0]);
            maxF = Math.max(maxF, box[1]);
            minL = Math.min(minL, box[2]);
            maxL = Math.max(maxL, box[3]);
        }

        WyrldInstanceRecord instance = WyrldManager.createWyrld(server, (maxF - minF) + 2 * MARGIN, (maxL - minL) + 2 * MARGIN, lifecycle);
        ServerLevel level = server.getLevel(WyrldSpaceDimension.KEY);
        if (level == null) {
            return new TunnelGenerateResult(instance, java.util.Collections.nCopies(plan.entranceAnchors().size(), BlockPos.ZERO));
        }
        FreeRect inner = instance.innerRect();
        int worldForwardOffset = inner.x1() + MARGIN - minF;
        int worldLateralOffset = inner.z1() + MARGIN - minL;

        record PendingCell(BlockPos pos, BlockState state, int y, LocalPos localPos) {
        }
        List<PendingCell> pending = new ArrayList<>();
        List<MarkerSpot> markerSpots = new ArrayList<>();
        for (Placement placement : plan.placements()) {
            for (WyrldPrintCell cell : placement.record().cells()) {
                int[] rotatedFL = rotateForwardLateral(cell.relForward(), cell.relLateral(), placement.rotation());
                int localForward = placement.originForward() + rotatedFL[0];
                int localLateral = placement.originLateral() + rotatedFL[1];
                int localY = placement.originY() + cell.relY();
                int worldX = worldForwardOffset + localForward;
                int worldZ = worldLateralOffset + localLateral;
                int worldY = WyrldManager.BASELINE_Y + localY;
                LocalPos localPos = new LocalPos(localForward, localLateral, localY);
                BlockState seal = plan.doorSeals().get(localPos);
                BlockState finalState;
                if (seal != null) {
                    finalState = seal;
                } else {
                    BlockState rotated = cell.state().rotate(placement.rotation());
                    finalState = isConnectorArtifact(rotated) ? Blocks.AIR.defaultBlockState() : rotated;
                }
                BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);
                if (recipe != null && finalState.getBlock() instanceof MarkerBlock marker) {
                    markerSpots.add(new MarkerSpot(worldPos, marker.kind()));
                    finalState = Blocks.AIR.defaultBlockState();
                }
                pending.add(new PendingCell(worldPos, finalState, worldY, localPos));
            }
        }
        pending.sort(Comparator.comparingInt(PendingCell::y));
        for (PendingCell cell : pending) {
            level.setBlock(cell.pos(), cell.state(), 3);
        }

        List<java.util.Set<LocalPos>> anchorCellSets = new ArrayList<>();
        for (Placement anchor : plan.entranceAnchors()) {
            anchorCellSets.add(cellStates(anchor).keySet());
        }
        List<List<BlockPos>> exitCellsPerAnchor = new ArrayList<>();
        for (int i = 0; i < anchorCellSets.size(); i++) {
            exitCellsPerAnchor.add(new ArrayList<>());
        }
        for (PendingCell cell : pending) {
            if (cell.state().getBlock() != ModBlocks.EXIT_PORTAL.get()) {
                continue;
            }
            for (int i = 0; i < anchorCellSets.size(); i++) {
                if (anchorCellSets.get(i).contains(cell.localPos())) {
                    exitCellsPerAnchor.get(i).add(cell.pos());
                    break;
                }
            }
        }
        for (List<BlockPos> exitCells : exitCellsPerAnchor) {
            spawnExitPortalPingMarkers(level, exitCells);
        }

        if (recipe != null) {
            populate(level, markerSpots, recipe);
        }

        BlockPos genericCenter = new BlockPos((int) Math.floor(inner.centerX()), instance.baselineY(), (int) Math.floor(inner.centerZ()));
        List<BlockPos> spawns = new ArrayList<>();
        for (Placement anchor : plan.entranceAnchors()) {
            spawns.add(tunnelSpawnPos(anchor, worldForwardOffset, worldLateralOffset).orElse(genericCenter));
        }

        List<com.github.cerealklla.tinywyrlds.instance.WyrldExitBinding> bindings = new ArrayList<>();
        for (int i = 0; i < exitCellsPerAnchor.size(); i++) {
            net.minecraft.core.GlobalPos dest = dests.get(i);
            for (BlockPos pos : exitCellsPerAnchor.get(i)) {
                bindings.add(new com.github.cerealklla.tinywyrlds.instance.WyrldExitBinding(pos, dest));
            }
        }
        WyrldInstanceRecord finalInstance = instance.withExitBindings(bindings).withTunnelSpawns(spawns);
        com.github.cerealklla.tinywyrlds.space.WyrldSpaceData.get(server).putInstance(finalInstance);

        return new TunnelGenerateResult(finalInstance, spawns);
    }

    private static Optional<BlockPos> tunnelSpawnPos(Placement anchor, int worldForwardOffset, int worldLateralOffset) {
        if (anchor.record().spawnPoints().isEmpty()) {
            return Optional.empty();
        }
        WyrldPrintSpawn spawn = anchor.record().spawnPoints().get(0);
        int[] rotatedFL = rotateForwardLateral(spawn.relForward(), spawn.relLateral(), anchor.rotation());
        int worldX = worldForwardOffset + anchor.originForward() + rotatedFL[0];
        int worldZ = worldLateralOffset + anchor.originLateral() + rotatedFL[1];
        int worldY = WyrldManager.BASELINE_Y + anchor.originY() + spawn.relY();
        return Optional.of(new BlockPos(worldX, worldY, worldZ));
    }

    /** @param spawnPoints every real-world spot a player could land -- the ENTRANCE's own captured
     *  {@code WyrldPrintSpawn} marker(s), translated into real world coordinates. More than one is
     *  expected and fine (explicit user spec, 2026-10-14: "if a party arrives they all need their
     *  own spawn point"); empty if no ENTRANCE was available/placed (caller falls back to the
     *  Wyrld's own generic inner-rect center). {@code dungeonId} is {@code null} when this result
     *  came from {@link #regenerate} -- re-saving an already-saved layout under a new id would just
     *  accumulate duplicate files for no reason. */
    /** @param spawnedEnemyIds every entity UUID the recipe-driven population pass spawned from an
     *  ENEMY pool (never FRIENDLY) -- empty when {@code recipe} was null, see {@link #stamp}'s own
     *  doc. Lets an external caller (e.g. Myssions' entrance-quest bridge) track "defeat this
     *  specific dungeon's guardian" without this mod needing to know what a quest even is. */
    public record GenerateResult(WyrldInstanceRecord instance, List<BlockPos> spawnPoints, UUID dungeonId, List<UUID> spawnedEnemyIds) {
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
        addFrontiers(frontiers, placement, 0);
    }

    /** @param parentBranchDepth the {@link Frontier#branchDepth()} of the frontier {@code placement}
     *  was just attached to (0 for a root placement with no parent, e.g. the first piece of a plan).
     *  Every ordinary (non-Tunnel) caller just uses the 2-arg overload above -- branch depth is only
     *  ever consulted by Tunnel generation, so passing a real value elsewhere would be inert anyway. */
    private static void addFrontiers(List<Frontier> frontiers, Placement placement, int parentBranchDepth) {
        boolean isFork = placement.record().points().size() > 2;
        int childBranchDepth = isFork ? 0 : parentBranchDepth + 1;
        for (WyrldPrintPoint point : placement.record().points()) {
            if (point == placement.consumedPoint()) {
                continue; // this is the point that was just used to attach -- not a new frontier.
            }
            int[] rotatedFL = rotateForwardLateral(point.relForward(), point.relLateral(), placement.rotation());
            int worldForward = placement.originForward() + rotatedFL[0];
            int worldLateral = placement.originLateral() + rotatedFL[1];
            int worldY = placement.originY() + point.relY();
            Optional<Direction> worldFacing = point.facing().map(f -> placement.rotation().rotate(f));
            frontiers.add(new Frontier(worldForward, worldLateral, worldY, point.wall(), worldFacing, placement.record().type(), point.size(), placement, childBranchDepth));
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
     *  (doors, cobblestone, wool, candles, etc.) rotates correctly with no world context.
     *
     * <p>When {@code recipe} is non-null, every marker block (see {@code
     * wyrldprint.marker.MarkerKind}) is cleared to air during stamping and handed to {@link
     * #populate} afterward instead -- a LOOT_* marker becomes a real chest holding one item picked
     * from that tier's pool (or stays air if the pool is empty), ENEMY/FRIENDLY become a real
     * spawned entity picked from the matching pool (or stay air if empty), and INTERACTIVE always
     * becomes a plain lever -- the simplest literal reading of the spec's own "buttons, switches,
     * etc." example, not something a recipe's pools need to pick between. When {@code recipe} is
     * null (the plain {@code generate}/{@code regenerate} overloads), marker blocks are left
     * exactly as captured -- unchanged, pre-existing behavior. */
    @SuppressWarnings("deprecation")
    private static GenerateResult stamp(MinecraftServer server, List<Placement> placements, Placement entrancePlacement,
                                         Map<LocalPos, BlockState> doorSeals, UUID dungeonId, WyrldRecipe recipe) {
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
            return new GenerateResult(instance, List.of(), dungeonId, List.of());
        }
        FreeRect inner = instance.innerRect();
        int worldForwardOffset = inner.x1() + MARGIN - minF;
        int worldLateralOffset = inner.z1() + MARGIN - minL;

        record PendingCell(BlockPos pos, BlockState state, int y, java.util.Optional<String> tag) {
        }
        List<PendingCell> pending = new ArrayList<>();
        List<MarkerSpot> markerSpots = new ArrayList<>();
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
                BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);
                if (recipe != null && finalState.getBlock() instanceof MarkerBlock marker) {
                    markerSpots.add(new MarkerSpot(worldPos, marker.kind()));
                    finalState = Blocks.AIR.defaultBlockState();
                }
                pending.add(new PendingCell(worldPos, finalState, worldY, cell.tag()));
            }
        }
        pending.sort(Comparator.comparingInt(PendingCell::y));
        for (PendingCell cell : pending) {
            level.setBlock(cell.pos(), cell.state(), 3);
            // A tag captured off a TaggedInteractiveBlockEntity in the crafting zone (see
            // WyrldPrintCapture's own doc) -- the one moment this specific placed block gets a real
            // dungeonInstanceId, since no generated instance existed yet when the tag was authored.
            if (cell.tag().isPresent()
                    && level.getBlockEntity(cell.pos()) instanceof com.github.cerealklla.tinywyrlds.wyrldprint.interactive.TaggedInteractiveBlockEntity tagged) {
                tagged.setTagAndInstance(cell.tag().get(), instance.id());
            }
        }

        List<BlockPos> exitPortalCells = new ArrayList<>();
        for (PendingCell cell : pending) {
            if (cell.state().getBlock() == ModBlocks.EXIT_PORTAL.get()) {
                exitPortalCells.add(cell.pos());
            }
        }
        spawnExitPortalPingMarkers(level, exitPortalCells);

        List<UUID> spawnedEnemyIds = recipe != null ? populate(level, markerSpots, recipe) : List.of();

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
        return new GenerateResult(instance, spawnPoints, dungeonId, spawnedEnemyIds);
    }

    /**
     * One {@link ExitPortalPingMarkerEntity} per connected {@code ExitPortalBlock} sheet actually
     * placed by this generation -- a captured Entrance's exit portal is just raw {@code
     * WyrldPrintCell} state like any other block, stamped above with no special handling at all, so
     * (unlike {@code ExitPortalItem#useOn}'s own authoring-time spawn) nothing here would otherwise
     * ever create the marker a real generated instance actually needs. Flood-fills face-adjacent
     * cells (same connectivity concept {@code ExitPortalBlock#breakConnectedNeighbors} already uses
     * for the opposite purpose) purely to dedupe a multi-cell 3x3/5x5 sheet down to exactly one
     * marker, not one per cell.
     */
    private static void spawnExitPortalPingMarkers(ServerLevel level, List<BlockPos> exitPortalCells) {
        java.util.Set<BlockPos> remaining = new java.util.HashSet<>(exitPortalCells);
        while (!remaining.isEmpty()) {
            BlockPos start = remaining.iterator().next();
            java.util.Deque<BlockPos> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            remaining.remove(start);
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.relative(direction);
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            ExitPortalPingMarkerEntity.create(level, start);
        }
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
        if (state.getBlock() instanceof ConnectorArrowBlock || state.getBlock() instanceof FloorConnectorPointBlock) {
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
     *  "enforce connector size matches" -- a small door can no longer join a large one). {@code
     *  ownerPlacement} is the real placement this point belongs to -- used by the door-sealing pass
     *  to find the connector's own wall material when the point never gets consumed. */
    /** {@code branchDepth} is how many non-forking (<=2-door) pieces separate this frontier from
     *  the nearest fork (a 3+-door piece) or the tunnel's own root -- reset to 0 the moment a fork
     *  is placed, incremented by 1 for every ordinary pass-through piece otherwise. Only meaningful
     *  for (and only ever consulted by) {@code planTunnelPlacements}'s own minimum-branch-length
     *  check -- irrelevant, always 0, for ordinary (non-Tunnel) generation. */
    private record Frontier(int worldForward, int worldLateral, int worldY, boolean wall, Optional<Direction> facing,
                             WyrldPrintType ownerType, ConnectorPointSize size, Placement ownerPlacement, int branchDepth) {
    }

    /** A single cell position in the shared local (forward, lateral, Y) planning space every
     *  placement is computed in -- not yet a real {@code BlockPos}, see {@code stamp}. */
    private record LocalPos(int forward, int lateral, int y) {
    }

    /** A marker block's real world position, captured during {@code stamp} and handed to {@link
     *  #populate} once every ordinary cell is placed. */
    private record MarkerSpot(BlockPos pos, MarkerKind kind) {
    }

    /** The actual WyrldRecipe-driven content pass -- see {@code stamp}'s own doc for what each
     *  {@code MarkerKind} becomes. A fresh {@code RandomSource} per call (not shared with planning)
     *  is deliberate: which specific item/entity gets picked should vary between a {@code generate}
     *  and a later {@code regenerate} of the same layout, per the explicit spec {@code regenerate}'s
     *  own doc already quotes. @return every entity UUID spawned from an ENEMY pool specifically
     *  (never FRIENDLY) -- see {@code GenerateResult#spawnedEnemyIds}' own doc for why. */
    private static List<UUID> populate(ServerLevel level, List<MarkerSpot> spots, WyrldRecipe recipe) {
        RandomSource random = RandomSource.create();
        List<UUID> spawnedEnemyIds = new ArrayList<>();
        for (MarkerSpot spot : spots) {
            switch (spot.kind()) {
                case LOOT_COMMON -> placeLoot(level, spot.pos(), recipe.commonLoot(), random);
                case LOOT_RARE -> placeLoot(level, spot.pos(), recipe.rareLoot(), random);
                case LOOT_EPIC -> placeLoot(level, spot.pos(), recipe.epicLoot(), random);
                case ENEMY -> spawnFromPool(level, spot.pos(), recipe.enemyPool(), random).ifPresent(spawnedEnemyIds::add);
                case FRIENDLY -> spawnFromPool(level, spot.pos(), recipe.friendlyPool(), random);
                case INTERACTIVE -> level.setBlock(spot.pos(), Blocks.LEVER.defaultBlockState(), 3);
            }
        }
        return spawnedEnemyIds;
    }

    /** An empty pool leaves the marker's position as the air {@code stamp} already cleared it to --
     *  no recipe content for that tier yet is a real, valid state, not an error. */
    private static void placeLoot(ServerLevel level, BlockPos pos, List<WyrldLootEntry> pool, RandomSource random) {
        if (pool.isEmpty()) {
            return;
        }
        WyrldLootEntry picked = pickWeighted(pool, WyrldLootEntry::weight, random);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, picked.item().copy());
        }
    }

    /** Same "empty pool leaves it air" rule as {@link #placeLoot} -- also skips quietly if the
     *  stored entity type id no longer resolves to a real registered entity (renamed/removed since
     *  the recipe was authored), same defensive lookup Myssions' own {@code QuestMobSpawnTicker}
     *  uses for the identical {@code Identifier}-to-{@code EntityType} resolution. */
    private static Optional<UUID> spawnFromPool(ServerLevel level, BlockPos pos, List<WyrldEntityEntry> pool, RandomSource random) {
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        WyrldEntityEntry picked = pickWeighted(pool, WyrldEntityEntry::weight, random);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(picked.entityTypeId()).orElse(null);
        if (type == null) {
            return Optional.empty();
        }
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) {
            return Optional.empty();
        }
        entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        return Optional.of(entity.getUUID());
    }

    private static <T> T pickWeighted(List<T> pool, java.util.function.ToIntFunction<T> weightOf, RandomSource random) {
        int totalWeight = 0;
        for (T entry : pool) {
            totalWeight += Math.max(0, weightOf.applyAsInt(entry));
        }
        if (totalWeight <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (T entry : pool) {
            cumulative += Math.max(0, weightOf.applyAsInt(entry));
            if (roll < cumulative) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
