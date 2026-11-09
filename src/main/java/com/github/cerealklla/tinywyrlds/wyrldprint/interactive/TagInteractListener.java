package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shift-right-click a {@link TaggedLeverBlock}/{@link TaggedButtonBlock} to open its tag prompt
 * instead of toggling it -- scoped to {@code space.WyrldSpaceDimension} only (covers both the
 * WyrldPrint crafting zone and every live generated instance, since both live in that one shared
 * dimension -- see {@code wyrldprint.WyrldPrintArea}'s own doc). A plain right-click (no shift) is
 * left completely alone, falling through to the block's own real vanilla toggle behavior.
 */
public final class TagInteractListener {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != WyrldSpaceDimension.KEY) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof TaggedInteractiveBlockEntity tagged)) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToPlayer(player, new OpenTagPromptPayload(event.getPos(), tagged.tag()));
    }
}
