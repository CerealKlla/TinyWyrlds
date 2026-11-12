package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A real vanilla {@link LeverBlock} -- same model, same facing/powered/redstone behavior, nothing
 * overridden -- with {@link EntityBlock} added on top purely so it can carry a {@link
 * TaggedInteractiveBlockEntity}. See that class's own doc for the full design ("why a new block at
 * all" -- vanilla's lever has no block entity, and that's the only real Minecraft mechanism for data
 * to live on a specific block). No {@code codec()} override needed -- same precedent as {@code
 * connector.FloorConnectorPointBlock extends TrapDoorBlock} already established in this mod: ordinary
 * mod block registration (a plain {@code DeferredRegister} factory lambda) never actually calls
 * through {@code codec()} at all, so {@code LeverBlock}'s own already-concrete implementation is
 * fine left exactly as inherited.
 */
public class TaggedLeverBlock extends LeverBlock implements EntityBlock {

    public TaggedLeverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TaggedInteractiveBlockEntity(pos, state);
    }
}
