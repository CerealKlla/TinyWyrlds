package com.github.cerealklla.tinywyrlds;

import com.github.cerealklla.tinywyrlds.debug.DebugCommands;
import com.github.cerealklla.tinywyrlds.instance.WyrldLifecycleTicker;
import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.voxel.VoxelLockListener;
import com.github.cerealklla.tinywyrlds.wyrldprint.CreativeZoneTicker;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintAreaSetupListener;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(TinyWyrldsMod.MODID)
public class TinyWyrldsMod {

    public static final String MODID = "tinywyrlds";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinyWyrldsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(new VoxelLockListener());
        NeoForge.EVENT_BUS.register(new WyrldLifecycleTicker());
        NeoForge.EVENT_BUS.register(new WyrldPrintAreaSetupListener());
        NeoForge.EVENT_BUS.register(new CreativeZoneTicker());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("TinyWyrlds common setup");
    }
}
