package com.github.cerealklla.tinywyrlds.voxel;

import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Enforces the one hard rule every Wyrld shares: no solid-block creation or destruction, ever --
 * only non-solid blocks (plants, webs, and similar) may be placed or broken. Scoped entirely to
 * {@link WyrldSpaceDimension#KEY}; has no effect in any other dimension.
 *
 * <p>"Non-solid" is approximated as "no collision shape" ({@link BlockState#getCollisionShape}
 * empty), which covers every example the user gave (crops, flowers, saplings, vines, cobwebs --
 * cobwebs deliberately have no collision box in vanilla, that's exactly what lets an entity walk
 * into one at all) without needing a hand-maintained block list.
 *
 * <p>Known simplification, same shape as Protectyons' own tool-transformation gap: an in-place
 * tool transformation (hoe-till, axe-strip, etc.) isn't specially detected here the way
 * Protectyons' {@code VoxelProtectionListener} does, since Wyrld Space has no natural terrain for
 * that to ever come up against in this slice (a void-flat dimension, nothing generated in it yet)
 * -- revisit if/when real generated terrain inside a Wyrld ever becomes a thing.
 */
public final class VoxelLockListener {

    @SubscribeEvent
    public void onBreakBlock(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || !isWyrldSpace(serverLevel)) {
            return;
        }
        if (!isNonSolid(event.getState(), serverLevel, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || !isWyrldSpace(serverLevel)) {
            return;
        }
        if (!isNonSolid(event.getPlacedBlock(), serverLevel, event.getPos())) {
            event.setCanceled(true);
        }
    }

    private boolean isWyrldSpace(ServerLevel level) {
        return level.dimension().equals(WyrldSpaceDimension.KEY);
    }

    private boolean isNonSolid(BlockState state, ServerLevel level, BlockPos pos) {
        return state.getCollisionShape(level, pos).isEmpty();
    }
}
