package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The real, concrete "Exit Structure" (explicit user spec, 2026-10-15: "Walking into the structure
 * will look and behave like a nether portal but a different color... that is the only way you'll
 * exit the Wyrld") -- a genuine {@link Portal} implementation, not a hand-rolled proximity check,
 * so it gets vanilla's own stand-and-fade teleport UX (a brief delay, then {@link
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

    /** Places a small permanent "Exit Structure" (a 1-wide x 2-tall portal doorway, bordered by a
     *  gold-block frame) a fixed offset from {@code spawnPos} -- called by {@code
     *  WyrldManager#enterAt} every time a player lands in a Wyrld, idempotent (skips if the portal
     *  cell is already this block, so re-entering an already-visited instance never re-places or
     *  disturbs it). */
    public static void ensurePlaced(ServerLevel level, BlockPos spawnPos) {
        BlockPos base = spawnPos.offset(0, 0, -2);
        if (level.getBlockState(base).is(ModBlocks.EXIT_PORTAL.get())) {
            return;
        }
        BlockState portalState = ModBlocks.EXIT_PORTAL.get().defaultBlockState().setValue(AXIS, Direction.Axis.X);
        for (int dy = 0; dy <= 1; dy++) {
            level.setBlock(base.above(dy), portalState, 2);
        }
        for (int dy = -1; dy <= 2; dy++) {
            level.setBlock(base.offset(-1, dy, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            level.setBlock(base.offset(1, dy, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        }
        for (int dx = -1; dx <= 1; dx++) {
            level.setBlock(base.offset(dx, -1, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            level.setBlock(base.offset(dx, 2, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        }
    }
}
