package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A "Connector Point" for a WyrldPrint floor/ceiling attachment -- explicit user spec
 * (2026-10-14): "create a 1x1, 3x3, 5x5 ceiling/floor 'Connector Points' which look like an oak
 * trap door... will have the purple glow effect." Genuinely extends vanilla's own {@link
 * TrapDoorBlock} (same open/close behavior, model shape via {@code
 * assets/tinywyrlds/blockstates/*.json} referencing the vanilla {@code oak_trapdoor} texture
 * directly -- no new art needed).
 */
public class FloorConnectorPointBlock extends TrapDoorBlock {

    private final ConnectorPointSize size;

    public FloorConnectorPointBlock(ConnectorPointSize size, Properties properties) {
        super(BlockSetType.OAK, properties);
        this.size = size;
    }

    public ConnectorPointSize size() {
        return size;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConnectorPointFill.fillFloor(level, pos, size);
    }

    /** The "purple glow effect" -- see {@code WallConnectorPointBlock}'s own doc. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.PORTAL,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                0.0, 0.0, 0.0);
    }
}
