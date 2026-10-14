package com.github.cerealklla.tinywyrlds.wyrldprint;

import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Stamps {@link WyrldPrintArea}'s physical structure into Wyrld Space the moment that dimension
 *  loads (every server start, since a custom dimension loads unconditionally like the Nether/End
 *  do) -- {@link WyrldPrintArea#ensureBuilt} is itself idempotent, so this never duplicates work. */
public final class WyrldPrintAreaSetupListener {

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension().equals(WyrldSpaceDimension.KEY)) {
            WyrldPrintArea.ensureBuilt(serverLevel);
        }
    }
}
