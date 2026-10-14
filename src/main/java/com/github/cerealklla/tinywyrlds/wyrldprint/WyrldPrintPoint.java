package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;

import net.minecraft.core.Direction;

/**
 * One connector-point attachment location within a saved WyrldPrint, same forward/lateral/Y
 * convention as {@link WyrldPrintCell}. Position is the connector's lower-half cell for a wall
 * point, or its own cell for a floor point -- redundant with the real block already sitting in
 * {@link WyrldPrintRecord#cells()} at that position, but kept separately so {@code
 * generate.OneOffGenerator} can find every attachment point without re-scanning every cell.
 *
 * <p>{@code facing} is the real captured door {@code FACING} for a wall point -- by this suite's
 * own authoring convention (every generated sample piece follows it, see {@code
 * tools/generate_content.sh}), it points *outward*, away from the piece's own body, through the
 * opening. Two wall points connect correctly when one's facing is the exact opposite of the
 * other's (post-rotation) -- see {@code OneOffGenerator#tryPlace}. Empty for a floor point: a
 * vertical shaft's own captured geometry already determines which direction it extends from
 * either endpoint, so no stored direction is needed there. Empty also for a point captured before
 * this field existed (added 2026-10-14) -- such a point simply can't be matched by the generator.
 */
public record WyrldPrintPoint(int relForward, int relLateral, int relY, boolean wall, ConnectorPointSize size,
                               Optional<Direction> facing) {

    public static final Codec<WyrldPrintPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("forward").forGetter(WyrldPrintPoint::relForward),
            Codec.INT.fieldOf("lateral").forGetter(WyrldPrintPoint::relLateral),
            Codec.INT.fieldOf("y").forGetter(WyrldPrintPoint::relY),
            Codec.BOOL.fieldOf("wall").forGetter(WyrldPrintPoint::wall),
            ConnectorPointSize.CODEC.fieldOf("size").forGetter(WyrldPrintPoint::size),
            Direction.CODEC.optionalFieldOf("facing").forGetter(WyrldPrintPoint::facing)
    ).apply(i, WyrldPrintPoint::new));
}
