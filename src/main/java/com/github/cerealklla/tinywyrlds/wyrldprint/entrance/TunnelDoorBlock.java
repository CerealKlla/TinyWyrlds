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
 * The real-world end of a Tunnel (see {@code generate.OneOffGenerator#generateTunnel}) -- explicit
 * user spec: "for now just make the final entrance/exit look like a door standing on the ground; a
 * player right-clicking it enters." Deliberately NOT a real two-tall vanilla {@code DoorBlock}
 * (hinge/half/open-state machinery, two block positions) -- a single-cell placeholder visual (a
 * thin door-shaped panel, see {@code models/block/tunnel_door.json}, reusing vanilla's own
 * {@code oak_door} texture) standing in for a nicer future look, same "for now" framing the user's
 * own request used. No {@code BlockItem}/placement item exists for this on purpose -- every known
 * use case so far (a settlement's own internal shortcut, a world-gen mountain bypass, a future
 * Mayor-placed stake pair) places a pair of these programmatically via {@code
 * api.TinyWyrlds#createTunnelDoorPair}, never by hand.
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
