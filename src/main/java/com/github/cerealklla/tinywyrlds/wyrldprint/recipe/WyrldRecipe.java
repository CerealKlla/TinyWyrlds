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
                           Optional<String> questId, Optional<String> entranceName, boolean useLatestTutorialPrint,
                           boolean tunnel, List<String> tags) {

    public static final Codec<WyrldRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(WyrldRecipe::name),
            Codec.INT.fieldOf("min_rooms").forGetter(WyrldRecipe::minRooms),
            Codec.INT.fieldOf("max_rooms").forGetter(WyrldRecipe::maxRooms),
            WyrldLootEntry.CODEC.listOf().optionalFieldOf("common_loot", List.of()).forGetter(WyrldRecipe::commonLoot),
            WyrldLootEntry.CODEC.listOf().optionalFieldOf("rare_loot", List.of()).forGetter(WyrldRecipe::rareLoot),
            WyrldLootEntry.CODEC.listOf().optionalFieldOf("epic_loot", List.of()).forGetter(WyrldRecipe::epicLoot),
            WyrldEntityEntry.CODEC.listOf().optionalFieldOf("enemy_pool", List.of()).forGetter(WyrldRecipe::enemyPool),
            WyrldEntityEntry.CODEC.listOf().optionalFieldOf("friendly_pool", List.of()).forGetter(WyrldRecipe::friendlyPool),
            Codec.STRING.optionalFieldOf("quest_id").forGetter(WyrldRecipe::questId),
            // Pins generation to one specific saved ENTRANCE WyrldPrint by name -- added 2026-11-06,
            // real bug: with more than one ENTRANCE ever saved, every recipe picked uniformly at
            // random among ALL of them (see generate.OneOffGenerator#planPlacements's own doc).
            // Absent (the default, and every pre-existing recipe on disk) keeps the old random
            // behavior unchanged.
            Codec.STRING.optionalFieldOf("entrance_name").forGetter(WyrldRecipe::entranceName),
            // Added 2026-11-11 alongside WyrldPrintType#TUTORIAL (see its own doc) -- when true,
            // the anchor is resolved as "whichever saved TUTORIAL print is newest" instead of
            // entranceName's fixed-name pin into the ENTRANCE pool. Mutually exclusive with
            // entranceName in practice (a recipe uses one mechanism or the other), but nothing
            // enforces that at the type level -- generate.OneOffGenerator just checks this flag
            // first and only falls back to entranceName/ENTRANCE when it's false.
            Codec.BOOL.optionalFieldOf("use_latest_tutorial_print", false).forGetter(WyrldRecipe::useLatestTutorialPrint),
            // Added 2026-11-11 -- see generate.OneOffGenerator#generateTunnel's own doc. When true,
            // minRooms/maxRooms is read as a single EXACT room count (the two must be equal by
            // construction -- a Tunnel's room count is a deliberate, specific number, never a
            // random range) rather than the usual min/max growth target.
            Codec.BOOL.optionalFieldOf("tunnel", false).forGetter(WyrldRecipe::tunnel),
            // Added 2026-11-11, the Tag system -- see generate.OneOffGenerator#matchesTags' own doc.
            // Empty (the default, and every pre-existing recipe on disk) keeps the old untargeted
            // random-pick-among-type behavior exactly as it was.
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(WyrldRecipe::tags)
    ).apply(i, WyrldRecipe::new));

    public static WyrldRecipe empty(String name, int minRooms, int maxRooms) {
        return new WyrldRecipe(name, minRooms, maxRooms, List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), false, false, List.of());
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
            case COMMON -> new WyrldRecipe(name, minRooms, maxRooms, appended(commonLoot, entry), rareLoot, epicLoot, enemyPool, friendlyPool, questId, entranceName, useLatestTutorialPrint, tunnel, tags);
            case RARE -> new WyrldRecipe(name, minRooms, maxRooms, commonLoot, appended(rareLoot, entry), epicLoot, enemyPool, friendlyPool, questId, entranceName, useLatestTutorialPrint, tunnel, tags);
            case EPIC -> new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, appended(epicLoot, entry), enemyPool, friendlyPool, questId, entranceName, useLatestTutorialPrint, tunnel, tags);
        };
    }

    public WyrldRecipe withEnemyAdded(WyrldEntityEntry entry) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, appended(enemyPool, entry), friendlyPool, questId, entranceName, useLatestTutorialPrint, tunnel, tags);
    }

    public WyrldRecipe withFriendlyAdded(WyrldEntityEntry entry) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, appended(friendlyPool, entry), questId, entranceName, useLatestTutorialPrint, tunnel, tags);
    }

    public WyrldRecipe withQuestId(String newQuestId) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, Optional.of(newQuestId), entranceName, useLatestTutorialPrint, tunnel, tags);
    }

    public WyrldRecipe withEntranceName(String newEntranceName) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, questId, Optional.of(newEntranceName), useLatestTutorialPrint, tunnel, tags);
    }

    public WyrldRecipe withUseLatestTutorialPrint(boolean newUseLatestTutorialPrint) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, questId, entranceName, newUseLatestTutorialPrint, tunnel, tags);
    }

    public WyrldRecipe withTunnel(boolean newTunnel) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, questId, entranceName, useLatestTutorialPrint, newTunnel, tags);
    }

    public WyrldRecipe withTagsAdded(String tag) {
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, questId, entranceName, useLatestTutorialPrint, tunnel, appended(tags, tag));
    }

    public WyrldRecipe withTagRemoved(String tag) {
        List<String> without = new java.util.ArrayList<>(tags);
        without.remove(tag);
        return new WyrldRecipe(name, minRooms, maxRooms, commonLoot, rareLoot, epicLoot, enemyPool, friendlyPool, questId, entranceName, useLatestTutorialPrint, tunnel, List.copyOf(without));
    }

    private static <T> List<T> appended(List<T> list, T entry) {
        List<T> copy = new java.util.ArrayList<>(list);
        copy.add(entry);
        return List.copyOf(copy);
    }
}
