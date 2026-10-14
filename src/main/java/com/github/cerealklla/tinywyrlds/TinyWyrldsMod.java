package com.github.cerealklla.tinywyrlds;

import com.github.cerealklla.tinywyrlds.debug.DebugCommands;
import com.github.cerealklla.tinywyrlds.instance.WyrldLifecycleTicker;
import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;
import com.github.cerealklla.tinywyrlds.registration.ModItems;
import com.github.cerealklla.tinywyrlds.space.AdvancementBlocker;
import com.github.cerealklla.tinywyrlds.voxel.VoxelLockListener;
import com.github.cerealklla.tinywyrlds.wyrldprint.CreativeZoneTicker;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintAreaSetupListener;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(TinyWyrldsMod.MODID)
public class TinyWyrldsMod {

    public static final String MODID = "tinywyrlds";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinyWyrldsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreativeTabItems);

        NeoForge.EVENT_BUS.register(new VoxelLockListener());
        NeoForge.EVENT_BUS.register(new WyrldLifecycleTicker());
        NeoForge.EVENT_BUS.register(new WyrldPrintAreaSetupListener());
        NeoForge.EVENT_BUS.register(new CreativeZoneTicker());
        NeoForge.EVENT_BUS.register(new AdvancementBlocker());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("TinyWyrlds common setup");
    }

    /** No mod in this suite had ever registered anything into a creative tab before -- every
     *  custom block/item across all 9 mods has only ever been obtainable via {@code /give} or a
     *  mod's own granting code. The six Connector Point blocks are meant to be hand-placed by an
     *  admin while authoring a WyrldPrint, so real spec: they belong in the browsable creative
     *  inventory, same tab (Functional Blocks) real vanilla doors/trapdoors already live in. */
    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            return;
        }
        event.accept(ModItems.CONNECTOR_POINT_WALL_SMALL_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_WALL_MEDIUM_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_WALL_LARGE_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_FLOOR_SMALL_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_FLOOR_MEDIUM_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_FLOOR_LARGE_ITEM.get());
        event.accept(ModItems.SPAWN_LOCATION_ITEM.get());
    }
}
