package com.github.cerealklla.tinywyrlds.registration;

import java.util.EnumMap;
import java.util.Map;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.wyrldprint.SpawnLocationBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.FloorConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.WallConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.marker.MarkerBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.marker.MarkerKind;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.WyrldWorkbenchBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The six Connector Point blocks -- see {@code wyrldprint.connector.WallConnectorPointBlock}/
 *  {@code FloorConnectorPointBlock}'s own docs. Properties mirror vanilla's real {@code oak_door}/
 *  {@code oak_trapdoor} registration exactly (same strength/sound/occlusion), since these
 *  genuinely are a {@code DoorBlock}/{@code TrapDoorBlock} subclass, not just a lookalike. */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TinyWyrldsMod.MODID);

    public static final DeferredBlock<WallConnectorPointBlock> CONNECTOR_POINT_WALL_SMALL = registerWall("connector_point_wall_small", ConnectorPointSize.SMALL);
    public static final DeferredBlock<WallConnectorPointBlock> CONNECTOR_POINT_WALL_MEDIUM = registerWall("connector_point_wall_medium", ConnectorPointSize.MEDIUM);
    public static final DeferredBlock<WallConnectorPointBlock> CONNECTOR_POINT_WALL_LARGE = registerWall("connector_point_wall_large", ConnectorPointSize.LARGE);

    public static final DeferredBlock<FloorConnectorPointBlock> CONNECTOR_POINT_FLOOR_SMALL = registerFloor("connector_point_floor_small", ConnectorPointSize.SMALL);
    public static final DeferredBlock<FloorConnectorPointBlock> CONNECTOR_POINT_FLOOR_MEDIUM = registerFloor("connector_point_floor_medium", ConnectorPointSize.MEDIUM);
    public static final DeferredBlock<FloorConnectorPointBlock> CONNECTOR_POINT_FLOOR_LARGE = registerFloor("connector_point_floor_large", ConnectorPointSize.LARGE);

    public static final DeferredBlock<SpawnLocationBlock> SPAWN_LOCATION = BLOCKS.register("spawn_location",
            key -> new SpawnLocationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .setId(ResourceKey.create(Registries.BLOCK, key))));

    public static final DeferredBlock<WyrldWorkbenchBlock> WYRLD_WORKBENCH = BLOCKS.register("wyrld_workbench",
            key -> new WyrldWorkbenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .setId(ResourceKey.create(Registries.BLOCK, key))));

    /** The six "possible content" marker blocks (loot x3 tiers, enemy, friendly, interactive) --
     *  see {@code wyrldprint.marker.MarkerKind}'s own doc. */
    public static final Map<MarkerKind, DeferredBlock<MarkerBlock>> MARKERS = registerMarkers();

    private static Map<MarkerKind, DeferredBlock<MarkerBlock>> registerMarkers() {
        Map<MarkerKind, DeferredBlock<MarkerBlock>> result = new EnumMap<>(MarkerKind.class);
        for (MarkerKind kind : MarkerKind.values()) {
            result.put(kind, BLOCKS.register(kind.id(), key -> new MarkerBlock(kind, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .setId(ResourceKey.create(Registries.BLOCK, key)))));
        }
        return result;
    }

    private static DeferredBlock<WallConnectorPointBlock> registerWall(String id, ConnectorPointSize size) {
        return BLOCKS.register(id, key -> new WallConnectorPointBlock(size, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(3.0F)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .setId(ResourceKey.create(Registries.BLOCK, key))));
    }

    private static DeferredBlock<FloorConnectorPointBlock> registerFloor(String id, ConnectorPointSize size) {
        return BLOCKS.register(id, key -> new FloorConnectorPointBlock(size, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(3.0F)
                .noOcclusion()
                .sound(SoundType.WOOD)
                .setId(ResourceKey.create(Registries.BLOCK, key))));
    }
}
