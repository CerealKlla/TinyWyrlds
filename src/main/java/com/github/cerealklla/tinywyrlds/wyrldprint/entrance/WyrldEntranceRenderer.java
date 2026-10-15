package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Renders {@link WyrldEntranceEntity} via vanilla's own {@code DisplayRenderer.BlockDisplayRenderer}
 *  -- that class's constructor is protected, so this is a trivial subclass that just exposes it.
 *  Same precedent as Blueprynts' {@code construction.GhostConstructionWallRenderer}. */
public final class WyrldEntranceRenderer extends DisplayRenderer.BlockDisplayRenderer {

    public WyrldEntranceRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
