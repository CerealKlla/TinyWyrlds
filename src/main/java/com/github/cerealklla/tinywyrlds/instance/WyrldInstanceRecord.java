package com.github.cerealklla.tinywyrlds.instance;

import java.util.ArrayList;
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
 * <p>{@code lifecycle}/{@code exitBindings}/{@code tunnelSpawns} added 2026-11-11 for Tunnels (see
 * {@code generate.OneOffGenerator#generateTunnel}) -- all default empty/{@code INSTANCED} for every
 * pre-existing instance and every ordinary (non-Tunnel) dungeon, which never populates them.
 * {@code exitBindings} is a flat list rather than a real {@code Map} keyed by {@code BlockPos}
 * purely because Mojang's codec map helpers want a string-encodable key -- {@link
 * WyrldManager#resolveExitDestination} just does the equality scan itself. {@code tunnelSpawns} is
 * indexed by each real-world door's own {@code roleIndex} (see {@code wyrldprint.entrance
 * .TunnelDoorBlockEntity}) -- widened from a fixed {@code tunnelSpawnA}/{@code tunnelSpawnB} pair
 * to an arbitrary-length list, 2026-11-12, for Tunnels with more than 2 real-world doors (explicit
 * user spec: "certain settlements could have multiple entrances to the same underground sewer
 * system").
 */
public record WyrldInstanceRecord(UUID id, FreeRect fullRect, FreeRect innerRect, int baselineY, WyrldState state,
                                   long lastActiveGameTime, WyrldLifecycle lifecycle, List<WyrldExitBinding> exitBindings,
                                   List<BlockPos> tunnelSpawns) {

    public static final Codec<WyrldInstanceRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(WyrldInstanceRecord::id),
            FreeRect.CODEC.fieldOf("full_rect").forGetter(WyrldInstanceRecord::fullRect),
            FreeRect.CODEC.fieldOf("inner_rect").forGetter(WyrldInstanceRecord::innerRect),
            Codec.INT.fieldOf("baseline_y").forGetter(WyrldInstanceRecord::baselineY),
            WyrldState.CODEC.fieldOf("state").forGetter(WyrldInstanceRecord::state),
            Codec.LONG.fieldOf("last_active_game_time").forGetter(WyrldInstanceRecord::lastActiveGameTime),
            WyrldLifecycle.CODEC.optionalFieldOf("lifecycle", WyrldLifecycle.INSTANCED).forGetter(WyrldInstanceRecord::lifecycle),
            WyrldExitBinding.CODEC.listOf().optionalFieldOf("exit_bindings", List.of()).forGetter(WyrldInstanceRecord::exitBindings),
            BlockPos.CODEC.listOf().optionalFieldOf("tunnel_spawns", List.of()).forGetter(WyrldInstanceRecord::tunnelSpawns),
            // Pre-generalization save (fixed tunnel_spawn_a/tunnel_spawn_b pair, 2026-11-11) --
            // these two never get written again (see the getters below), only read back once to
            // rebuild an equivalent tunnel_spawns list for an already-persisted Tunnel instance.
            BlockPos.CODEC.optionalFieldOf("tunnel_spawn_a").forGetter(r -> Optional.<BlockPos>empty()),
            BlockPos.CODEC.optionalFieldOf("tunnel_spawn_b").forGetter(r -> Optional.<BlockPos>empty())
    ).apply(instance, (id, fullRect, innerRect, baselineY, state, lastActiveGameTime, lifecycle, exitBindings, tunnelSpawns, legacySpawnA, legacySpawnB) -> {
        List<BlockPos> resolvedSpawns = tunnelSpawns;
        if (resolvedSpawns.isEmpty() && (legacySpawnA.isPresent() || legacySpawnB.isPresent())) {
            List<BlockPos> rebuilt = new ArrayList<>();
            legacySpawnA.ifPresent(rebuilt::add);
            legacySpawnB.ifPresent(rebuilt::add);
            resolvedSpawns = rebuilt;
        }
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, lifecycle, exitBindings, resolvedSpawns);
    }));

    public WyrldInstanceRecord withState(WyrldState newState, long gameTime) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, newState, gameTime, lifecycle, exitBindings, tunnelSpawns);
    }

    public WyrldInstanceRecord withLifecycle(WyrldLifecycle newLifecycle) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, newLifecycle, exitBindings, tunnelSpawns);
    }

    public WyrldInstanceRecord withExitBindings(List<WyrldExitBinding> newExitBindings) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, lifecycle, newExitBindings, tunnelSpawns);
    }

    /** @param spawns one spawn point per real-world door, indexed by that door's own {@code
     *  roleIndex} within its Tunnel group. */
    public WyrldInstanceRecord withTunnelSpawns(List<BlockPos> spawns) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, state, lastActiveGameTime, lifecycle, exitBindings, spawns);
    }

    public Optional<BlockPos> tunnelSpawn(int roleIndex) {
        return roleIndex >= 0 && roleIndex < tunnelSpawns.size() ? Optional.ofNullable(tunnelSpawns.get(roleIndex)) : Optional.empty();
    }

    public Optional<net.minecraft.core.GlobalPos> exitDestinationFor(BlockPos localExitPos) {
        return exitBindings.stream()
                .filter(b -> b.localExitPos().equals(localExitPos))
                .map(WyrldExitBinding::destination)
                .findFirst();
    }
}
