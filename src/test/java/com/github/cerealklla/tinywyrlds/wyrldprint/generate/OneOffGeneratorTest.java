package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
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

    /**
     * Confirms {@code buildMergeTree}/{@code totalEdgeWeight} -- the other pure-math piece of this
     * class testable without a live registry bootstrap, added 2026-11-12 for the shape-aware
     * Tunnel layout (explicit user spec: "the entrances in the tunnel should more or less be
     * similar [to the real-world arrangement], not totally random"). An "L": doors A and B close
     * together, C far off on its own -- A and B must merge first (the smaller real-world distance),
     * and that merge must survive as a genuinely separate subtree from C, not get flattened or
     * reordered by the clustering.
     */
    @Test
    void buildMergeTreeMergesTheCloseDoorsFirst() {
        GlobalPos a = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0));
        GlobalPos b = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, 0));
        GlobalPos c = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 100));

        OneOffGenerator.MergeNode root = OneOffGenerator.buildMergeTree(List.of(a, b, c));

        assertTrue(!root.isLeaf(), "the root of a 3-leaf tree must be an internal merge, not a leaf");
        OneOffGenerator.MergeNode abSubtree = root.left().isLeaf() ? root.right() : root.left();
        OneOffGenerator.MergeNode cLeaf = root.left().isLeaf() ? root.left() : root.right();

        assertTrue(cLeaf.isLeaf(), "the far-off door (C) must end up as one of the root's direct children, not buried inside a subtree");
        assertEquals(2, cLeaf.leafEntranceIndex(), "C was index 2 in the input list");

        assertTrue(!abSubtree.isLeaf(), "A and B must have merged into their own internal node before reaching the root");
        Set<Integer> abIndices = new HashSet<>();
        collectLeafIndices(abSubtree, abIndices);
        assertEquals(Set.of(0, 1), abIndices, "the AB subtree must contain exactly A (index 0) and B (index 1), nothing else");

        assertEquals(10.0, abSubtree.mergeDistance(), 0.0001, "A-B real-world distance is exactly 10");
        assertTrue(root.mergeDistance() > abSubtree.mergeDistance(),
                "the root merge (reaching the far door C) must be a larger real-world distance than the close A-B merge");

        double totalWeight = OneOffGenerator.totalEdgeWeight(root);
        double expected = 2 * root.mergeDistance() + 2 * abSubtree.mergeDistance();
        assertEquals(expected, totalWeight, 0.0001,
                "total edge weight is 2x every internal merge's own distance (one weight contribution per child edge), summed over the whole tree");
    }

    private static void collectLeafIndices(OneOffGenerator.MergeNode node, Set<Integer> out) {
        if (node.isLeaf()) {
            out.add(node.leafEntranceIndex());
        } else {
            collectLeafIndices(node.left(), out);
            collectLeafIndices(node.right(), out);
        }
    }
}
