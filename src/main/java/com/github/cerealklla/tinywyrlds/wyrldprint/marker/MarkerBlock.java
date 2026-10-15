package com.github.cerealklla.tinywyrlds.wyrldprint.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A "possible content" marker block, one real registered block per {@link MarkerKind} -- see that
 *  enum's own doc for why it needs no special capture/placement code of its own. */
public class MarkerBlock extends Block {

    private final MarkerKind kind;

    public MarkerBlock(MarkerKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public MarkerKind kind() {
        return kind;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) != 0) {
            return;
        }
        level.addParticle(kind.particle(),
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                0.0, 0.0, 0.0);
    }
}
