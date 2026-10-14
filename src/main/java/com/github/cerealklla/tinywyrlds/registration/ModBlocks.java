package com.github.cerealklla.tinywyrlds.registration;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.wyrldprint.SpawnLocationBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.FloorConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.WallConnectorPointBlock;

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
