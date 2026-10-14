package com.github.cerealklla.tinywyrlds.wyrldprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marks exactly where a player should land inside an {@code ENTRANCE}-type WyrldPrint -- explicit
 * user spec (2026-10-14): "a 'spawn location' block that I can place for where the players will
 * actually arrive." A pure build-time marker, same precedent as the Connector Points' own glass
 * fill: {@code WyrldPrintCapture} records its position into {@link WyrldPrintSpawn} and then
 * captures its own cell as air (never stamped as a real block a player would see/stand on top of
 * in the generated dungeon -- {@code OneOffGenerator}'s stamp already places real floor beneath
 * where this marker sat).
 */
public class SpawnLocationBlock extends Block {

    public SpawnLocationBlock(Properties properties) {
        super(properties);
    }

    /** A distinct gold-colored glow (vs. Connector Points' purple) so the two marker kinds are
     *  easy to tell apart at a glance while authoring. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                0.0, 0.0, 0.0);
    }
}
