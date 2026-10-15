package com.github.cerealklla.tinywyrlds.wyrldprint.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

/** One weighted entry in a {@link WyrldRecipe}'s loot pool -- the real item (count included, via
 *  the full {@code ItemStack.CODEC}, same precedent as e.g. Coryspondance's {@code Letter}
 *  attachments) plus its own relative pick weight within that pool. */
public record WyrldLootEntry(ItemStack item, int weight) {

    public static final Codec<WyrldLootEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(WyrldLootEntry::item),
            Codec.INT.fieldOf("weight").forGetter(WyrldLootEntry::weight)
    ).apply(i, WyrldLootEntry::new));
}
