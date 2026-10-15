package com.github.cerealklla.tinywyrlds.registration;

import java.util.EnumMap;
import java.util.Map;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldReturnItem;
import com.github.cerealklla.tinywyrlds.wyrldprint.marker.MarkerBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.marker.MarkerKind;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredBlock;
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
    public static final DeferredItem<BlockItem> WYRLD_WORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.WYRLD_WORKBENCH);

    public static final DeferredItem<WyrldReturnItem> WYRLD_RETURN_ITEM = ITEMS.register("wyrld_return_item",
            key -> new WyrldReturnItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, key))));

    public static final Map<MarkerKind, DeferredItem<BlockItem>> MARKER_ITEMS = registerMarkerItems();

    private static Map<MarkerKind, DeferredItem<BlockItem>> registerMarkerItems() {
        Map<MarkerKind, DeferredItem<BlockItem>> result = new EnumMap<>(MarkerKind.class);
        for (Map.Entry<MarkerKind, DeferredBlock<MarkerBlock>> entry : ModBlocks.MARKERS.entrySet()) {
            result.put(entry.getKey(), ITEMS.registerSimpleBlockItem(entry.getValue()));
        }
        return result;
    }
}
