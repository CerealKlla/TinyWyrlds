package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A marker that a Tunnel group has already been generated -- explicit user spec: "each tunnel
 * [should get] its own file in generateddungeons, so that it would be as simple as deleting a
 * file there, and deleting the recipe file in the recipe directory, and the server would
 * self-heal... so that we don't have one-off commands to do things." Replaces relying purely on
 * {@code WyrldSpaceData#tunnelAssignments} (a map entry buried inside one shared, server-wide save
 * file with no per-tunnel handle at all) as the thing a door's own {@code enter()} trusts.
 *
 * <p>Deliberately a thin pointer, not a structural replay record like {@link GeneratedDungeonRecord}
 * (which exists to reproduce an *identical* layout on demand) -- a Tunnel has no "regenerate the
 * exact same layout" use case; deleting this file is a request for a genuinely fresh one. All it
 * needs to record is which real {@code WyrldInstanceRecord} (in {@code WyrldSpaceData}, which still
 * owns the actual live Wyrld bookkeeping -- that doesn't move) this group currently resolves to, so
 * {@code TunnelDoorBlockEntity#enter} can check this file FIRST: present and still pointing at a
 * real instance -> resume; missing (deleted) or dangling -> generate fresh, no admin command
 * needed either way.
 *
 * @param playerId present only for a {@code WyrldLifecycle#INSTANCED} group (a private generation
 *     per player) -- empty for {@code PUBLIC} (one shared instance for every entrant), matching
 *     the exact two-shape key {@code TunnelDoorBlockEntity#enter} always used before this file
 *     existed. No real caller passes {@code INSTANCED} today, but the field genuinely supports
 *     it, so this file-naming has to too -- otherwise every player touching an INSTANCED group
 *     would collide onto the first player's own private instance.
 */
public record TunnelGenerationRecord(UUID groupId, Optional<UUID> playerId, UUID wyrldInstanceId) {

    public static final Codec<TunnelGenerationRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("group_id").forGetter(TunnelGenerationRecord::groupId),
            UUIDUtil.CODEC.optionalFieldOf("player_id").forGetter(TunnelGenerationRecord::playerId),
            UUIDUtil.CODEC.fieldOf("wyrld_instance_id").forGetter(TunnelGenerationRecord::wyrldInstanceId)
    ).apply(i, TunnelGenerationRecord::new));
}
