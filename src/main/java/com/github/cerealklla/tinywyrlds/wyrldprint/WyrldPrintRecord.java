package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A saved WyrldPrint -- a {@code ROOM}, a {@code CONNECTOR}, an {@code ENTRANCE}, or a {@code
 * TUTORIAL} (see {@link WyrldPrintType}). No size limit on any type (explicit spec): {@code
 * forwardSize}/{@code lateralSize} are whatever the real captured content's bounding box turned
 * out to be, not a fixed budget.
 *
 * <p>{@code savedAt} added 2026-11-11 (epoch millis) specifically so "which saved {@code TUTORIAL}
 * print is the latest one" never needs to parse anything out of {@code name} -- {@link
 * WyrldPrintStorage#save} always stamps it fresh on every write, regardless of what the caller
 * passed in, so it's trustworthy as a real save-order key even for records that predate this field
 * (defaulted to 0 for those, which only matters if one of them is somehow still the newest
 * TUTORIAL on disk, i.e. never in practice).
 */
public record WyrldPrintRecord(int templateVersion, String name, String author, WyrldPrintType type,
                                int forwardSize, int lateralSize, int minY, int maxY,
                                List<WyrldPrintCell> cells, List<WyrldPrintPoint> points,
                                List<WyrldPrintSpawn> spawnPoints, long savedAt, List<String> tags) {

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
            WyrldPrintSpawn.CODEC.listOf().optionalFieldOf("spawn_points", List.of()).forGetter(WyrldPrintRecord::spawnPoints),
            Codec.LONG.optionalFieldOf("saved_at", 0L).forGetter(WyrldPrintRecord::savedAt),
            // Added 2026-11-11 (the Tag system, see wyrldprint.tag.TagApprovalRelay/Nuclyus#approveTag)
            // -- free-text descriptors (e.g. "Orcish", "Dark"), used by generate.OneOffGenerator to
            // filter which prints a tag-constrained recipe can ever pick. Absent/empty is the only
            // state every pre-existing saved print has, and must mean "never matches a tag-filtered
            // query" -- see OneOffGenerator#matchesTags' own doc for why that's not the same as "no tags = match anything."
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(WyrldPrintRecord::tags)
    ).apply(i, WyrldPrintRecord::new));

    public WyrldPrintRecord withSavedAt(long newSavedAt) {
        return new WyrldPrintRecord(templateVersion, name, author, type, forwardSize, lateralSize, minY, maxY,
                cells, points, spawnPoints, newSavedAt, tags);
    }

    public WyrldPrintRecord withTags(List<String> newTags) {
        return new WyrldPrintRecord(templateVersion, name, author, type, forwardSize, lateralSize, minY, maxY,
                cells, points, spawnPoints, savedAt, newTags);
    }
}
