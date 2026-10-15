package com.github.cerealklla.tinywyrlds.wyrldprint.recipe;

import java.util.Locale;

import com.mojang.serialization.Codec;

/** The three loot-box tiers from the original spec ("various types like common, rare, epic, etc"),
 *  matching {@code wyrldprint.marker.MarkerKind}'s own three LOOT_* kinds one-for-one. Same small-
 *  enum {@code Codec.STRING.xmap} convention as {@code WyrldPrintType}. */
public enum LootTier {
    COMMON,
    RARE,
    EPIC;

    public static final Codec<LootTier> CODEC = Codec.STRING.xmap(
            s -> LootTier.valueOf(s.toUpperCase(Locale.ROOT)),
            tier -> tier.name().toLowerCase(Locale.ROOT)
    );
}
