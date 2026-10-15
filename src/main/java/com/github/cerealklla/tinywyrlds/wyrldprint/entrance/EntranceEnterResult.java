package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.List;
import java.util.UUID;

/** What happened the last time a player successfully entered a {@link WyrldEntranceEntity} --
 *  stored on the entity itself (not persisted, purely in-memory) and also mirrored into {@link
 *  EntranceEnterResults} (a global, entity-independent lookup) so an external caller, e.g.
 *  Myssions' own {@code bridge.TinyWyrldsEntranceBridge}, can read back what the entry produced via
 *  a plain synchronous poll, without this mod needing to know that caller exists at all. */
public record EntranceEnterResult(UUID wyrldId, List<UUID> spawnedEnemyIds, boolean freshlyGenerated) {
}
