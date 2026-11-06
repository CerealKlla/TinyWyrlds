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
}
