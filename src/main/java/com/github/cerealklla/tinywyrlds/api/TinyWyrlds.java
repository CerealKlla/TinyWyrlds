package com.github.cerealklla.tinywyrlds.api;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.instance.WyrldExitHandler;
import com.github.cerealklla.tinywyrlds.instance.WyrldLifecycle;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResult;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResults;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.TunnelDoorBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.TunnelDoorBlockEntity;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceEntity;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldEntityEntry;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipeStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * This mod's first public cross-mod facade -- same "thin wrapper over real internals" convention
 * as every other {@code api.X} in this suite (e.g. {@code api.Myssions}, {@code api.Blueprynts}).
 * The listening mod (e.g. Myssions, for the quest-triggered Entrance variant -- see
 * design-document.md's Entrance-generation subsystem) owns its own {@code bridge.X} wrapper around
 * this, guarded by {@code ModList.get().isLoaded("tinywyrlds")}; this mod never references the
 * caller back.
 */
public final class TinyWyrlds {

    private TinyWyrlds() {
    }

    /** Places a real, per-player-visible {@code WyrldEntranceEntity} at {@code pos} -- {@code
     *  entranceId} is the caller's own identity for it (e.g. a Myssions quest instance's {@code
     *  instanceId}), {@code recipeName} is which saved {@code WyrldRecipe} it generates from the
     *  first time someone enters, and {@code ownerPlayerId} is the one player who can currently see
     *  it at all (per the original spec: "only be visible to players with that quest's UUID
     *  accepted" -- party-sharing more than one owner is real, deferred follow-up work, no party
     *  system exists anywhere in this suite yet). */
    public static void spawnEntrance(ServerLevel level, BlockPos pos, UUID entranceId, String recipeName, UUID ownerPlayerId) {
        WyrldEntranceEntity.create(level, pos, entranceId, recipeName, ownerPlayerId);
    }

    /** Removes every {@code WyrldEntranceEntity} matching {@code entranceId} within a generous
     *  search radius of {@code nearPos} -- for a caller to clean up after itself once its own
     *  entrance is no longer needed (e.g. the quest it belongs to finished or was abandoned). */
    public static void despawnEntrance(ServerLevel level, BlockPos nearPos, UUID entranceId) {
        double radius = 256.0;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                nearPos.getX() - radius, level.getMinY(), nearPos.getZ() - radius,
                nearPos.getX() + radius, level.getMaxY(), nearPos.getZ() + radius);
        for (WyrldEntranceEntity entrance : level.getEntitiesOfClass(WyrldEntranceEntity.class, box,
                e -> entranceId.equals(e.entranceId()))) {
            entrance.discard();
        }
    }

    /** Entity-free entry: generates (or reuses) a Wyrld from {@code recipeName} and teleports
     *  {@code player} straight in, with nothing placed in the world at all -- for a caller that
     *  needs a one-shot "put this player into a Wyrld right now" event rather than a standing,
     *  walk-into-it entrance (e.g. Arryval's new-player onboarding). {@code entranceId} is still the
     *  caller's own identity for this entry, same as {@link #spawnEntrance}'s, so {@link
     *  #getEnterResult} keeps working identically either way. See {@code
     *  WyrldEntranceEntity#enterDirect}'s own doc for why this exists.
     *  @return {@code false} on the same failure cases {@link #spawnEntrance}'s own entity would log
     *      and refuse on (missing recipe, no usable WyrldPrint content, Wyrld Space not loaded). */
    public static boolean enterEntranceDirect(ServerPlayer player, UUID entranceId, String recipeName) {
        return WyrldEntranceEntity.enterDirect(player, entranceId, recipeName);
    }

    /** What the last real entry through this entrance id produced, if any -- a plain synchronous
     *  read against {@code EntranceEnterResults} (see its own doc), so a caller like Myssions'
     *  {@code bridge.TinyWyrldsEntranceBridge} can poll for "has a player entered yet, and what
     *  entity did the ENEMY pool spawn" from its own periodic ticker, matching this suite's own
     *  established bridge-polls-the-source-mod convention rather than needing any shared event. */
    public static Optional<EntranceEnterResult> getEnterResult(UUID entranceId) {
        return EntranceEnterResults.get(entranceId);
    }

    /** See {@link WyrldExitHandler}'s own doc -- a chance to intercept/claim a player's exit from a Wyrld before the real teleport happens. */
    public static void registerExitHandler(WyrldExitHandler handler) {
        com.github.cerealklla.tinywyrlds.instance.WyrldExitHooks.register(handler);
    }

    /**
     * A genuine toggle of the lever/button tagged {@code tag} within dungeon instance {@code
     * dungeonInstanceId} -- on becomes off, off becomes on, unconditionally (see {@code
     * interactive.TaggedInteractiveBlockEntity#toggle}'s own doc for why it must never silently
     * fail to flip). {@code dungeonInstanceId} is the real generated Wyrld's own id (a {@link
     * EntranceEnterResult#wyrldId}, not the entrance id passed to {@link #spawnEntrance}). Added
     * 2026-11-06, explicit user design: "a way for a quest to target a specific lever" -- the tag
     * is authored once, directly onto the block, while building the room (see {@code
     * interactive.TaggedInteractiveBlockEntity}'s own doc for the full design and why there's
     * deliberately no position-keyed lookup table anywhere in this). A no-op if nothing's currently
     * registered under that (instance, tag) pair -- e.g. the tag doesn't exist in this recipe's own
     * WyrldPrint, or (very unlikely in practice) the tagged block's chunk isn't currently loaded.
     */
    public static void toggleState(UUID dungeonInstanceId, String tag) {
        com.github.cerealklla.tinywyrlds.wyrldprint.interactive.TaggedInteractiveRegistry.find(dungeonInstanceId, tag)
                .ifPresent(com.github.cerealklla.tinywyrlds.wyrldprint.interactive.TaggedInteractiveBlockEntity::toggle);
    }

    /**
     * Clears {@code player}'s own "I'm currently inside a Wyrld" tracking state -- real fix,
     * 2026-11-06, for a serious live bug (Arryval's own exit-portal picker): a {@link
     * WyrldExitHandler} that claims an exit and teleports the player itself (rather than letting the
     * real {@code wyrldprint.entrance.ExitPortalBlock} portal mechanism run) was never calling
     * {@code instance.WyrldManager#resolveLeaveDestination} at all, since that only happens inside
     * the vanilla {@code Portal#getPortalDestination} path this exact claim mechanism exists to
     * bypass. Left unresolved, the player's own {@code instance.PlayerWyrldData#currentWyrldId}
     * stayed permanently set to the Wyrld they'd just left -- so {@code instance.WyrldRespawnListener}
     * (which keys purely off that persisted field, not the player's real current dimension) incorrectly
     * sent them back into that Wyrld on their *next* death, anywhere, including a death that happened
     * nowhere near a Wyrld at all. Call this immediately after performing any such direct teleport, in
     * addition to -- not instead of -- the real teleport itself; the discarded return value ({@code
     * instance.WyrldManager.LeaveDestination}) is irrelevant here since the caller already knows
     * exactly where the player is really going.
     */
    public static void markLeftWyrld(ServerPlayer player) {
        com.github.cerealklla.tinywyrlds.instance.WyrldManager.resolveLeaveDestination(player);
    }

    /**
     * Ensures a {@code WyrldRecipe} named {@code name} exists AND has at least the given friendly/
     * enemy content -- creates it from scratch if missing entirely, but also tops up either pool on
     * an *already-existing* recipe if that specific pool is still empty. Added 2026-11-09, explicit
     * user instruction, after this exact gap bit a real player twice: the previously-documented
     * one-time manual admin setup ({@code /tinywyrlds recipe create/addfriendly/setentrance}) had to
     * be run by hand against Production, then had to be run AGAIN for a fresh singleplayer world,
     * since a {@code WyrldRecipe} is per-world persisted data like everything else this mod owns --
     * there is no way to "pre-ship" one.
     *
     * <p><b>Real follow-up bug, same day</b>: the first version of this method only ever checked
     * "does a recipe with this name exist at all," and skipped everything else if so -- a real live
     * report ("still no zombies") traced back to this: a player's own game instance had already
     * created this exact recipe during an earlier test, before an {@code enemy_pool} was ever added
     * to it server-side, so every later fix to Production's own copy was irrelevant to that
     * player's own already-existing, now-permanently-stale local copy -- this method's own "exists"
     * check was satisfied forever regardless. Checking each pool independently, and topping up
     * whichever one is still empty, means a content addition like this self-heals on every instance
     * it's missing from, not just the very first one it's ever run against. Idempotent and safe to
     * call on every server boot (same "generate once if missing," now extended to "and keep
     * reconciled," shape every other {@code nuclyus.server.PersistedServerStore} consumer in this
     * suite otherwise follows) -- a caller like Arryval's own tutorial bootstrap should call this
     * unconditionally at {@code ServerStartingEvent}, every boot, on every world, so neither the
     * recipe nor its intended content can ever go missing again regardless of how the world/server
     * came to exist or what it already had saved from an earlier version of this method's intent.
     *
     * <p>{@code useLatestTutorialPrint} replaced a {@code String entranceName} parameter of the
     * same name/position, 2026-11-11 -- the only real caller (Arryval's tutorial recipe) always
     * passed a fixed {@code ENTRANCE} name, which needed a new one-time {@code /tinywyrlds recipe
     * setentrance} admin command every time the room was re-captured. {@code true} here instead
     * sets {@link WyrldRecipe#useLatestTutorialPrint()} (see {@link WyrldPrintType#TUTORIAL}'s own
     * doc), which self-heals the exact same stale-local-copy way the pool top-up above does: an
     * already-existing recipe that still has this flag {@code false} (e.g. one created before this
     * change) gets flipped to {@code true} on the very next boot, no re-creation needed.
     */
    public static void ensureRecipeExists(String name, int minRooms, int maxRooms,
            Identifier friendlyEntityType, int friendlyWeight,
            Identifier enemyEntityType, int enemyWeight, boolean useLatestTutorialPrint) {
        Optional<WyrldRecipe> existing = WyrldRecipeStorage.get().load(name);
        WyrldRecipe recipe = existing.orElseGet(() -> WyrldRecipe.empty(name, minRooms, maxRooms).withUseLatestTutorialPrint(useLatestTutorialPrint));
        boolean changed = existing.isEmpty();
        if (recipe.friendlyPool().isEmpty()) {
            recipe = recipe.withFriendlyAdded(new WyrldEntityEntry(friendlyEntityType, friendlyWeight));
            changed = true;
        }
        if (recipe.enemyPool().isEmpty()) {
            recipe = recipe.withEnemyAdded(new WyrldEntityEntry(enemyEntityType, enemyWeight));
            changed = true;
        }
        if (useLatestTutorialPrint && !recipe.useLatestTutorialPrint()) {
            recipe = recipe.withUseLatestTutorialPrint(true);
            changed = true;
        }
        if (changed) {
            WyrldRecipeStorage.get().save(recipe);
        }
    }

    /**
     * Tops up an already-existing recipe's {@link com.github.cerealklla.tinywyrlds.wyrldprint.recipe.LootTier
     * #COMMON} loot pool with {@code item} (weight {@code weight}) if that pool is still empty --
     * same self-healing "top up whichever pool is still empty" shape {@link #ensureRecipeExists}
     * already uses for the friendly/enemy pools, just for a guaranteed common-loot item instead of a
     * weighted random one. A no-op if {@code name} doesn't exist at all (call {@link
     * #ensureRecipeExists} first) or already has at least one common-loot entry. Added 2026-11-11
     * for Arryval's tutorial recipe, which seeds the "Kyngdoms, A History" welcome book here instead
     * of granting it as a Kyt (explicit user instruction, same incident as {@code
     * WyrldEntranceEntity#enterDirect}'s own doc).
     */
    public static void ensureCommonLootExists(String name, net.minecraft.world.item.ItemStack item, int weight) {
        WyrldRecipeStorage.get().load(name).ifPresent(recipe -> {
            if (recipe.commonLoot().isEmpty()) {
                WyrldRecipeStorage.get().save(recipe.withLootAdded(
                        com.github.cerealklla.tinywyrlds.wyrldprint.recipe.LootTier.COMMON,
                        new com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldLootEntry(item, weight)));
            }
        });
    }

    /** Same self-healing creates-if-missing idempotent shape as {@link #ensureRecipeExists}, but
     *  for a bare Tunnel recipe with no loot/mob pools at all -- the exact shape {@code
     *  zone.NaturalSettlementTunnelPlacer}'s own "a town's internal shortcut has no loot, no
     *  mobs" use case needs (see {@link WyrldLifecycle}'s own doc, point 1: a stake-placed pair
     *  "might always be no loot, no mobs, public"). Safe to call on every server boot. */
    public static void ensureTunnelRecipeExists(String name, int roomCount) {
        Optional<WyrldRecipe> existing = WyrldRecipeStorage.get().load(name);
        if (existing.isEmpty()) {
            WyrldRecipeStorage.get().save(WyrldRecipe.empty(name, roomCount, roomCount).withTunnel(true));
        }
    }

    /**
     * Places both real-world ends of a Tunnel at once -- see {@code
     * generate.OneOffGenerator#generateTunnel}'s own doc for the full mechanic. {@code recipeName}
     * must already name (or be ensured via {@link #ensureRecipeExists}-style bootstrap) a {@code
     * WyrldRecipe} with {@code tunnel = true} and {@code minRooms == maxRooms} (the exact room
     * count between the two ends). {@code lifecycle} is the caller's own choice per this specific
     * pairing (see {@link WyrldLifecycle}'s own doc -- not a fixed property of the recipe). Nothing
     * is generated yet by this call -- the connecting Wyrld itself is built lazily, the first time
     * either door is actually used (explicit user instruction, "whichever is easier/less
     * performance heavy on the server").
     *
     * <p>{@code facingA}/{@code facingB} orient each door's own placeholder visual to face back
     * toward whatever direction makes sense for its own placement (e.g. outward from the room it
     * sits in) -- this call doesn't try to guess one from the two positions.
     */
    public static void createTunnelDoorPair(ServerLevel levelA, BlockPos posA, Direction facingA,
                                              ServerLevel levelB, BlockPos posB, Direction facingB,
                                              String recipeName, WyrldLifecycle lifecycle) {
        UUID pairId = UUID.randomUUID();
        GlobalPos globalA = GlobalPos.of(levelA.dimension(), posA);
        GlobalPos globalB = GlobalPos.of(levelB.dimension(), posB);

        levelA.setBlock(posA, ModBlocks.TUNNEL_DOOR.get().defaultBlockState().setValue(TunnelDoorBlock.FACING, facingA), 3);
        if (levelA.getBlockEntity(posA) instanceof TunnelDoorBlockEntity doorA) {
            doorA.configure(pairId, true, globalB, recipeName, lifecycle);
        }
        levelB.setBlock(posB, ModBlocks.TUNNEL_DOOR.get().defaultBlockState().setValue(TunnelDoorBlock.FACING, facingB), 3);
        if (levelB.getBlockEntity(posB) instanceof TunnelDoorBlockEntity doorB) {
            doorB.configure(pairId, false, globalA, recipeName, lifecycle);
        }
    }
}
