package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The real-world end of a Tunnel (see {@code generate.OneOffGenerator#generateTunnel}) -- originally
 * "a door standing on the ground," now a flat trapdoor/hatch lying on the ground instead, explicit
 * user request 2026-11-11 ("Can we change the entrances from doors to trapdoors?") -- the player
 * walks up to it and right-clicks it to enter. Class/registry name kept as-is (never renamed --
 * already-placed real Tunnel pairs on Production reference this block by registry id) despite no
 * longer being door-shaped; see {@code models/block/tunnel_door.json} for the real flat geometry,
 * reusing vanilla's own {@code oak_trapdoor} texture. Deliberately NOT a real vanilla {@code
 * TrapDoorBlock} (open/close animation, HALF/OPEN state machinery) -- same single-cell placeholder
 * shape approach as before, just reshaped flat instead of standing. No {@code BlockItem}/placement
 * item exists for this on purpose -- every known use case so far (a settlement's own internal
 * shortcut, a world-gen mountain bypass, a Mayor-placed stake group) places a group of these
 * programmatically via {@code api.TinyWyrlds#createTunnelDoorGroup}, never by hand.
 */
public class TunnelDoorBlock extends Block implements EntityBlock {

    public static final MapCodec<TunnelDoorBlock> CODEC = simpleCodec(TunnelDoorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TunnelDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TunnelDoorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TunnelDoorBlockEntity doorEntity) {
            doorEntity.enter(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
