package com.github.cerealklla.tinywyrlds.instance;

import net.minecraft.server.level.ServerPlayer;

/**
 * Open registration, no pairwise dependency needed -- same "open registry" shape as Nuclyus's own
 * {@code menu.client.MenuContributor}/{@code ping.Pingable}. Registered via {@code
 * api.TinyWyrlds#registerExitHandler}, called by {@code WyrldExitListener} right before a player
 * would otherwise leave the Wyrld they're currently in (any dimension change while inside {@code
 * wyrldprint.space.WyrldSpaceDimension}, not specifically via {@code ExitPortalBlock} -- that's the
 * only way out today, but this hook doesn't assume it).
 */
public interface WyrldExitHandler {

    /**
     * @return {@code true} to claim this exit -- the real dimension travel is cancelled entirely,
     * and this handler is now responsible for whatever happens to the player next (teleporting them
     * somewhere itself, opening a screen first, etc.). {@code false} leaves the exit alone for the
     * next registered handler (or, if none claim it, the original teleport proceeds normally).
     */
    boolean tryHandle(ServerPlayer player);

    /**
     * Fired early, proactively, while a player is merely near a placed {@code
     * wyrldprint.entrance.ExitPortalBlock} -- real {@link com.github.cerealklla.nuclyus.ping.Pingable}
     * dispatch from {@code wyrldprint.entrance.ExitPortalPingMarkerEntity}, not any kind of teleport
     * attempt, so there's nothing to cancel and no return value. Added 2026-11-06, explicit user
     * design: "we should use the player pingable and have it grant the destination popup just before
     * the player actually reaches the portal" -- lets a handler show a UI (Arryval's own starting-
     * settlement picker) before the player actually steps into the portal, rather than only reacting
     * at the moment they'd already be mid-teleport. Every registered handler gets this call (unlike
     * {@link #tryHandle}'s first-claim-wins shape) since nothing here is being claimed/cancelled.
     * Default no-op -- {@link #tryHandle} on physical contact stays a required fallback for any
     * handler that doesn't care about the earlier proactive moment, or in case a player reaches the
     * portal before ever being pinged (e.g. its chunk just loaded, or they beat the scan interval).
     */
    default void onApproach(ServerPlayer player) {
    }
}
