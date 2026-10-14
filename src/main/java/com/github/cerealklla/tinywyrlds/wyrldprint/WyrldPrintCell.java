package com.github.cerealklla.tinywyrlds.wyrldprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.block.state.BlockState;

/**
 * One captured cell of a saved WyrldPrint -- {@code relForward}/{@code relLateral} are coordinates
 * relative to {@link WyrldPrintArea#FRONT_DIRECTION} (the fixed "front" of the crafting zone,
 * explicit user spec: "treat the side facing toward the 50x100 staging area as the front"), same
 * forward/lateral-normalization convention Blueprynts' own {@code BlueprintCell} uses so a future
 * generation-time rotation re-orients correctly. {@code relY} is relative to {@code
 * WyrldManager#BASELINE_Y}. Always a real captured {@link BlockState} (including air -- there's
 * no "pre-existing terrain" sentinel here the way Blueprynts needs one, since the crafting zone is
 * void to begin with, not built on top of existing ground).
 */
public record WyrldPrintCell(int relForward, int relLateral, int relY, BlockState state) {

    public static final Codec<WyrldPrintCell> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("forward").forGetter(WyrldPrintCell::relForward),
            Codec.INT.fieldOf("lateral").forGetter(WyrldPrintCell::relLateral),
            Codec.INT.fieldOf("y").forGetter(WyrldPrintCell::relY),
            BlockState.CODEC.fieldOf("state").forGetter(WyrldPrintCell::state)
    ).apply(i, WyrldPrintCell::new));
}
