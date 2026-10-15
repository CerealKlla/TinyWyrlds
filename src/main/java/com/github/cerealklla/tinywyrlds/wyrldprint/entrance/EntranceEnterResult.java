package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.List;
import java.util.UUID;

/** What happened the last time a player successfully entered a {@link WyrldEntranceEntity} --
 *  stored on the entity itself (not persisted, purely in-memory) so an external listener (e.g.
 *  Myssions' own bridge, registered for the same {@code PlayerInteractEvent.EntityInteract} at a
 *  lower priority than {@code WyrldEntranceInteractListener}) can read back what the entry produced
 *  without this mod needing to know that listener exists at all. */
public record EntranceEnterResult(UUID wyrldId, List<UUID> spawnedEnemyIds, boolean freshlyGenerated) {
}
