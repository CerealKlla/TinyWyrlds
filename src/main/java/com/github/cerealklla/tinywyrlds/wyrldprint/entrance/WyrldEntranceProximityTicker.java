package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.github.cerealklla.tinywyrlds.registration.ModEntities;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Explicit user spec, 2026-10-15: "I want the entrance to be similar to actually enter the Wyrld"
 * -- walking into/near it triggers entry, not right-clicking it. Checked every server tick against
 * every online player's own position (cheap: a small per-player bounding-box query, same pattern
 * {@code instance.WyrldManager#occupiedWyrldIds} already uses, not a level-wide entity scan) rather
 * than a block-collision hook, since {@code Entity#setAsInsidePortal} (what {@link
 * ExitPortalBlock} uses for its own, real vanilla walk-through trigger) only exists for blocks, not
 * entities -- and this has to stay an entity for {@link WyrldEntranceEntity#broadcastToPlayer}'s
 * own per-player visibility to work at all (see that class's own doc).
 *
 * <p><b>No re-trigger-while-standing guard needed</b>: {@link WyrldEntranceEntity#enter} teleports
 * the player into Wyrld Space (a different dimension entirely) the instant it succeeds, so the
 * very next tick's distance check naturally no longer finds them nearby -- there's nothing left
 * to debounce.
 */
public final class WyrldEntranceProximityTicker {

    private static final double TRIGGER_DISTANCE_SQR = 1.0 * 1.0;
    private static final double SEARCH_RADIUS = 2.0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AABB box = new AABB(player.getX() - SEARCH_RADIUS, player.getY() - SEARCH_RADIUS, player.getZ() - SEARCH_RADIUS,
                    player.getX() + SEARCH_RADIUS, player.getY() + SEARCH_RADIUS, player.getZ() + SEARCH_RADIUS);
            for (WyrldEntranceEntity entrance : player.level().getEntitiesOfClass(WyrldEntranceEntity.class, box,
                    e -> e.broadcastToPlayer(player))) {
                if (entrance.distanceToSqr(player) <= TRIGGER_DISTANCE_SQR) {
                    entrance.enter(player);
                    break;
                }
            }
        }
    }
}
