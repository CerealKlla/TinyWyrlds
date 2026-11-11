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
 * random {@code ROOM} when any are saved) and never anywhere else, and requires at least one {@code
 * wyrldprint.SpawnLocationBlock} marker (see its own doc) so a generated dungeon has real, specific
 * spot(s) to teleport player(s) into instead of just the Wyrld's own generic inner-rect center.
 * More than one marker is expected and fine -- "there is only 1" is about the ENTRANCE WyrldPrint
 * itself, not the spawn markers inside it ("if a party arrives they all need their own spawn
 * point," clarified same day).
 *
 * <p>{@code TUTORIAL} added 2026-11-11, explicit user spec: a dedicated anchor type for the
 * Arryval tutorial entrance room specifically, authored/validated identically to {@code ENTRANCE}
 * (same minimum-connector-point rule, same required Spawn Location marker -- see {@code
 * WyrldPrintCapture}), but resolved at generation time by {@code generate.OneOffGenerator} as
 * "whichever saved TUTORIAL print has the newest {@code WyrldPrintRecord#savedAt}," never by a
 * pinned name. Replaces the previous "pin a WyrldRecipe's own {@code entranceName} to one fixed
 * ENTRANCE by name" approach for the tutorial specifically -- that required a new {@code
 * /tinywyrlds recipe setentrance} admin command every time the room was re-captured, exactly the
 * one-time-admin-setup pattern this project's own convention says never to lean on. Re-saving
 * under type {@code TUTORIAL} (any name) always wins automatically on the very next player login,
 * with zero admin action.
 */
public enum WyrldPrintType {
    ROOM,
    CONNECTOR,
    ENTRANCE,
    TUTORIAL;

    public static final Codec<WyrldPrintType> CODEC = Codec.STRING.xmap(
            s -> WyrldPrintType.valueOf(s.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT)
    );

    public int minimumConnectorPoints() {
        return this == CONNECTOR ? 2 : 1;
    }

    /** {@code ENTRANCE} and {@code TUTORIAL} are both "the guaranteed first/anchor piece of a
     *  generated layout, never placed anywhere else, never counted toward min/maxRooms" -- every
     *  generation-time special case that currently reads {@code == ENTRANCE} needs to read this
     *  instead so a TUTORIAL anchor gets identical treatment. */
    public boolean isAnchor() {
        return this == ENTRANCE || this == TUTORIAL;
    }
}
