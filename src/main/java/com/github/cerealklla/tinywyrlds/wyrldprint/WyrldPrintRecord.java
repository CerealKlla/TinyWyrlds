package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A saved WyrldPrint -- either a {@code ROOM} or a {@code CONNECTOR} (see {@link WyrldPrintType}).
 * No size limit on either type (explicit spec): {@code forwardSize}/{@code lateralSize} are
 * whatever the real captured content's bounding box turned out to be, not a fixed budget.
 */
public record WyrldPrintRecord(int templateVersion, String name, String author, WyrldPrintType type,
                                int forwardSize, int lateralSize, int minY, int maxY,
                                List<WyrldPrintCell> cells, List<WyrldPrintPoint> points) {

    public static final int CURRENT_TEMPLATE_VERSION = 1;

    public static final Codec<WyrldPrintRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("template_version", 1).forGetter(WyrldPrintRecord::templateVersion),
            Codec.STRING.fieldOf("name").forGetter(WyrldPrintRecord::name),
            Codec.STRING.fieldOf("author").forGetter(WyrldPrintRecord::author),
            WyrldPrintType.CODEC.fieldOf("type").forGetter(WyrldPrintRecord::type),
            Codec.INT.fieldOf("forward_size").forGetter(WyrldPrintRecord::forwardSize),
            Codec.INT.fieldOf("lateral_size").forGetter(WyrldPrintRecord::lateralSize),
            Codec.INT.fieldOf("min_y").forGetter(WyrldPrintRecord::minY),
            Codec.INT.fieldOf("max_y").forGetter(WyrldPrintRecord::maxY),
            WyrldPrintCell.CODEC.listOf().fieldOf("cells").forGetter(WyrldPrintRecord::cells),
            WyrldPrintPoint.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(WyrldPrintRecord::points)
    ).apply(i, WyrldPrintRecord::new));
}
