package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Right-clicking a {@link WyrldEntranceEntity} enters it -- always registered (this is TinyWyrlds'
 *  own entity type, same externalized-interaction precedent as Myssions' own {@code
 *  quest.QuestNpcTalkListener}). Deliberately {@link EventPriority#HIGH} so this runs (and {@link
 *  WyrldEntranceEntity#lastEnterResult()} is populated) before any lower-priority, optional
 *  cross-mod listener (e.g. Myssions' own entrance-quest bridge, registered at default priority)
 *  reads it for the same interaction. */
public final class WyrldEntranceInteractListener {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof WyrldEntranceEntity entrance) || !entrance.broadcastToPlayer(player)) {
            return;
        }
        entrance.enter(player);
        event.setCanceled(true);
    }
}
