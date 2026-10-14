package com.github.cerealklla.tinywyrlds.registration;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TinyWyrldsMod.MODID);

    public static final DeferredItem<BlockItem> CONNECTOR_POINT_WALL_SMALL_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_POINT_WALL_SMALL);
    public static final DeferredItem<BlockItem> CONNECTOR_POINT_WALL_MEDIUM_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_POINT_WALL_MEDIUM);
    public static final DeferredItem<BlockItem> CONNECTOR_POINT_WALL_LARGE_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_POINT_WALL_LARGE);
    public static final DeferredItem<BlockItem> CONNECTOR_POINT_FLOOR_SMALL_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_POINT_FLOOR_SMALL);
    public static final DeferredItem<BlockItem> CONNECTOR_POINT_FLOOR_MEDIUM_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_POINT_FLOOR_MEDIUM);
    public static final DeferredItem<BlockItem> CONNECTOR_POINT_FLOOR_LARGE_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_POINT_FLOOR_LARGE);
    public static final DeferredItem<BlockItem> SPAWN_LOCATION_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_LOCATION);
}
