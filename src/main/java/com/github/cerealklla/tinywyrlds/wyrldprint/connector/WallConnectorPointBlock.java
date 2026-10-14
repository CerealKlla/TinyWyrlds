package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A "Connector Point" for a WyrldPrint wall/doorway attachment -- explicit user spec (2026-10-14):
 * "will look like a normal Oak Door, but will have the purple glow effect." Genuinely extends
 * vanilla's own {@link DoorBlock} (same {@link BlockSetType#OAK} open/close/hinge behavior, same
 * model shape via {@code assets/tinywyrlds/blockstates/*.json} referencing the vanilla
 * {@code oak_door} textures directly -- no new art needed) rather than reimplementing a door from
 * scratch. {@link #size} is baked in at registration (one real block per {@link
 * ConnectorPointSize}), not a blockstate property.
 */
public class WallConnectorPointBlock extends DoorBlock {

    private final ConnectorPointSize size;

    public WallConnectorPointBlock(ConnectorPointSize size, Properties properties) {
        super(BlockSetType.OAK, properties);
        this.size = size;
    }

    public ConnectorPointSize size() {
        return size;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        ConnectorPointFill.fillWall(level, basePos, state.getValue(FACING), size);
    }

    /** Symmetric counterpart of {@link #setPlacedBy}'s fill -- real live report, 2026-10-14:
     *  "when I break a connector it doesn't break the surrounding glass." Vanilla's own door
     *  removal fires this once per half; guarded to {@code LOWER} only so the clear doesn't run
     *  twice. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            ConnectorPointFill.clearWall(level, pos, state.getValue(FACING), size);
        }
    }

    /** The "purple glow effect" -- {@link ParticleTypes#PORTAL} is the same particle a Nether
     *  portal uses, already the right color, no new particle type needed. */
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
