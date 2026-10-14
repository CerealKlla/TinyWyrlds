package com.github.cerealklla.tinywyrlds.space;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

/**
 * Explicit user spec (2026-10-14): "Advancements should be disabled in the TinyWyrld." Vanilla/
 * NeoForge has no cancelable "about to grant" hook -- both {@code AdvancementEvent.
 * AdvancementEarnEvent} and {@code AdvancementProgressEvent} are explicitly documented as non-
 * cancellable (confirmed by reading NeoForge's own source, not assumed). Implemented instead by
 * immediately revoking any criterion the instant it's granted while the player is in Wyrld Space
 * -- functionally equivalent to disabling advancements there, just reactive by one tick rather
 * than truly preventative. A criterion revoked this way never completes its parent advancement,
 * so nothing is ever actually earned.
 */
public final class AdvancementBlocker {

    @SubscribeEvent
    public void onProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getProgressType() != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().dimension().equals(WyrldSpaceDimension.KEY)) {
            return;
        }
        player.getAdvancements().revoke(event.getAdvancement(), event.getCriterionName());
    }
}
