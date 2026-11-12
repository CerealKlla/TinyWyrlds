package com.github.cerealklla.tinywyrlds.instance;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodic idle-staleness/deletion sweep -- see {@link WyrldManager#tick}. */
public final class WyrldLifecycleTicker {

    // Once per in-game day (24000 ticks, 20 real minutes) -- explicit user correction, 2026-11-12,
    // after widening WyrldManager#tick's own PUBLIC-exemption check to also scan every Tunnel's
    // generateddungeons file: "it doesn't need to react within 10 seconds." The sweep's own grace
    // periods (STALE_AFTER_TICKS = 5 minutes, DELETE_AFTER_TICKS = 15 more) were never meant to be
    // hit to the second anyway -- a day-scale cadence just means staleness/deletion lags by up to
    // one extra day at worst, which is fine for cleanup that was already minutes-grained.
    private static final int SCAN_INTERVAL_TICKS = 24000;
    // Staggered against the suite's other periodic scans (many of which land on round multiples of
    // 100) -- see Myssions' QuestDeadlineTicker for why several uncoordinated "every 100 ticks"
    // scans landing on the same tick caused a real, confirmed lag spike.
    private static final int TICK_OFFSET = 53;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if ((server.getTickCount() + TICK_OFFSET) % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        WyrldManager.tick(server);
    }
}
