package com.github.cerealklla.tinywyrlds.registration;

import java.util.EnumMap;
import java.util.Map;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.wyrldprint.SpawnLocationBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorArrowBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.ConnectorPointSize;
import com.github.cerealklla.tinywyrlds.wyrldprint.connector.FloorConnectorPointBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.ExitPortalBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.TunnelDoorBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.interactive.TaggedButtonBlock;
import com.github.cerealklla.tinywyrlds.wyrldprint.interactive.TaggedLeverBlock;
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

/** The six Connector Point blocks -- see {@code wyrldprint.connector.ConnectorArrowBlock}/
 *  {@code FloorConnectorPointBlock}'s own docs. Floor points genuinely are a {@code TrapDoorBlock}
 *  subclass (no facing ambiguity there -- straight up); wall points used to be a {@code DoorBlock}
 *  subclass too, but that was replaced 2026-11-11 with a plain thin arrow block after a real
 *  generation bug traced to a door's facing giving no visual cue which way was "outward". */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TinyWyrldsMod.MODID);

    public static final DeferredBlock<ConnectorArrowBlock> CONNECTOR_POINT_WALL_SMALL = registerWall("connector_point_wall_small", ConnectorPointSize.SMALL);
    public static final DeferredBlock<ConnectorArrowBlock> CONNECTOR_POINT_WALL_MEDIUM = registerWall("connector_point_wall_medium", ConnectorPointSize.MEDIUM);
    public static final DeferredBlock<ConnectorArrowBlock> CONNECTOR_POINT_WALL_LARGE = registerWall("connector_point_wall_large", ConnectorPointSize.LARGE);

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

    /** The real "Exit Structure" -- see {@code wyrldprint.entrance.ExitPortalBlock}'s own doc.
     *  Properties mirror vanilla's own {@code Blocks.NETHER_PORTAL} registration (unbreakable,
     *  no collision, random-tick-free here since this has no piglin-spawning equivalent). */
    public static final DeferredBlock<ExitPortalBlock> EXIT_PORTAL = BLOCKS.register("exit_portal",
            key -> new ExitPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .noCollision()
                    .noOcclusion()
                    .lightLevel(state -> 11)
                    .strength(-1.0F)
                    .setId(ResourceKey.create(Registries.BLOCK, key))));

    /** See {@code wyrldprint.entrance.TunnelDoorBlock}'s own doc -- a placeholder door-shaped
     *  single-cell visual, not a real two-tall vanilla door. */
    public static final DeferredBlock<TunnelDoorBlock> TUNNEL_DOOR = BLOCKS.register("tunnel_door",
            key -> new TunnelDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, key))));

    /** A real vanilla lever/stone button, just with a {@link TaggedInteractiveBlockEntity} attached
     *  -- see that class's own doc. Same push/strength/sound as vanilla's own lever/stone button. */
    public static final DeferredBlock<TaggedLeverBlock> TAGGED_LEVER = BLOCKS.register("tagged_lever",
            key -> new TaggedLeverBlock(BlockBehaviour.Properties.of()
                    .noCollision()
                    .strength(0.5F)
                    .pushReaction(PushReaction.DESTROY)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, key))));

    public static final DeferredBlock<TaggedButtonBlock> TAGGED_BUTTON = BLOCKS.register("tagged_button",
            key -> new TaggedButtonBlock(BlockBehaviour.Properties.of()
                    .noCollision()
                    .strength(0.5F)
                    .pushReaction(PushReaction.DESTROY)
                    .sound(SoundType.STONE)
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

    private static DeferredBlock<ConnectorArrowBlock> registerWall(String id, ConnectorPointSize size) {
        return BLOCKS.register(id, key -> new ConnectorArrowBlock(size, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(3.0F)
                .noOcclusion()
                .instabreak()
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
