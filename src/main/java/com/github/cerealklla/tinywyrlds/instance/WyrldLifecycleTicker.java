package com.github.cerealklla.tinywyrlds.instance;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodic idle-staleness/deletion sweep -- see {@link WyrldManager#tick}. */
public final class WyrldLifecycleTicker {

    private static final int SCAN_INTERVAL_TICKS = 200; // 10 seconds -- cheap, not latency-sensitive.
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
