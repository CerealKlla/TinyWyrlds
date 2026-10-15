package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A saved dungeon layout -- explicit user spec, 2026-10-14: "when a dungeon is generated I'd like
 * the layout to be saved into a GeneratedDungeon folder. Only the room arrangements/doors/etc get
 * saved in this file. Mob Spawn choices, loot box locations, loot pools, mob pools all remain
 * randomized, so we can regenerate the same layout and have a slightly different experience each
 * time." Deliberately structural-only: which real saved WyrldPrints got placed, at what rotation
 * and position, plus the exact door-sealing result from that run (kept verbatim, not recomputed,
 * so "regenerate" reproduces precisely what was generated rather than silently picking up whatever
 * the sealing logic does today) -- nothing about mob/loot content, since none of that exists yet
 * either; this record is deliberately shaped so a future mob/loot system has nowhere to put
 * per-generation random state, keeping that randomized on every regenerate by construction.
 */
public record GeneratedDungeonRecord(UUID id, List<SavedPlacement> placements, List<SealedCell> doorSeals) {

    public static final Codec<GeneratedDungeonRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GeneratedDungeonRecord::id),
            SavedPlacement.CODEC.listOf().fieldOf("placements").forGetter(GeneratedDungeonRecord::placements),
            SealedCell.CODEC.listOf().optionalFieldOf("door_seals", List.of()).forGetter(GeneratedDungeonRecord::doorSeals)
    ).apply(i, GeneratedDungeonRecord::new));

    /** One real placed piece -- references the source WyrldPrint by name/type (re-fetched from
     *  {@code WyrldPrintStorage} at regenerate time) rather than embedding a full copy, so editing
     *  that saved WyrldPrint later is reflected the next time this layout is regenerated, same as
     *  every other part of the mod treats a WyrldPrint's name as its one real identity. */
    public record SavedPlacement(String pieceName, WyrldPrintType pieceType, Rotation rotation,
                                  int originForward, int originLateral, int originY) {
        private static final Codec<Rotation> ROTATION_CODEC = Codec.STRING.xmap(
                s -> Rotation.valueOf(s.toUpperCase(Locale.ROOT)),
                r -> r.name().toLowerCase(Locale.ROOT)
        );

        public static final Codec<SavedPlacement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(SavedPlacement::pieceName),
                WyrldPrintType.CODEC.fieldOf("type").forGetter(SavedPlacement::pieceType),
                ROTATION_CODEC.fieldOf("rotation").forGetter(SavedPlacement::rotation),
                Codec.INT.fieldOf("origin_forward").forGetter(SavedPlacement::originForward),
                Codec.INT.fieldOf("origin_lateral").forGetter(SavedPlacement::originLateral),
                Codec.INT.fieldOf("origin_y").forGetter(SavedPlacement::originY)
        ).apply(i, SavedPlacement::new));
    }

    /** One cell {@code OneOffGenerator}'s door-sealing pass overrode, in the same planning-local
     *  (forward, lateral, y) space {@code SavedPlacement}'s own origins use. */
    public record SealedCell(int forward, int lateral, int y, BlockState state) {
        public static final Codec<SealedCell> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("forward").forGetter(SealedCell::forward),
                Codec.INT.fieldOf("lateral").forGetter(SealedCell::lateral),
                Codec.INT.fieldOf("y").forGetter(SealedCell::y),
                BlockState.CODEC.fieldOf("state").forGetter(SealedCell::state)
        ).apply(i, SealedCell::new));
    }
}
