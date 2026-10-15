package com.github.cerealklla.tinywyrlds.wyrldprint.recipe;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the pure data-shape logic of {@link WyrldRecipe} that needs no live item/entity
 *  registry (unlike the Codec itself, which embeds {@code ItemStack.CODEC} -- see {@code
 *  OneOffGeneratorTest}'s own doc for why registry-dependent logic isn't unit-tested here). */
class WyrldRecipeTest {

    @Test
    void emptyRecipeHasNoPoolsAndNoQuest() {
        WyrldRecipe recipe = WyrldRecipe.empty("test", 2, 5);
        assertEquals(2, recipe.minRooms());
        assertEquals(5, recipe.maxRooms());
        assertTrue(recipe.commonLoot().isEmpty());
        assertTrue(recipe.rareLoot().isEmpty());
        assertTrue(recipe.epicLoot().isEmpty());
        assertTrue(recipe.enemyPool().isEmpty());
        assertTrue(recipe.friendlyPool().isEmpty());
        assertEquals(Optional.empty(), recipe.questId());
    }

    @Test
    void withEnemyAddedAppendsWithoutTouchingOtherPools() {
        WyrldRecipe recipe = WyrldRecipe.empty("test", 1, 1);
        WyrldEntityEntry zombie = new WyrldEntityEntry(Identifier.withDefaultNamespace("zombie"), 10);
        WyrldRecipe updated = recipe.withEnemyAdded(zombie);

        assertEquals(List.of(zombie), updated.enemyPool());
        assertTrue(updated.friendlyPool().isEmpty());
        assertTrue(recipe.enemyPool().isEmpty(), "original recipe must stay unmodified (immutable record)");
    }

    @Test
    void withFriendlyAddedAppendsToFriendlyPoolOnly() {
        WyrldRecipe recipe = WyrldRecipe.empty("test", 1, 1);
        WyrldEntityEntry villager = new WyrldEntityEntry(Identifier.withDefaultNamespace("villager"), 5);
        WyrldRecipe updated = recipe.withFriendlyAdded(villager);

        assertEquals(List.of(villager), updated.friendlyPool());
        assertTrue(updated.enemyPool().isEmpty());
    }

    @Test
    void lootPoolAccessorRoutesToTheRightTier() {
        WyrldRecipe recipe = WyrldRecipe.empty("test", 1, 1);
        WyrldLootEntry common = new WyrldLootEntry(net.minecraft.world.item.ItemStack.EMPTY, 1);
        WyrldLootEntry rare = new WyrldLootEntry(net.minecraft.world.item.ItemStack.EMPTY, 2);
        WyrldLootEntry epic = new WyrldLootEntry(net.minecraft.world.item.ItemStack.EMPTY, 3);
        WyrldRecipe updated = recipe.withLootAdded(LootTier.COMMON, common)
                .withLootAdded(LootTier.RARE, rare)
                .withLootAdded(LootTier.EPIC, epic);

        assertEquals(List.of(common), updated.lootPool(LootTier.COMMON));
        assertEquals(List.of(rare), updated.lootPool(LootTier.RARE));
        assertEquals(List.of(epic), updated.lootPool(LootTier.EPIC));
    }

    @Test
    void withQuestIdSetsAnOptionalStringUnconsumedForNow() {
        WyrldRecipe recipe = WyrldRecipe.empty("test", 1, 1).withQuestId("find-the-troll");
        assertEquals(Optional.of("find-the-troll"), recipe.questId());
    }

    @Test
    void lootTierCodecRoundTripsThroughLowercaseStrings() {
        for (LootTier tier : LootTier.values()) {
            var encoded = LootTier.CODEC.encodeStart(com.mojang.serialization.JavaOps.INSTANCE, tier);
            Object value = encoded.result().orElseThrow();
            assertSame(tier, LootTier.CODEC.parse(com.mojang.serialization.JavaOps.INSTANCE, value).result().orElseThrow());
        }
    }
}
