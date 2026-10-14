package com.github.cerealklla.tinywyrlds.wyrldprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Where {@code generate.OneOffGenerator} teleports a player into a generated layout -- the captured
 * position of a {@link SpawnLocationBlock} marker, same forward/lateral/Y convention as {@link
 * WyrldPrintCell}/{@link WyrldPrintPoint}. Only meaningful on an {@code ENTRANCE}-type WyrldPrint
 * ({@code WyrldPrintCapture} requires exactly one there); harmless and simply unused if somehow
 * present on a {@code ROOM}/{@code CONNECTOR}.
 */
public record WyrldPrintSpawn(int relForward, int relLateral, int relY) {

    public static final Codec<WyrldPrintSpawn> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("forward").forGetter(WyrldPrintSpawn::relForward),
            Codec.INT.fieldOf("lateral").forGetter(WyrldPrintSpawn::relLateral),
            Codec.INT.fieldOf("y").forGetter(WyrldPrintSpawn::relY)
    ).apply(i, WyrldPrintSpawn::new));
}
