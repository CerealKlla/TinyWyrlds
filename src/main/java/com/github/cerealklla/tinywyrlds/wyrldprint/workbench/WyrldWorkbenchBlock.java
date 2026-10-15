package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import java.util.List;

import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The in-world "right-click to bring up a menu" fixture (design-document.md's own long-deferred
 * spec item) -- a single permanent block placed by {@code WyrldPrintArea#ensureBuilt} on the
 * staging strip, same role for the WyrldPrint system that a real menu block never got for the
 * debug-command-only workflow it replaces. Carries no block-entity state of its own: every action
 * it exposes (Save/Load/Clear/Validate All/Generate One-off/Regenerate) just calls straight into
 * the same lower-level APIs {@code debug.DebugCommands} already uses, reading current state fresh
 * from {@code WyrldPrintStorage} every time rather than caching anything per-position. Visually a
 * crafting-table lookalike (same reused vanilla textures), same v1 precedent Blueprynts'
 * {@code ConstructionSiteBlock} set for "a thing you right-click to get a menu."
 */
public class WyrldWorkbenchBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<WyrldWorkbenchBlock> CODEC = simpleCodec(WyrldWorkbenchBlock::new);

    public WyrldWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
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

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        sendScreen(serverPlayer, pos);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Rebuilds and sends a fresh screen snapshot -- called on open and again after a Save adds a
     *  new entry, so an already-open screen's Load list stays current. */
    public static void sendScreen(ServerPlayer player, BlockPos pos) {
        List<WyrldPrintRecord> saved = WyrldPrintStorage.get().listAll();
        List<String> names = saved.stream().map(WyrldPrintRecord::name).toList();
        List<String> types = saved.stream().map(r -> r.type().name()).toList();
        PacketDistributor.sendToPlayer(player, new OpenWyrldWorkbenchScreenPayload(pos, names, types));
    }
}
