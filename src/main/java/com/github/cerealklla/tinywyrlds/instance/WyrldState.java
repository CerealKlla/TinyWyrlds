package com.github.cerealklla.tinywyrlds.instance;

import java.util.Locale;

import com.mojang.serialization.Codec;

/**
 * ACTIVE: at least one player has been inside within {@code WyrldManager#STALE_AFTER_TICKS}.
 * STALE: idle past that threshold, but not yet wiped -- re-entering simply resumes it (revives to
 * ACTIVE). After {@code WyrldManager#DELETE_AFTER_TICKS} more idle time, a STALE instance is
 * wiped and its space freed; there's no further state for that, the instance record is just
 * removed.
 */
public enum WyrldState {
    ACTIVE,
    STALE;

    public static final Codec<WyrldState> CODEC = Codec.STRING.xmap(
            s -> WyrldState.valueOf(s.toUpperCase(Locale.ROOT)),
            state -> state.name().toLowerCase(Locale.ROOT)
    );
}
