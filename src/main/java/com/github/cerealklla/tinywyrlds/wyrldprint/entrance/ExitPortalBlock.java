package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * A plain, obtainable, player-placed block that looks like a shimmering portal and is the real way
 * out of a Wyrld -- explicit user correction, 2026-10-15: "I didn't tell you to change anything
 * existing to make the portal. I will place the exit where I want it, I just want a block which
 * looks like a shimmering portal, no frame around it." No auto-placement, no frame, no "ensure one
 * exists near spawn" logic -- just a real block, given like any other item in this mod, placed by
 * hand wherever the user wants it. (An earlier version of this class auto-built a gold-framed
 * structure near every spawn point; that's been removed entirely, not just disabled.)
 *
 * <p>Placement itself lives entirely in {@link ExitPortalItem} (one of three size variants --
 * 1x2/3x3/5x5, see {@link PortalSize} -- stamping a flat rectangle of this block via a custom
 * {@code useOn}), not a vanilla {@code BlockItem}; this class has no {@code getStateForPlacement}
 * override because nothing ever places a single cell of it that way.
 *
 * <p>A genuine {@link Portal} implementation, not a hand-rolled proximity check, so it gets
 * vanilla's own stand-and-fade teleport UX (a brief delay, then {@link
 * Portal.Transition#CONFUSION}'s dizzy screen effect) for free, the same real mechanism {@code
 * NetherPortalBlock} itself uses -- confirmed by reading that class directly: {@code
 * Entity#setAsInsidePortal} tracks a {@code PortalProcessor} that calls {@link
 * #getPortalDestination} exactly once, at the moment the stand timer actually completes, then
 * performs the real cross-dimension teleport itself from whatever {@link TeleportTransition} is
 * returned here -- no manual {@code teleportTo}/dimension-change call is needed on this end at all.
 *
 * <p>{@code WyrldReturnItem} (the previous exit mechanic) is deliberately left registered and
 * untouched, just no longer auto-granted on entry -- explicit user instruction: "Don't delete the
 * item you made, we may want that at some point, but not now."
 *
 * <p>Visually reuses vanilla's own animated {@code nether_portal} texture (confirmed by reading
 * its real assets: a completely ordinary baked block model, {@code elements}/{@code faces} JSON,
 * the "swirl" is just the texture's own {@code .mcmeta} frame animation -- no hardcoded special
 * renderer at all) with a gold tint applied via a registered {@code BlockColor}, "a different
 * color if possible, maybe gold" per the explicit request. The real resulting hue (multiply-tinting
 * a magenta/purple source texture gold shifts it warm, but isn't a true clean gold) hasn't been
 * visually confirmed in a live client -- flagged, same category as every other live-client-only gap
 * this session.
 */
public class ExitPortalBlock extends Block implements Portal {

    public static final MapCodec<ExitPortalBlock> CODEC = simpleCodec(ExitPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** A brief "stand in it" delay before the real teleport fires, same spirit as vanilla's own
     *  nether portal stand-timer (there, driven by a GameRule; here, just a short fixed constant --
     *  this mod has no comparable rule to read). */
    private static final int TRANSITION_TIME_TICKS = 20;

    public ExitPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return TRANSITION_TIME_TICKS;
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }
        WyrldManager.LeaveDestination destination = WyrldManager.resolveLeaveDestination(player);
        if (destination == null) {
            return null;
        }
        BlockPos pos = destination.pos();
        return new TeleportTransition(destination.level(), new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Same gold-ish particle language SpawnLocationBlock already established in this mod,
        // rather than vanilla's own (purple) ParticleTypes.PORTAL, for visual consistency.
        if (random.nextInt(4) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                0.0, 0.0, 0.0);
    }

    /** Breaking one placed cell breaks the whole connected "sheet" with it -- a 3x3/5x5 variant is
     *  really one logical portal split across many identical blocks, and leaving the rest behind
     *  after breaking one is just tedious to clean up by hand. Explicit user request: "to speed up
     *  editing." A plain flood fill over face-adjacent cells that are also this block -- doesn't
     *  drop anything for the swept-up neighbors (this block has no loot table at all, same as
     *  vanilla's own nether portal), just clears them quietly. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            breakConnectedNeighbors(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void breakConnectedNeighbors(Level level, BlockPos origin) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.Deque<BlockPos> queue = new java.util.ArrayDeque<>();
        visited.add(origin.immutable());
        queue.add(origin.immutable());
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (!visited.add(neighbor)) {
                    continue;
                }
                if (level.getBlockState(neighbor).is(this)) {
                    queue.add(neighbor);
                }
            }
        }
        visited.remove(origin);
        for (BlockPos neighbor : visited) {
            level.removeBlock(neighbor, false);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> {
                return switch (state.getValue(AXIS)) {
                    case X -> state.setValue(AXIS, Direction.Axis.Z);
                    case Z -> state.setValue(AXIS, Direction.Axis.X);
                    default -> state;
                };
            }
            default -> {
                return state;
            }
        }
    }
}
