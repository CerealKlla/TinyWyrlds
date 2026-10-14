package com.github.cerealklla.tinywyrlds.instance;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.tinywyrlds.space.FreeRect;

import net.minecraft.core.UUIDUtil;

/**
 * One live Wyrld: its reserved footprint in Wyrld Space ({@code fullRect}, the padded rect {@code
 * WyrldSpaceAllocator} actually owns; {@code innerRect}, the usable placement area inside it --
 * see that class's own doc for the buffer margin), the shared baseline Y every instance starts
 * from ({@code WyrldManager#BASELINE_Y}), and its lifecycle state.
 *
 * <p>No room/content data yet -- this slice is purely the lifecycle (reserve space, teleport
 * in/out, go stale, get wiped). See context/design-document.md subsystems 2-4 (room blueprints,
 * WyrldRecipe, Entrance generation) for what layers on top of this later.
 */
public record WyrldInstanceRecord(UUID id, FreeRect fullRect, FreeRect innerRect, int baselineY, WyrldState state,
                                   long lastActiveGameTime) {

    public static final Codec<WyrldInstanceRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(WyrldInstanceRecord::id),
            FreeRect.CODEC.fieldOf("full_rect").forGetter(WyrldInstanceRecord::fullRect),
            FreeRect.CODEC.fieldOf("inner_rect").forGetter(WyrldInstanceRecord::innerRect),
            Codec.INT.fieldOf("baseline_y").forGetter(WyrldInstanceRecord::baselineY),
            WyrldState.CODEC.fieldOf("state").forGetter(WyrldInstanceRecord::state),
            Codec.LONG.fieldOf("last_active_game_time").forGetter(WyrldInstanceRecord::lastActiveGameTime)
    ).apply(instance, WyrldInstanceRecord::new));

    public WyrldInstanceRecord withState(WyrldState newState, long gameTime) {
        return new WyrldInstanceRecord(id, fullRect, innerRect, baselineY, newState, gameTime);
    }
}
