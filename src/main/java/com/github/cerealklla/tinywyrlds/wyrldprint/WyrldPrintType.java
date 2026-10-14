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
 */
public enum WyrldPrintType {
    ROOM,
    CONNECTOR;

    public static final Codec<WyrldPrintType> CODEC = Codec.STRING.xmap(
            s -> WyrldPrintType.valueOf(s.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT)
    );

    public int minimumConnectorPoints() {
        return this == CONNECTOR ? 2 : 1;
    }
}
