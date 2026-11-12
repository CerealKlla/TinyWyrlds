package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
     * Confirms {@code buildMergeTree} -- the other pure-math piece of this class testable without
     * a live registry bootstrap, added 2026-11-12 for the shape-aware Tunnel layout. An "L": doors
     * A and B close together, C far off on its own -- A and B must merge first (the smaller
     * real-world distance), and that merge must survive as a genuinely separate subtree from C,
     * not get flattened or reordered by the clustering. {@code totalEdgeWeight} no longer exists --
     * the shape-aware generator moved from a shared-budget proportional split (which this test used
     * to also confirm) to per-node target-coordinate steering; see {@code OneOffGenerator
     * #tryShapedTunnelPlan}'s own doc for why.
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

        // MergeNode.fork's centroid is the unweighted average of its own two children's centroids
        // (recursive, not a flat average over every leaf) -- confirms that's what root actually
        // carries, since tryShapedTunnelPlan's target-coordinate steering is now built entirely on
        // these centroids. abSubtree's own centroid is (A+B)/2 = (5, 0); root's is
        // (abSubtree + cLeaf) / 2 = ((5,0) + (0,100)) / 2 = (2.5, 50).
        assertEquals(5.0, abSubtree.centroidX(), 0.0001, "AB subtree centroid X is the midpoint of A and B");
        assertEquals(0.0, abSubtree.centroidZ(), 0.0001, "AB subtree centroid Z is the midpoint of A and B");
        assertEquals(2.5, root.centroidX(), 0.0001, "root centroid X is the midpoint of the AB subtree and C, not a flat 3-way average");
        assertEquals(50.0, root.centroidZ(), 0.0001, "root centroid Z is the midpoint of the AB subtree and C, not a flat 3-way average");
    }

    private static void collectLeafIndices(OneOffGenerator.MergeNode node, Set<Integer> out) {
        if (node.isLeaf()) {
            out.add(node.leafEntranceIndex());
        } else {
            collectLeafIndices(node.left(), out);
            collectLeafIndices(node.right(), out);
        }
    }

    /** Cardinal {@code (dx, dz)} unit vectors, same convention this class's own code already uses
     *  (forward=+X=east, lateral=+Z=south) -- a Tunnel fork piece's own doors only ever face one
     *  of these 4 directions (90-degree rotation increments), so this is the real, honest option
     *  set {@link OneOffGenerator#bestDirectionAssignment} actually has to choose from in
     *  production, not a continuous one.
     */
    private static final List<double[]> CARDINAL_DIRECTIONS = List.of(
            new double[]{0, -1}, new double[]{1, 0}, new double[]{0, 1}, new double[]{-1, 0});

    @Test
    void bestDirectionAssignmentPicksTheClosestMatchingDirectionPerChild() {
        // One child wants to go roughly east, the other roughly west -- regardless of which order
        // the 4 cardinal options are listed in, the assignment must route each child through its
        // own genuinely closest direction, not an arbitrary or swapped one.
        double[] wantsEast = {10, 1};
        double[] wantsWest = {-10, 1};
        int[] assignment = OneOffGenerator.bestDirectionAssignment(CARDINAL_DIRECTIONS, wantsEast, wantsWest);
        assertArrayEquals(new double[]{1, 0}, CARDINAL_DIRECTIONS.get(assignment[0]), 0.0001, "the east-wanting child must be assigned the EAST direction");
        assertArrayEquals(new double[]{-1, 0}, CARDINAL_DIRECTIONS.get(assignment[1]), 0.0001, "the west-wanting child must be assigned the WEST direction");
    }

    @Test
    void bestDirectionAssignmentReturnsNoneWithFewerThanTwoOptions() {
        int[] assignment = OneOffGenerator.bestDirectionAssignment(List.of(new double[]{1, 0}), new double[]{1, 0}, new double[]{-1, 0});
        assertArrayEquals(new int[]{-1, -1}, assignment, "fewer than 2 available directions must report none, not pick one twice");
    }

    /**
     * End-to-end check of the real 2026-11-12 fix, run entirely offline -- no Minecraft registry,
     * no server, no deploy -- against the exact 6 real door positions from the live Tunnel this
     * session's bug reports were about. Simulates growth the same way production actually can:
     * at every fork, {@link OneOffGenerator#bestDirectionAssignment} picks one of only 4 cardinal
     * directions per child (a real door's own facing options), then that child's position moves by
     * the PROJECTION of its own target vector onto that one fixed direction -- exactly what
     * {@code growTowardTarget}'s mid-chain loop converges to now that it stops the instant nothing
     * gets closer (it can never move perpendicular to the direction chosen at the fork, since every
     * ordinary piece in this catalog is a straight pass-through). This is deliberately pessimistic
     * about precision (real growth gets at least this close, often closer, since a piece's own
     * footprint can land a few cells past the ideal projection) -- it exists to catch a repeat of
     * the actual regression (entrances landing in essentially the same, wrong, unrelated direction
     * -- see this class's own git history for the "611-piece Tunnel" incident), not to demand
     * perfect geometry a 4-direction grid can't deliver.
     */
    @Test
    void shapedTunnelGrowthRoughlyPreservesRealWorldRelativeDirectionForTheLiveSixDoorGroup() {
        List<GlobalPos> dests = List.of(
                GlobalPos.of(Level.OVERWORLD, new BlockPos(202, 71, 311)),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(230, 65, 284)),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(282, 67, 311)),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(321, 63, 212)),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(321, 63, 261)),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(322, 64, 325)));

        OneOffGenerator.MergeNode tree = OneOffGenerator.buildMergeTree(dests);
        double groupCentroidX = tree.centroidX();
        double groupCentroidZ = tree.centroidZ();
        double maxRadius = OneOffGenerator.maxLeafRadius(tree, groupCentroidX, groupCentroidZ);
        double scale = Math.min(1.0, OneOffGenerator.TARGET_SPREAD_CELLS / maxRadius);

        Map<Integer, double[]> simulated = new HashMap<>();
        simulateGrowth(tree, 0, 0, groupCentroidX, groupCentroidZ, scale, simulated);
        assertEquals(6, simulated.size(), "every one of the 6 real doors must end up with a simulated local position");

        List<double[]> realPositions = dests.stream().map(d -> new double[]{d.pos().getX(), d.pos().getZ()}).toList();

        int pairCount = 0;
        double totalAngleError = 0;
        int withinNinetyDegrees = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) {
                double[] realVector = {realPositions.get(j)[0] - realPositions.get(i)[0], realPositions.get(j)[1] - realPositions.get(i)[1]};
                double[] localVector = {simulated.get(j)[0] - simulated.get(i)[0], simulated.get(j)[1] - simulated.get(i)[1]};
                double angleError = angleBetweenDegrees(realVector, localVector);
                totalAngleError += angleError;
                pairCount++;
                if (angleError <= 90) {
                    withinNinetyDegrees++;
                }
            }
        }
        double averageAngleError = totalAngleError / pairCount;
        System.out.println("shapedTunnelGrowth: average pairwise angle error = " + averageAngleError
                + " degrees, " + withinNinetyDegrees + "/" + pairCount + " pairs within 90 degrees");

        // A 4-direction grid can never reproduce an arbitrary real-world bearing exactly -- the
        // honest bar this test holds the algorithm to is "meaningfully related," not "precise":
        // every pair should at least land in the right HALF of the compass (<=90 degrees off), and
        // the average error across all 15 pairs should be well under the ~90-degree error a
        // random/unrelated layout would average (confirmed separately in the regression this
        // replaces: entrances landed hundreds of cells apart in a fixed cross pattern with no
        // relation to the real doors at all).
        assertEquals(15, pairCount, "6 doors have exactly 15 pairs");
        assertTrue(withinNinetyDegrees == pairCount, "every pair must land in the right half of the compass relative to reality -- got " + withinNinetyDegrees + "/" + pairCount);
        assertTrue(averageAngleError < 60.0, "average pairwise angle error should be well under a random layout's ~90 degrees -- was " + averageAngleError);
    }

    private static void simulateGrowth(OneOffGenerator.MergeNode node, double originForward, double originLateral,
            double groupCentroidX, double groupCentroidZ, double scale, Map<Integer, double[]> out) {
        if (node.isLeaf()) {
            out.put(node.leafEntranceIndex(), new double[]{originForward, originLateral});
            return;
        }
        int[] leftTarget = OneOffGenerator.targetLocalCoord(node.left(), groupCentroidX, groupCentroidZ, scale);
        int[] rightTarget = OneOffGenerator.targetLocalCoord(node.right(), groupCentroidX, groupCentroidZ, scale);
        double[] leftDirection = {leftTarget[0] - originForward, leftTarget[1] - originLateral};
        double[] rightDirection = {rightTarget[0] - originForward, rightTarget[1] - originLateral};
        int[] assignment = OneOffGenerator.bestDirectionAssignment(CARDINAL_DIRECTIONS, leftDirection, rightDirection);
        double[] leftStep = CARDINAL_DIRECTIONS.get(assignment[0]);
        double[] rightStep = CARDINAL_DIRECTIONS.get(assignment[1]);
        double leftProjection = Math.max(0, leftDirection[0] * leftStep[0] + leftDirection[1] * leftStep[1]);
        double rightProjection = Math.max(0, rightDirection[0] * rightStep[0] + rightDirection[1] * rightStep[1]);
        simulateGrowth(node.left(), originForward + leftStep[0] * leftProjection, originLateral + leftStep[1] * leftProjection,
                groupCentroidX, groupCentroidZ, scale, out);
        simulateGrowth(node.right(), originForward + rightStep[0] * rightProjection, originLateral + rightStep[1] * rightProjection,
                groupCentroidX, groupCentroidZ, scale, out);
    }

    /**
     * Same pure offline simulation as the live-door test above, but against 300 random door
     * layouts (3 to 8 doors each, fixed seed for reproducibility) instead of one known-good
     * example -- the real "automate testing this until it gets close" loop: this runs in a couple
     * of seconds under {@code ./gradlew test}, no server or deploy involved, so the algorithm can
     * be iterated on and re-checked against a broad spread of layouts directly, the same way the
     * single real-door test above caught (and now guards against a repeat of) the 611-piece
     * regression. Aggregate bars, not a 100% pass on every single trial -- a handful of adversarial
     * random layouts (near-collinear points, exact ties) landing a bit worse is expected and
     * acceptable; what matters is the broad trend staying far from "unrelated to reality," which is
     * what the regression actually looked like.
     */
    @Test
    void shapedTunnelGrowthRoughlyPreservesRelativeDirectionAcrossManyRandomLayouts() {
        Random random = new Random(42);
        int trialCount = 300;
        int totalPairs = 0;
        int withinNinetyDegrees = 0;
        double totalAngleError = 0;
        for (int trial = 0; trial < trialCount; trial++) {
            int doorCount = 3 + random.nextInt(6); // 3..8
            List<GlobalPos> dests = new ArrayList<>();
            List<double[]> realPositions = new ArrayList<>();
            for (int i = 0; i < doorCount; i++) {
                int x = random.nextInt(601) - 300;
                int z = random.nextInt(601) - 300;
                dests.add(GlobalPos.of(Level.OVERWORLD, new BlockPos(x, 64, z)));
                realPositions.add(new double[]{x, z});
            }

            OneOffGenerator.MergeNode tree = OneOffGenerator.buildMergeTree(dests);
            double groupCentroidX = tree.centroidX();
            double groupCentroidZ = tree.centroidZ();
            double maxRadius = OneOffGenerator.maxLeafRadius(tree, groupCentroidX, groupCentroidZ);
            double scale = maxRadius <= 0 ? 1.0 : Math.min(1.0, OneOffGenerator.TARGET_SPREAD_CELLS / maxRadius);

            Map<Integer, double[]> simulated = new HashMap<>();
            simulateGrowth(tree, 0, 0, groupCentroidX, groupCentroidZ, scale, simulated);

            for (int i = 0; i < doorCount; i++) {
                for (int j = i + 1; j < doorCount; j++) {
                    double[] realVector = {realPositions.get(j)[0] - realPositions.get(i)[0], realPositions.get(j)[1] - realPositions.get(i)[1]};
                    double[] localVector = {simulated.get(j)[0] - simulated.get(i)[0], simulated.get(j)[1] - simulated.get(i)[1]};
                    double angleError = angleBetweenDegrees(realVector, localVector);
                    totalAngleError += angleError;
                    totalPairs++;
                    if (angleError <= 90) {
                        withinNinetyDegrees++;
                    }
                }
            }
        }
        double averageAngleError = totalAngleError / totalPairs;
        double withinNinetyFraction = (double) withinNinetyDegrees / totalPairs;
        System.out.println("shapedTunnelGrowth (random, " + trialCount + " layouts, " + totalPairs + " pairs): "
                + "average angle error = " + averageAngleError + " degrees, " + (withinNinetyFraction * 100) + "% within 90 degrees");

        // A random/unrelated layout (the actual shape of the regression this guards against) would
        // average close to 90 degrees -- these bars confirm the fix is broadly, not just luckily
        // (on the one real example), related to reality.
        assertTrue(withinNinetyFraction >= 0.9, "at least 90% of pairs across all random layouts should land within 90 degrees of reality -- was " + (withinNinetyFraction * 100) + "%");
        assertTrue(averageAngleError < 50.0, "average angle error across all random layouts should be well under a random/unrelated 90 degrees -- was " + averageAngleError);
    }

    private static double angleBetweenDegrees(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1];
        double magA = Math.sqrt(a[0] * a[0] + a[1] * a[1]);
        double magB = Math.sqrt(b[0] * b[0] + b[1] * b[1]);
        if (magA <= 0.0001 || magB <= 0.0001) {
            return 0; // a degenerate (near-zero) vector has no meaningful angle -- treat as a non-issue rather than NaN.
        }
        double cos = Math.max(-1.0, Math.min(1.0, dot / (magA * magB)));
        return Math.toDegrees(Math.acos(cos));
    }
}
