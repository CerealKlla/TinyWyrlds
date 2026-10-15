package com.github.cerealklla.tinywyrlds;

import com.github.cerealklla.tinywyrlds.registration.ModEntities;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceRenderer;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.ClientWyrldPrintRequests;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.WyrldWorkbenchScreen;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe --
// same precedent as every other mod in this suite's own *ModClient (see Blueprynts' CLAUDE.md).
@Mod(value = TinyWyrldsMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TinyWyrldsMod.MODID, value = Dist.CLIENT)
public class TinyWyrldsModClient {

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WYRLD_ENTRANCE.get(), WyrldEntranceRenderer::new);
    }

    /** Polls the zero-server-refs bridge for a pending Workbench screen-open request every client
     *  tick -- always replaces the screen, since a Save resends this payload specifically to
     *  refresh an already-open screen's Load list with fresh authoritative state. */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientWyrldPrintRequests.takePendingWorkbenchScreen()
                .ifPresent(request -> Minecraft.getInstance().setScreen(new WyrldWorkbenchScreen(request)));
    }
}
