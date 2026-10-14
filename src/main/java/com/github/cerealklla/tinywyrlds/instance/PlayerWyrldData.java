package com.github.cerealklla.tinywyrlds.instance;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

/**
 * Per-player attachment (see {@code registration.ModAttachments#WYRLD_DATA}) tracking which
 * Wyrld (if any) a player is currently inside, and where to send them back to on {@code
 * WyrldManager#leave} -- captured at the moment they entered, since by the time they leave the
 * Wyrld's own space tells us nothing about where they came from.
 */
public record PlayerWyrldData(Optional<UUID> currentWyrldId, Optional<GlobalPos> returnPos) {

    public static final PlayerWyrldData EMPTY = new PlayerWyrldData(Optional.empty(), Optional.empty());

    public static final Codec<PlayerWyrldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("current_wyrld_id").forGetter(PlayerWyrldData::currentWyrldId),
            GlobalPos.CODEC.optionalFieldOf("return_pos").forGetter(PlayerWyrldData::returnPos)
    ).apply(instance, PlayerWyrldData::new));

    public PlayerWyrldData entering(UUID wyrldId, GlobalPos from) {
        return new PlayerWyrldData(Optional.of(wyrldId), Optional.of(from));
    }

    public PlayerWyrldData leaving() {
        return EMPTY;
    }
}
