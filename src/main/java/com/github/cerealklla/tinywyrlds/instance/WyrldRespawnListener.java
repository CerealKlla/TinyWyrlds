package com.github.cerealklla.tinywyrlds.instance;

import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/** Explicit user spec, 2026-10-15: "When inside a wyrld if a player dies they should respawn at
 *  the Spawn Point they were given when entering the wyrld" -- overrides vanilla's own bed/
 *  world-spawn respawn logic whenever the dying player's {@link PlayerWyrldData#currentWyrldId()}
 *  is still set at the moment of death (i.e. they died without ever leaving), sending them back to
 *  {@link PlayerWyrldData#wyrldSpawnPos()} instead. A player who died after already leaving (
 *  {@code currentWyrldId} empty) respawns completely normally -- this only ever fires while still
 *  genuinely inside a Wyrld. */
public final class WyrldRespawnListener {

    @SubscribeEvent
    public void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerWyrldData data = player.getData(ModAttachments.WYRLD_DATA);
        if (data.currentWyrldId().isEmpty() || data.wyrldSpawnPos().isEmpty()) {
            return;
        }
        ServerLevel wyrldLevel = player.level().getServer().getLevel(WyrldSpaceDimension.KEY);
        if (wyrldLevel == null) {
            return;
        }
        BlockPos pos = data.wyrldSpawnPos().get();
        event.setRespawnLevel(WyrldSpaceDimension.KEY);
        event.setTeleportTransition(new TeleportTransition(wyrldLevel,
                new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
    }
}
