package com.github.cerealklla.tinywyrlds.wyrldprint.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The generation spec from the original spec's own section 3: "settings for minimum and maximum
 * rooms generated, loot pools for each type of loot box, loot pools for enemies, and some kind of
 * possible quest tie-ins." Loot pools are three flat fields (one per {@link LootTier}) rather than
 * a {@code Map<LootTier, List<...>>} -- same "flatten for Codec sanity" precedent Myssions' own
 * {@code QuestInstance} set once nested state got unwieldy, and there are only ever exactly three
 * tiers. {@code friendlyPool} shares {@link WyrldEntityEntry} with {@code enemyPool} -- both are
 * just "a weighted EntityType pool," the only real difference is which {@code MarkerKind} marker
 * they fill in. {@code questId} is a bare, unconsumed placeholder for now (an opaque string, not a
 * real Myssions quest UUID lookup) -- real quest-tie-in behavior needs Myssions' own API wired in,
 * which is separate, not-yet-started work (see design-document.md's Entrance-generation subsystem).
 * An empty pool (the default for a freshly created recipe) means "generation leaves that marker
 * kind alone" -- see {@code generate.OneOffGenerator}'s own population step.
 */
public record WyrldRecipe(String name, int minRooms, int maxRooms,
                           List<WyrldLootEntry> commonLoot, List<WyrldLootEntry> rareLoot, List<WyrldLootEntry> epicLoot,
                           List<WyrldEntityEntry> enemyPool, List<WyrldEntityEntry> friendlyPool,
                           Optional<String> questId) {

    public static final Codec<WyrldRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(WyrldRecipe::name),
            Codec.INT.fieldOf("min_rooms").forGetter(WyrldRecipe::minRooms),
            Codec.INT.fieldOf("max_rooms").forGetter(WyrldRecipe::maxRooms),
            WyrldLootEntry.CODEC.listOf().optionalFieldOf("common_loot", List.of()).forGetter(WyrldRecipe::commonLoot),
            WyrldLootEntry.CODEC.listOf().optionalFieldOf("rare_loot", List.of()).forGetter(WyrldRecipe::rareLoot),
            WyrldLootEntry.CODEC.listOf().optionalFieldOf("epic_loot", List.of()).forGetter(WyrldRecipe::epicLoot),
            WyrldEntityEntry.CODEC.listOf().optionalFieldOf("enemy_pool", List.of()).forGetter(WyrldRecipe::enemyPool),
            WyrldEntityEntry.CODEC.listOf().optionalFieldOf("friendly_pool", List.of()).forGetter(WyrldRecipe::friendlyPool),
            Codec.STRING.optionalFieldOf("quest_id").forGetter(WyrldRecipe::questId)
    ).apply(i, WyrldRecipe::new));

    public static WyrldRecipe empty(String name, int minRooms, int maxRooms) {
        return new WyrldRecipe(name, minRooms, maxRooms, List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty());
    }

    public List<WyrldLootEntry> lootPool(LootTier tier) {
        return switch (tier) {
            case COMMON -> commonLoot;
            case RARE -> rareLoot;
            case EPIC -> epicLoot;
        };
    }

    public WyrldRecipe withLootAdded(LootTier tier, WyrldLootEntry entry) {
        return switch (tier) {
            case COMMON -> new WyrldRecipe(name, minRooms, maxRooms, appended(commonLoot, entry), rareLoot, epicLoot, enemyPool, friendlyPool, questId);
            case RARE -> new WyrldRecipe(name, minRooms, maxRooms, commonLoot, appended(rareLoot, entry), epicLoot, enemyPool, friendlyPool, questId);
            case EPIC -> new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, appended(epicLoot, entry), enemyPool, friendlyPool, questId);
        };
    }

    public WyrldRecipe withEnemyAdded(WyrldEntityEntry entry) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, appended(enemyPool, entry), friendlyPool, questId);
    }

    public WyrldRecipe withFriendlyAdded(WyrldEntityEntry entry) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, appended(friendlyPool, entry), questId);
    }

    public WyrldRecipe withQuestId(String newQuestId) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, Optional.of(newQuestId));
    }

    private static <T> List<T> appended(List<T> list, T entry) {
        List<T> copy = new java.util.ArrayList<>(list);
        copy.add(entry);
        return List.copyOf(copy);
    }
}
