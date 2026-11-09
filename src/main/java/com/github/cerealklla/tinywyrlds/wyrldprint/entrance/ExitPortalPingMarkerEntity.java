package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.github.cerealklla.nuclyus.ping.PingPacket;
import com.github.cerealklla.nuclyus.ping.Pingable;
import com.github.cerealklla.tinywyrlds.instance.WyrldExitHooks;
import com.github.cerealklla.tinywyrlds.registration.ModEntities;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * One spawned per placed {@link ExitPortalBlock} sheet (at {@link ExitPortalItem#useOn}'s own
 * centerline anchor), purely so the portal becomes a real {@link Pingable} -- added 2026-11-06,
 * explicit user design: "we should use the player pingable and have it grant the destination popup
 * just before the player actually reaches the portal," replacing a plan to only react at the moment
 * of actual portal contact. Genuinely imperceptible (a {@code Blocks#BARRIER} {@link
 * Display.BlockDisplay}, same "invisible in survival" convention {@code
 * roadway.RoadwayConnectionMarkerEntity} already established in Settlemynts) -- the real portal
 * blocks themselves are already the only thing a player needs to see here; this entity exists solely
 * to be found by {@code ping.client.ClientPingScanner}. Always broadcasts to every player (that
 * scanner can only ever find entities already sent to this client, same reasoning that entity's own
 * doc gives for the identical choice).
 *
 * <p>{@link #onPing} just re-dispatches to {@link WyrldExitHooks#notifyApproach} -- it carries no
 * onboarding-specific logic itself (that lives entirely in Arryval's {@code
 * starttown.OnboardingExitHandler#onApproach}); this stays a generic, reusable "a player is near this
 * Wyrld's own exit" signal, same as every other open hook in this mod.
 */
public class ExitPortalPingMarkerEntity extends Display.BlockDisplay implements Pingable {

    /** This Pingable's own real trigger radius -- see {@link Pingable#radiusBlocks}'s own doc for why
     *  there's no shared value to inherit instead. Explicit user spec, 2026-11-06: "should be like,
     *  4" -- genuinely "just before reaching the portal," not any wider scan ceiling. */
    private static final double APPROACH_RANGE_BLOCKS = 4.0;

    public ExitPortalPingMarkerEntity(EntityType<? extends ExitPortalPingMarkerEntity> type, Level level) {
        super(type, level);
    }

    public static ExitPortalPingMarkerEntity create(ServerLevel level, BlockPos centerPos) {
        ExitPortalPingMarkerEntity marker = new ExitPortalPingMarkerEntity(ModEntities.EXIT_PORTAL_PING_MARKER.get(), level);
        marker.setPos(centerPos.getX() + 0.5, centerPos.getY(), centerPos.getZ() + 0.5);
        marker.setInvulnerable(true);
        BlockDisplayHelper.setBlockState(marker, Blocks.BARRIER.defaultBlockState());
        level.addFreshEntity(marker);
        return marker;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Exit Portal Marker");
    }

    @Override
    public double radiusBlocks() {
        return APPROACH_RANGE_BLOCKS;
    }

    @Override
    public void onPing(PingPacket ping) {
        ServerPlayer player = ping.player();
        if (player.level() instanceof ServerLevel level && level.dimension() == WyrldSpaceDimension.KEY) {
            WyrldExitHooks.notifyApproach(player);
        }
    }
}
