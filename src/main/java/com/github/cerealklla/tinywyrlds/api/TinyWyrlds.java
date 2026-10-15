package com.github.cerealklla.tinywyrlds.api;

import java.util.UUID;

import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
}
