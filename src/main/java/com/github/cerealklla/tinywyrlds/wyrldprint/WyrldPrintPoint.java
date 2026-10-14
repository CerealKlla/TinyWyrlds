package com.github.cerealklla.tinywyrlds.wyrldprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;

/**
 * One connector-point attachment location within a saved WyrldPrint, same forward/lateral/Y
 * convention as {@link WyrldPrintCell}. Position is the connector's lower-half cell for a wall
 * point, or its own cell for a floor point -- redundant with the real block already sitting in
 * {@link WyrldPrintRecord#cells()} at that position, but kept separately so a future dungeon
 * generator (not yet built) can find every attachment point without re-scanning every cell.
 */
public record WyrldPrintPoint(int relForward, int relLateral, int relY, boolean wall, ConnectorPointSize size) {

    public static final Codec<WyrldPrintPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("forward").forGetter(WyrldPrintPoint::relForward),
            Codec.INT.fieldOf("lateral").forGetter(WyrldPrintPoint::relLateral),
            Codec.INT.fieldOf("y").forGetter(WyrldPrintPoint::relY),
            Codec.BOOL.fieldOf("wall").forGetter(WyrldPrintPoint::wall),
            ConnectorPointSize.CODEC.fieldOf("size").forGetter(WyrldPrintPoint::size)
    ).apply(i, WyrldPrintPoint::new));
}
