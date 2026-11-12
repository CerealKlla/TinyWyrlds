package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import com.github.cerealklla.nuclyus.block.DirectionalMarkerBlock;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * TinyWyrlds' own wall Connector Point, now a thin subclass of Nuclyus's generic
 * {@link DirectionalMarkerBlock} -- explicit user direction, 2026-11-11, after asking for a
 * similar-but-wall-capable generic marker ("the X block"): make this the one real shared
 * implementation, with TinyWyrlds switching over rather than keeping a separate copy. Originally a
 * plain vanilla {@code DoorBlock} reskin ({@code WallConnectorPointBlock}), replaced outright after
 * a real generation-breaking bug traced to a door's {@code facing} giving no visual cue which way
 * it pointed -- see {@code decisions.md} for the full incident.
 *
 * <p>Deliberately pinned to {@link AttachFace#FLOOR} only ({@link #getStateForPlacement} ignores
 * which face was actually clicked) -- a Connector Point marks the base of a vertical doorway
 * opening, and {@link ConnectorPointFill}'s own footprint math assumes exactly that floor-level
 * placement; wall/ceiling mounting is a capability of the shared base class, not something this
 * subclass currently uses. {@link #size} is still baked in at registration, not a blockstate
 * property, same precedent as the block this replaced.
 */
public class ConnectorArrowBlock extends DirectionalMarkerBlock {

    private final ConnectorPointSize size;
    private final MapCodec<ConnectorArrowBlock> codec;

    public ConnectorArrowBlock(ConnectorPointSize size, Properties properties) {
        super(properties);
        this.size = size;
        this.codec = simpleCodec(props -> new ConnectorArrowBlock(size, props));
    }

    public ConnectorPointSize size() {
        return size;
    }

    @Override
    protected MapCodec<? extends com.github.cerealklla.nuclyus.block.DirectionalMarkerBlock> codec() {
        return codec;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConnectorPointFill.fillWall(level, pos, state.getValue(FACING), size);
    }

    /** Symmetric counterpart of {@link #setPlacedBy}'s fill. No {@code HALF} here (single cell),
     *  so no double-fire guard is needed the way the old door-based block required. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ConnectorPointFill.clearWall(level, pos, state.getValue(FACING), size);
    }

    /** The established "purple glow" Connector Point particle -- unchanged from the block this
     *  replaced. */
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
