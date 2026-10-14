package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Rotation;

/**
 * Confirms {@code rotateForwardLateral}'s (forward, lateral) convention matches vanilla's own
 * {@link Rotation#rotate} semantics for a real {@code Direction} (forward=EAST=(+1,0), lateral
 * axis=+Z) -- the one piece of this class testable without a live registry bootstrap (everything
 * else needs real {@code BlockState}/{@code ServerLevel}).
 */
class OneOffGeneratorTest {

    @Test
    void clockwise90MatchesEastToSouth() {
        // EAST (forward=1,lateral=0) rotated one step clockwise should land where SOUTH would be
        // (lateral=1,forward=0), matching Direction.EAST.getClockWise() == SOUTH.
        assertArrayEquals(new int[]{0, 1}, OneOffGenerator.rotateForwardLateral(1, 0, Rotation.CLOCKWISE_90));
    }

    @Test
    void clockwise180IsEastToWest() {
        assertArrayEquals(new int[]{-1, 0}, OneOffGenerator.rotateForwardLateral(1, 0, Rotation.CLOCKWISE_180));
    }

    @Test
    void counterClockwise90MatchesEastToNorth() {
        assertArrayEquals(new int[]{0, -1}, OneOffGenerator.rotateForwardLateral(1, 0, Rotation.COUNTERCLOCKWISE_90));
    }

    @Test
    void noneIsIdentity() {
        assertArrayEquals(new int[]{3, -2}, OneOffGenerator.rotateForwardLateral(3, -2, Rotation.NONE));
    }

    @Test
    void fourQuarterTurnsReturnToStart() {
        int forward = 5, lateral = -3;
        int[] result = {forward, lateral};
        for (int i = 0; i < 4; i++) {
            result = OneOffGenerator.rotateForwardLateral(result[0], result[1], Rotation.CLOCKWISE_90);
        }
        assertArrayEquals(new int[]{forward, lateral}, result);
    }
}
