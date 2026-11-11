package com.github.cerealklla.tinywyrlds.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

/**
 * Binds one specific exit-portal cell inside a Wyrld (in local Wyrld-space {@code localExitPos})
 * to a real main-world destination -- added 2026-11-11 for Tunnels. Confirmed explicit user spec:
 * each Entrance Portion's own exit always leads back to its own real-world door, never to
 * "wherever the player entered from" (the latter was correctly flagged as wrong by the user --
 * "the player could turn around mid way through the tunnel and come back out the same entrance
 * they entered," which the old entry-history-based {@code PlayerWyrldData#returnPos} model would
 * mishandle for a two-ended structure). {@code wyrldprint.entrance.ExitPortalBlock} consults
 * {@link WyrldManager#resolveExitDestination} (which checks these bindings first) instead of
 * {@link WyrldManager#resolveLeaveDestination} directly, but that entry-history fallback is kept
 * and still used for every other exit (an admin teleported into the craft zone, a debug command,
 * anything not part of a bound pairing) -- explicit user instruction, not removed.
 */
public record WyrldExitBinding(BlockPos localExitPos, GlobalPos destination) {

    public static final Codec<WyrldExitBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("local_exit_pos").forGetter(WyrldExitBinding::localExitPos),
            GlobalPos.CODEC.fieldOf("destination").forGetter(WyrldExitBinding::destination)
    ).apply(i, WyrldExitBinding::new));
}
