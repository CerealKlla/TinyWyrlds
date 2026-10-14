package com.github.cerealklla.tinywyrlds.wyrldprint;

import com.github.cerealklla.tinywyrlds.instance.PlayerWyrldData;
import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Explicit user spec (2026-10-14): "within this 150x100 players will be able to freely build/
 * destroy/fly like creative mode, no need for food etc." -- implemented literally as a real
 * gamemode swap (the simplest, least-reinvent-the-wheel option; Blueprynts flagged the same
 * design question for its own Construction Site build volume and never actually decided between
 * swapping gamemode vs. faking its individual effects -- this mod just picks the swap).
 *
 * <p>Checked once per second per online player: crossing into {@link WyrldPrintArea#contains} (an
 * X/Z-only check -- any Y, since players fly) while in {@link WyrldSpaceDimension#KEY} remembers
 * their current {@link GameType} and switches them to {@code CREATIVE}; crossing back out (or
 * leaving the dimension entirely) restores it. A player whose attachment already has a remembered
 * return type is treated as "currently swapped," so this is safe to run every tick without
 * double-swapping.
 */
public final class CreativeZoneTicker {

    private static final int SCAN_INTERVAL_TICKS = 20; // 1 second -- needs to feel responsive at the boundary.
    private static final int TICK_OFFSET = 11;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if ((server.getTickCount() + TICK_OFFSET) % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerWyrldData data = player.getData(ModAttachments.WYRLD_DATA);
            boolean inZone = player.level().dimension().equals(WyrldSpaceDimension.KEY)
                    && WyrldPrintArea.contains(player.blockPosition());
            boolean currentlySwapped = data.craftZoneReturnGameType().isPresent();

            if (inZone && !currentlySwapped) {
                player.setData(ModAttachments.WYRLD_DATA, data.withCraftZoneReturnGameType(player.gameMode()));
                player.setGameMode(GameType.CREATIVE);
            } else if (!inZone && currentlySwapped) {
                GameType restore = data.craftZoneReturnGameType().get();
                player.setData(ModAttachments.WYRLD_DATA, data.withoutCraftZoneReturnGameType());
                player.setGameMode(restore);
            }
        }
    }
}
