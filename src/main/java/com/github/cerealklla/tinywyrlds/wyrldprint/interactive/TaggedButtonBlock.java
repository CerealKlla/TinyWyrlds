package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A real vanilla {@link ButtonBlock} (stone-type timing/sound, same as {@code
 * Blocks.STONE_BUTTON}) -- same model, same press behavior -- with {@link EntityBlock} added on
 * top purely so it can carry a {@link TaggedInteractiveBlockEntity}. See that class's own doc for
 * the full design, and {@code TaggedLeverBlock}'s own doc for why no {@code codec()} override is
 * needed here either.
 */
public class TaggedButtonBlock extends ButtonBlock implements EntityBlock {

    public TaggedButtonBlock(Properties properties) {
        super(BlockSetType.STONE, 20, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TaggedInteractiveBlockEntity(pos, state);
    }
}
