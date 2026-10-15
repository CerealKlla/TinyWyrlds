package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.github.cerealklla.tinywyrlds.registration.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The real, obtainable item for {@link ExitPortalBlock} -- one per {@link PortalSize}, explicit
 * user spec 2026-10-15: "I'd like 3 variants, a 1x2, 3x3, and 5x5 flat portal." Right-clicking a
 * block stamps a whole flat rectangular sheet of real {@code ExitPortalBlock} cells in one go
 * (oriented across whichever horizontal axis the placing player faces, same convention {@link
 * ExitPortalBlock#getStateForPlacement} already uses for a single cell), rather than requiring the
 * player to place each cell of a 5x5 sheet by hand -- a plain {@link Item}, not a {@code BlockItem},
 * since placing more than one block per use needs its own {@link #useOn} logic anyway.
 *
 * <p>Refuses to place (no block changed, item not consumed) if any target cell isn't replaceable --
 * a real, reported failure ("Not enough room...") rather than silently overwriting whatever terrain
 * happens to be there.
 */
public class ExitPortalItem extends Item {

    private final PortalSize size;

    public ExitPortalItem(PortalSize size, Properties properties) {
        super(properties);
        this.size = size;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos placeAt = clickedPos.relative(clickedFace);

        Direction.Axis facingAxis = player.getDirection().getAxis();
        Direction.Axis portalAxis = facingAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        BlockState portalState = ModBlocks.EXIT_PORTAL.get().defaultBlockState().setValue(ExitPortalBlock.AXIS, portalAxis);

        int half = size.width() / 2;
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int i = -half; i <= half; i++) {
            for (int dy = 0; dy < size.height(); dy++) {
                BlockPos cell = portalAxis == Direction.Axis.X
                        ? placeAt.offset(i, dy, 0)
                        : placeAt.offset(0, dy, i);
                cells.add(cell);
            }
        }

        for (BlockPos cell : cells) {
            if (!serverLevel.getBlockState(cell).canBeReplaced()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal(
                            "Not enough room here for a " + size.width() + "x" + size.height() + " portal."));
                }
                return InteractionResult.FAIL;
            }
        }

        for (BlockPos cell : cells) {
            serverLevel.setBlock(cell, portalState, 3);
        }
        serverLevel.playSound(null, placeAt, portalState.getSoundType().getPlaceSound(), SoundSource.BLOCKS,
                (portalState.getSoundType().getVolume() + 1.0F) / 2.0F, portalState.getSoundType().getPitch() * 0.8F);

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    public PortalSize size() {
        return size;
    }
}
