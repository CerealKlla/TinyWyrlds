package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.lang.reflect.Field;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/** Sets a freshly-created {@link Display.BlockDisplay}'s block state -- direct port of Blueprynts'
 *  {@code construction.GhostBlockDisplays} (no code-sharing dependency between the two mods; same
 *  small helper, copied -- see that class's own doc for why this goes through the synced-data field
 *  via reflection rather than a public setter, which {@code Display.BlockDisplay} doesn't expose). */
public final class BlockDisplayHelper {

    private static volatile EntityDataAccessor<BlockState> blockStateAccessor;

    private BlockDisplayHelper() {
    }

    public static void setBlockState(Display.BlockDisplay entity, BlockState state) {
        entity.getEntityData().set(blockStateAccessor(), state);
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<BlockState> blockStateAccessor() {
        EntityDataAccessor<BlockState> accessor = blockStateAccessor;
        if (accessor == null) {
            synchronized (BlockDisplayHelper.class) {
                accessor = blockStateAccessor;
                if (accessor == null) {
                    try {
                        Field field = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
                        field.setAccessible(true);
                        accessor = (EntityDataAccessor<BlockState>) field.get(null);
                        blockStateAccessor = accessor;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not resolve Display.BlockDisplay's block state accessor", e);
                    }
                }
            }
        }
        return accessor;
    }
}
