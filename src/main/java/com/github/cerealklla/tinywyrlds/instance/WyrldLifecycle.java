package com.github.cerealklla.tinywyrlds.instance;

import java.util.Locale;

import com.mojang.serialization.Codec;

/**
 * Added 2026-11-11 for Tunnels (see {@code generate.OneOffGenerator#generateTunnel}) -- explicit
 * user spec: "Instanced, unique instance of the WyrldPrint object just for that player/party," or
 * "public, any number of players can enter/leave at any time; It's 'permanent'." Deliberately a
 * caller-decided choice per request, not a fixed property of a {@code WyrldRecipe} -- the same
 * recipe content could back either (a settlement might always request PUBLIC for its own stake
 * pairs, a randomly-placed shortcut might not), confirmed explicitly with the user rather than
 * assumed.
 *
 * <p>The only two things this actually changes, both in {@link WyrldManager}: a {@code PUBLIC}
 * instance is exempt from the idle {@code ACTIVE -> STALE -> WIPED} sweep entirely (it's meant to
 * just always exist), and a {@code PUBLIC} pairing resolves to one shared instance id for every
 * entrant (via {@code space.WyrldSpaceData}'s tunnel-assignment map keyed by pair id alone),
 * whereas {@code INSTANCED} keys that same lookup by pair id *and* the entering player's own UUID,
 * so each player/party gets their own private generation the first time they use a given pair --
 * same per-owner privacy idea {@code wyrldprint.entrance.WyrldEntranceEntity} already uses via its
 * own {@code ownerPlayerId}, just keyed centrally instead of per-entity since a door pair is two
 * separate block entities, not one. Generation timing itself is unaffected either way -- both
 * flavors generate lazily on first real entry, per explicit user instruction not to bother with
 * eager generation at placement time ("whichever is easier/less performance heavy").
 */
public enum WyrldLifecycle {
    INSTANCED,
    PUBLIC;

    public static final Codec<WyrldLifecycle> CODEC = Codec.STRING.xmap(
            s -> WyrldLifecycle.valueOf(s.toUpperCase(Locale.ROOT)),
            lifecycle -> lifecycle.name().toLowerCase(Locale.ROOT)
    );
}
