package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A saved WyrldPrint -- a {@code ROOM}, a {@code CONNECTOR}, or an {@code ENTRANCE} (see {@link
 * WyrldPrintType}). No size limit on any type (explicit spec): {@code forwardSize}/{@code
 * lateralSize} are whatever the real captured content's bounding box turned out to be, not a fixed
 * budget.
 */
public record WyrldPrintRecord(int templateVersion, String name, String author, WyrldPrintType type,
                                int forwardSize, int lateralSize, int minY, int maxY,
                                List<WyrldPrintCell> cells, List<WyrldPrintPoint> points,
                                List<WyrldPrintSpawn> spawnPoints) {

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
            WyrldPrintPoint.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(WyrldPrintRecord::points),
            WyrldPrintSpawn.CODEC.listOf().optionalFieldOf("spawn_points", List.of()).forGetter(WyrldPrintRecord::spawnPoints)
    ).apply(i, WyrldPrintRecord::new));
}
