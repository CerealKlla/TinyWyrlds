package com.github.cerealklla.tinywyrlds.instance;

import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/**
 * Fires right before a player would leave the Wyrld they're currently in -- the real hook behind
 * {@link WyrldExitHandler}. Confirmed directly against the decompiled vanilla source (not assumed):
 * {@code Entity#teleport(TeleportTransition)} -- the one method every portal-driven teleport goes
 * through, including {@code ExitPortalBlock}'s own {@code Portal} implementation -- calls {@code
 * CommonHooks.onTravelToDimension} (which fires this exact event) unconditionally as its very first
 * check, before doing anything else; cancelling it here means {@code teleport()} returns {@code
 * null} and nothing happens, the entity simply stays put. There is no {@code
 * EntityTeleportEvent.TeleportPortal} in this NeoForge version (an earlier research pass assumed
 * one existed -- it doesn't; re-verified directly here rather than trusting that claim).
 */
public final class WyrldExitListener {

    @SubscribeEvent
    public void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel currentLevel) || currentLevel.dimension() != WyrldSpaceDimension.KEY) {
            return; // Not currently inside a Wyrld at all -- nothing to intercept.
        }
        if (event.getDimension() == currentLevel.dimension()) {
            return; // Not actually leaving (e.g. a same-dimension TeleportTransition inside Wyrld Space itself).
        }
        if (WyrldExitHooks.tryHandle(player)) {
            event.setCanceled(true);
        }
    }
}
