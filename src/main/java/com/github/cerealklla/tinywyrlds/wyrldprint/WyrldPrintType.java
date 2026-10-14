package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.Locale;

import com.mojang.serialization.Codec;

/**
 * Explicit user spec (2026-10-14): "WorldPrynts will be either Connectors or Rooms, designated
 * upon saving." Chosen by the author at save time (not auto-inferred from content), validated by
 * {@code WyrldPrintCapture}: a {@code CONNECTOR} needs at least 2 connector points ("needs to have
 * 2 of any type of Connector Points, and serves as the hallway between rooms"); a {@code ROOM}
 * needs at least 1 (the original spec's own "Rooms will have defined connector points, at minimum
 * 1"). Neither type has a size limit.
 *
 * <p>{@code ENTRANCE} added same day, later: "treated same as a room, but there is only 1" -- same
 * minimum-connector-point rule as {@code ROOM}, but {@code generate.OneOffGenerator} always places
 * exactly one (as the guaranteed first/anchor piece of a generated layout, preferring it over a
 * random {@code ROOM} when any are saved) and never anywhere else, and requires exactly one {@code
 * wyrldprint.SpawnLocationBlock} marker (see its own doc) so a generated dungeon has a real,
 * specific spot to teleport the player into instead of just the Wyrld's own generic inner-rect
 * center.
 */
public enum WyrldPrintType {
    ROOM,
    CONNECTOR,
    ENTRANCE;

    public static final Codec<WyrldPrintType> CODEC = Codec.STRING.xmap(
            s -> WyrldPrintType.valueOf(s.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT)
    );

    public int minimumConnectorPoints() {
        return this == CONNECTOR ? 2 : 1;
    }
}
