package com.github.cerealklla.tinywyrlds.wyrldprint.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/** One weighted entry in a {@link WyrldRecipe}'s enemy or friendly pool -- stored as a plain
 *  {@code Identifier} and resolved to a real {@code EntityType<?>} via {@code
 *  BuiltInRegistries.ENTITY_TYPE} at use-site, same house convention as Myssions'
 *  {@code QuestMilestone#killTargetEntityType}/{@code QuestMobSpawnTicker} rather than a raw
 *  {@code EntityType.CODEC} (no real precedent for that in this codebase). */
public record WyrldEntityEntry(Identifier entityTypeId, int weight) {

    public static final Codec<WyrldEntityEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("entity_type").forGetter(WyrldEntityEntry::entityTypeId),
            Codec.INT.fieldOf("weight").forGetter(WyrldEntityEntry::weight)
    ).apply(i, WyrldEntityEntry::new));
}
