package com.github.cerealklla.tinywyrlds.instance;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.GameType;

/**
 * Per-player attachment (see {@code registration.ModAttachments#WYRLD_DATA}) tracking:
 * <ul>
 * <li>{@code currentWyrldId}/{@code returnPos} -- which real Wyrld instance (if any) a player is
 * currently inside and where to send them back to on {@code WyrldManager#leave}, captured at the
 * moment they entered. {@code returnPos} alone (with {@code currentWyrldId} empty) also covers a
 * player sent directly to the WyrldPrint crafting area, which isn't a real numbered instance --
 * {@code leave()} only ever needs {@code returnPos} to work.</li>
 * <li>{@code craftZoneReturnGameType} -- their gamemode from just before {@code
 * wyrldprint.CreativeZoneTicker} swapped them to Creative while inside the WyrldPrint crafting
 * area, so it can be restored exactly on exit.</li>
 * </ul>
 */
public record PlayerWyrldData(Optional<UUID> currentWyrldId, Optional<GlobalPos> returnPos,
                               Optional<GameType> craftZoneReturnGameType) {

    private static final Codec<GameType> GAME_TYPE_CODEC = Codec.STRING.xmap(
            s -> GameType.valueOf(s.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT)
    );

    public static final PlayerWyrldData EMPTY = new PlayerWyrldData(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<PlayerWyrldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("current_wyrld_id").forGetter(PlayerWyrldData::currentWyrldId),
            GlobalPos.CODEC.optionalFieldOf("return_pos").forGetter(PlayerWyrldData::returnPos),
            GAME_TYPE_CODEC.optionalFieldOf("craft_zone_return_game_type").forGetter(PlayerWyrldData::craftZoneReturnGameType)
    ).apply(instance, PlayerWyrldData::new));

    public PlayerWyrldData enteringWyrld(UUID wyrldId, GlobalPos from) {
        return new PlayerWyrldData(Optional.of(wyrldId), Optional.of(from), craftZoneReturnGameType);
    }

    /** Sent directly to the WyrldPrint crafting area -- not a real numbered instance, so {@code
     *  currentWyrldId} stays empty; {@code leave()} only needs {@code returnPos}. */
    public PlayerWyrldData enteringCraftZoneDirectly(GlobalPos from) {
        return new PlayerWyrldData(Optional.empty(), Optional.of(from), craftZoneReturnGameType);
    }

    public PlayerWyrldData leavingWyrld() {
        return new PlayerWyrldData(Optional.empty(), Optional.empty(), craftZoneReturnGameType);
    }

    public PlayerWyrldData withCraftZoneReturnGameType(GameType currentGameType) {
        return new PlayerWyrldData(currentWyrldId, returnPos, Optional.of(currentGameType));
    }

    public PlayerWyrldData withoutCraftZoneReturnGameType() {
        return new PlayerWyrldData(currentWyrldId, returnPos, Optional.empty());
    }
}
