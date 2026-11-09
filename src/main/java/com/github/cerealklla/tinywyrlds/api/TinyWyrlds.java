package com.github.cerealklla.tinywyrlds.api;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.instance.WyrldExitHandler;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResult;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResults;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceEntity;

import net.minecraft.core.BlockPos;
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
}
