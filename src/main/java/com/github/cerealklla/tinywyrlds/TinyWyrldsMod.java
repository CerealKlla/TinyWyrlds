package com.github.cerealklla.tinywyrlds;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(TinyWyrldsMod.MODID)
public class TinyWyrldsMod {

    public static final String MODID = "tinywyrlds";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinyWyrldsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("TinyWyrlds common setup");
    }
}
