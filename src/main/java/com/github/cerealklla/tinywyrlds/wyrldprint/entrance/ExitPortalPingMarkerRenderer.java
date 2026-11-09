package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Renders {@link ExitPortalPingMarkerEntity} via vanilla's own {@code DisplayRenderer.BlockDisplayRenderer}
 *  -- same trivial-subclass precedent as {@link WyrldEntranceRenderer}. */
public final class ExitPortalPingMarkerRenderer extends DisplayRenderer.BlockDisplayRenderer {

    public ExitPortalPingMarkerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
