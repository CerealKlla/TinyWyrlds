package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Checks, purely in memory against a {@link WyrldPrintRecord}'s own captured cells (no live world
 * needed), whether every one of its connector points is actually reachable from every other one by
 * walking/climbing through the piece -- added 2026-10-14 after real, hand-authored pieces turned
 * out to be genuinely impassable despite looking fine on paper (a non-functional hidden-piston
 * mechanism; a climb that led into a solid wall; a decorative lantern sitting at head height in an
 * otherwise-clear hallway). Manually re-deriving 3D geometry by eye already got some of these wrong
 * once each; this gives an objective, repeatable answer for all 45 (and any future) pieces instead.
 *
 * <p>Floods outward from the first connector point through every 6-directionally-adjacent position
 * whose own ("feet") cell AND the cell directly above it ("head") are both passable (air, or a
 * door/trapdoor/ladder -- all openable/climbable regardless of captured open/closed state, since a
 * player can interact with them), and reports which of the other points, if any, were never
 * reached. The two-tall requirement is real, not optional: a single-layer ("feet only") version
 * missed the lantern bug entirely, since the floor-level cell underneath it was still open even
 * though the lantern at head height made the column genuinely unwalkable.
 */
public final class WyrldPrintValidator {

    private WyrldPrintValidator() {
    }

    public static Result checkConnectivity(WyrldPrintRecord record) {
        if (record.points().size() < 2) {
            return new Result(true, "only " + record.points().size() + " connector point(s), nothing to check");
        }

        Map<Pos, Boolean> passableCells = new HashMap<>();
        for (WyrldPrintCell cell : record.cells()) {
            passableCells.put(new Pos(cell.relForward(), cell.relLateral(), cell.relY()), isPassable(cell.state()));
        }

        Pos start = new Pos(record.points().get(0).relForward(), record.points().get(0).relLateral(), record.points().get(0).relY());
        Set<Pos> visited = new HashSet<>();
        Deque<Pos> queue = new ArrayDeque<>();
        if (hasClearance(passableCells, start)) {
            visited.add(start);
            queue.add(start);
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            Pos current = queue.poll();
            for (int[] step : steps) {
                Pos next = new Pos(current.forward() + step[0], current.lateral() + step[1], current.y() + step[2]);
                if (!visited.contains(next) && hasClearance(passableCells, next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        List<Integer> unreachable = new ArrayList<>();
        for (int i = 1; i < record.points().size(); i++) {
            WyrldPrintPoint point = record.points().get(i);
            Pos target = new Pos(point.relForward(), point.relLateral(), point.relY());
            if (!visited.contains(target)) {
                unreachable.add(i);
            }
        }
        if (unreachable.isEmpty()) {
            return new Result(true, "all " + record.points().size() + " points connected (" + visited.size() + " reachable cells)");
        }
        return new Result(false, "point(s) " + unreachable + " unreachable from point 0 (" + visited.size() + " cells reachable)");
    }

    /** Both {@code pos} (feet) and the cell directly above it (head) must be passable -- a
     *  position whose "head" cell falls outside the record's own captured cells entirely (treated
     *  as unknown, not open space) is conservatively rejected too. */
    private static boolean hasClearance(Map<Pos, Boolean> passableCells, Pos pos) {
        Pos head = new Pos(pos.forward(), pos.lateral(), pos.y() + 1);
        return Boolean.TRUE.equals(passableCells.get(pos)) && Boolean.TRUE.equals(passableCells.get(head));
    }

    /** A door/trapdoor is always treated as passable regardless of captured open/closed state --
     *  a player can interact with it. A ladder is a real, deliberate exception: climbing is
     *  special-cased player movement, not an empty hitbox -- {@code LadderBlock} actually has a
     *  real (thin) collision shape, confirmed by reading its source, so it would otherwise wrongly
     *  fail this check. Everything else uses the block's own real collision shape (the same
     *  "no collision shape = non-solid" check {@code voxel.VoxelLockListener} already relies on),
     *  not a hand-maintained list -- an earlier version of this method only recognized doors/
     *  trapdoors/ladders by name and wrongly flagged glow lichen, moss carpet, and water (all
     *  genuinely walkable, just not air) as blocking, producing false failures on every "Cave
     *  Interior" room. */
    private static boolean isPassable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof LadderBlock) {
            return true;
        }
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    public record Result(boolean connected, String message) {
    }

    private record Pos(int forward, int lateral, int y) {
    }
}
