package com.github.cerealklla.tinywyrlds.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.tinywyrlds.space.FreeRect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * One live Wyrld: its reserved footprint in Wyrld Space ({@code fullRect}, the padded rect {@code
 * WyrldSpaceAllocator} actually owns; {@code innerRect}, the usable placement area inside it --
 * see that class's own doc for the buffer margin), the shared baseline Y every instance starts
 * from ({@code WyrldManager#BASELINE_Y}), and its lifecycle state.
 *
 * <p>{@code lifecycle}/{@code exitBindings}/{@code tunnelSpawnA}/{@code tunnelSpawnB} added
 * 2026-11-11 for Tunnels (see {@code generate.OneOffGenerator#generateTunnel}) -- all four default
 * empty/{@code INSTANCED} for every pre-existing instance and every ordinary (non-Tunnel) dungeon,
 * which never populates them. {@code exitBindings} is a flat list rather than a real {@code Map}
 * keyed by {@code BlockPos} purely because Mojang's codec map helpers want a string-encodable key
 * -- {@link WyrldManager#resolveExitDestination} just does the equality scan itself, and this list
 * is never more than 2 entries long in practice (one per Tunnel anchor) so that's not a real cost.
 */
public record WyrldInstanceRecord(UUID id, FreeRect fullRect, FreeRect innerRect, int baselineY, WyrldState state,
                                   long lastActiveGameTime, WyrldLifecycle lifecycle, List<WyrldExitBinding> exitBindings,
                                   Optional<BlockPos> tunnelSpawnA, Optional<BlockPos> tunnelSpawnB) {

    public static final Codec<WyrldInstanceRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(WyrldInstanceRecord::id),
            FreeRect.CODEC.fieldOf("full_rect").forGetter(WyrldInstanceRecord::fullRect),
            FreeRect.CODEC.fieldOf("inner_rect").forGetter(WyrldInstanceRecord::innerRect),
            Codec.INT.fieldOf("baseline_y").forGetter(WyrldInstanceRecord::baselineY),
            WyrldState.CODEC.fieldOf("state").forGetter(WyrldInstanceRecord::state),
            Codec.LONG.fieldOf("last_active_game_time").forGetter(WyrldInstanceRecord::lastActiveGameTime),
            WyrldLifecycle.CODEC.optionalFieldOf("lifecycle", WyrldLifecycle.INSTANCED).forGetter(WyrldInstanceRecord::lifecycle),
            WyrldExitBinding.CODEC.listOf().optionalFieldOf("exit_bindings", List.of()).forGetter(WyrldInstanceRecord::exitBindings),
            BlockPos.CODEC.optionalFieldOf("tunnel_spawn_a").forGetter(WyrldInstanceRecord::tunnelSpawnA),
            BlockPos.CODEC.optionalFieldOf("tunnel_spawn_b").forGetter(WyrldInstanceRecord::tunnelSpawnB)
    ).apply(instance, WyrldInstanceRecord::new));

    public WyrldInstanceRecord withState(WyrldState newState, long gameTime) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, newState, gameTime, lifecycle, exitBindings, tunnelSpawnA, tunnelSpawnB);
    }

    public WyrldInstanceRecord withLifecycle(WyrldLifecycle newLifecycle) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, newLifecycle, exitBindings, tunnelSpawnA, tunnelSpawnB);
    }

    public WyrldInstanceRecord withExitBindings(List<WyrldExitBinding> newExitBindings) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, lifecycle, newExitBindings, tunnelSpawnA, tunnelSpawnB);
    }

    public WyrldInstanceRecord withTunnelSpawns(BlockPos spawnA, BlockPos spawnB) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, lifecycle, exitBindings,
                Optional.of(spawnA), Optional.of(spawnB));
    }

    public Optional<net.minecraft.core.GlobalPos> exitDestinationFor(BlockPos localExitPos) {
        return exitBindings.stream()
                .filter(b -> b.localExitPos().equals(localExitPos))
                .map(WyrldExitBinding::destination)
                .findFirst();
    }
}
