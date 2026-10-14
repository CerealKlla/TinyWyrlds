package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.util.Set;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.instance.PlayerWyrldData;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.space.FreeRect;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The one permanent, fixed-location authoring zone for building dungeon components ("WyrldPrints")
 * -- centered on Wyrld Space's own origin, 150 (X) x 100 (Z) blocks total, explicit user spec
 * (2026-10-14): a 100x100 free-build "crafting" area, a 50x100 strip of flat cobblestone next to
 * it, and a single ground-level obsidian block per Z marking the boundary between them (one
 * block, same shape as the cobblestone floor itself -- corrected same day from an initial,
 * mistaken 12-tall wall; counted as part of the 50-wide strip, so the two areas still sum to
 * exactly 150). Carved permanently out of {@code
 * space.WyrldSpaceAllocator}'s universe (see {@link #RESERVED_BOUNDS}) so no real Wyrld instance
 * is ever allocated on top of it.
 *
 * <p>No WyrldPrint capture/save/load logic lives here yet -- the marker-block mechanic for
 * connector/loot/mob/interactive points is explicitly deferred (user, 2026-10-14: "that was a
 * separate question, I'll figure that out later"). This class is purely the physical space + the
 * two behavioral rules that apply inside it ({@code CreativeZoneTicker}'s gamemode swap, {@code
 * voxel.VoxelLockListener}'s exemption).
 */
public final class WyrldPrintArea {

    private WyrldPrintArea() {
    }

    /** Full reserved footprint: 150 (X) x 100 (Z), centered on Wyrld Space's origin. */
    public static final int MIN_X = -75;
    public static final int MAX_X = 75; // exclusive
    public static final int MIN_Z = -50;
    public static final int MAX_Z = 50; // exclusive

    /** Free-build crafting zone -- exactly 100x100, no floor (void -- players fly here). */
    public static final int CRAFT_MIN_X = MIN_X;
    public static final int CRAFT_MAX_X = MIN_X + 100; // exclusive, = 25

    /** The single obsidian column marking the boundary -- counted as part of the 50-wide strip. */
    public static final int BOUNDARY_X = CRAFT_MAX_X; // = 25

    /** Flat cobblestone strip -- 49 wide (the remaining 50 - 1 obsidian column). */
    public static final int COBBLESTONE_MIN_X = BOUNDARY_X + 1; // = 26
    public static final int COBBLESTONE_MAX_X = MAX_X; // exclusive, = 75

    private static final int FLOOR_Y = WyrldManager.BASELINE_Y - 1;
    // A first build (2026-10-13) mistakenly stood the boundary marker up as a 12-tall wall --
    // corrected 2026-10-14 ("the obsidian was meant to be just 1 block on the ground") to a
    // single ground-level block per Z, matching the cobblestone floor's own shape. This upper
    // bound is kept only so ensureBuilt can self-heal a dev save that already has the old wall.
    private static final int OLD_WALL_CLEANUP_TOP_Y = WyrldManager.BASELINE_Y + 10;

    /** Extra breathing room beyond the physical structure before the allocator's universe hole starts. */
    private static final int ALLOCATOR_HOLE_MARGIN = 16;

    public static final FreeRect RESERVED_BOUNDS = new FreeRect(
            MIN_X - ALLOCATOR_HOLE_MARGIN, MIN_Z - ALLOCATOR_HOLE_MARGIN,
            MAX_X + ALLOCATOR_HOLE_MARGIN, MAX_Z + ALLOCATOR_HOLE_MARGIN);

    /** True for any (x, z) inside the full 150x100 footprint, at any Y -- players can fly, so this
     *  is deliberately a 2D check, not bounded in height. */
    public static boolean contains(int x, int z) {
        return x >= MIN_X && x < MAX_X && z >= MIN_Z && z < MAX_Z;
    }

    public static boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    /** Idempotent for the (expensive) cobblestone floor -- skips re-placing it once a sentinel
     *  block confirms it's already there. The single-block obsidian boundary is cheap enough to
     *  always re-enforce unconditionally, which doubles as a self-heal for a dev save that
     *  already has the old, mistakenly-tall wall (see {@link #OLD_WALL_CLEANUP_TOP_Y}). */
    public static void ensureBuilt(ServerLevel level) {
        boolean floorAlreadyBuilt = level.getBlockState(new BlockPos(COBBLESTONE_MIN_X, FLOOR_Y, MIN_Z)).is(Blocks.COBBLESTONE);
        if (!floorAlreadyBuilt) {
            for (int x = COBBLESTONE_MIN_X; x < COBBLESTONE_MAX_X; x++) {
                for (int z = MIN_Z; z < MAX_Z; z++) {
                    level.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
            }
        }

        for (int z = MIN_Z; z < MAX_Z; z++) {
            level.setBlock(new BlockPos(BOUNDARY_X, FLOOR_Y, z), Blocks.OBSIDIAN.defaultBlockState(), 2);
            for (int y = FLOOR_Y + 1; y <= OLD_WALL_CLEANUP_TOP_Y; y++) {
                BlockPos pos = new BlockPos(BOUNDARY_X, y, z);
                if (level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        if (!floorAlreadyBuilt) {
            TinyWyrldsMod.LOGGER.info("Built the WyrldPrint crafting area in Wyrld Space.");
        }
    }

    /** Teleports {@code player} directly to the cobblestone staging area (solid footing, next to
     *  the void crafting zone), recording where they came from so {@code WyrldManager#leave}
     *  sends them back. Not tied to any real Wyrld instance -- see {@link PlayerWyrldData}'s own
     *  doc for why that's safe. */
    public static boolean teleportPlayerIn(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel wyrldLevel = server.getLevel(WyrldSpaceDimension.KEY);
        if (wyrldLevel == null) {
            return false;
        }
        GlobalPos from = GlobalPos.of(player.level().dimension(), player.blockPosition());
        PlayerWyrldData current = player.getData(ModAttachments.WYRLD_DATA);
        player.setData(ModAttachments.WYRLD_DATA, current.enteringCraftZoneDirectly(from));

        int x = (COBBLESTONE_MIN_X + COBBLESTONE_MAX_X) / 2;
        int z = (MIN_Z + MAX_Z) / 2;
        player.teleportTo(wyrldLevel, x + 0.5, WyrldManager.BASELINE_Y, z + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);
        return true;
    }
}
