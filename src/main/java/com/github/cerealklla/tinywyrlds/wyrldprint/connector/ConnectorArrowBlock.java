package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces {@code WallConnectorPointBlock} (a plain vanilla {@code DoorBlock} reskin) -- explicit
 * user correction, 2026-11-11, after a real generation-breaking bug traced to a door's {@code
 * facing} giving a builder no actual visual cue about which way a connector point's "outward"
 * direction (away from the piece's own body, toward where the next piece will attach) is pointing.
 * A door looks the same regardless of {@code facing} unless you already know vanilla's own
 * placement convention -- this doesn't: it's a thin (snow-layer-depth) floor decal, not a
 * wall-mounted plane -- real correction after a first attempt mounted it vertically on the wall,
 * which (confirmed live, by screenshot) read as a tall ambiguous repeated pattern instead of one
 * clear marker. The real shape is a single flat slice resting on the ground (identical profile to
 * vanilla's own snow layer), with the arrow visible looking down at it from above, pointing in
 * {@code FACING}'s horizontal direction. Genuinely has no collision at all (explicit spec: "the
 * arrow itself should have no collision") -- a player just walks straight through/over it.
 *
 * <p>Single-cell (no {@code HALF} the old door-based block needed) -- {@link #size} is still baked
 * in at registration, not a blockstate property, same precedent as the block it replaces.
 * {@link #getStateForPlacement} points the arrow at whoever placed it (explicit spec: "pointing
 * toward the player's face when placed"), and a plain right-click ({@link #useWithoutItem}) cycles
 * it clockwise so a builder can actually choose which way it ends up facing afterward.
 */
public class ConnectorArrowBlock extends HorizontalDirectionalBlock {

    private final ConnectorPointSize size;
    private final MapCodec<ConnectorArrowBlock> codec;

    public ConnectorArrowBlock(ConnectorPointSize size, Properties properties) {
        super(properties);
        this.size = size;
        this.codec = simpleCodec(props -> new ConnectorArrowBlock(size, props));
        registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    public ConnectorPointSize size() {
        return size;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()));
        }
        return InteractionResult.SUCCESS;
    }

    /** Snow-layer-thin, resting flat on the ground -- same profile as vanilla's own snow layer
     *  ({@code Block.box(0,0,0,16,2,16)}), regardless of {@code FACING} (rotation only affects the
     *  arrow's own horizontal direction, not this shape). Used for the selection outline only --
     *  {@link #getCollisionShape} overrides collision separately to stay fully walkable. */
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 2, 16);

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Explicit spec: "the arrow itself should have no collision" -- a player walks straight
     *  through it, same as the 2-block-tall doorway opening it sits at the base of. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
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
     *  replaces. */
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
