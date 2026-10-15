package com.github.cerealklla.tinywyrlds.instance;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.space.FreeRect;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceAllocator;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceData;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Orchestrates a Wyrld's whole lifecycle: reserve space ({@link #createWyrld}), teleport a player
 * in/out ({@link #enter}/{@link #leave}), and the periodic idle-staleness/deletion sweep ({@link
 * #tick}, driven by {@code WyrldLifecycleTicker}). No room/recipe content exists yet -- see {@link
 * WyrldInstanceRecord}'s own doc for what's intentionally not here.
 */
public final class WyrldManager {

    private WyrldManager() {
    }

    /** Baseline Y every Wyrld starts stamping from. The custom {@code wyrld_space} dimension_type
     *  runs {@code min_y=0}..{@code height=2032}, so this sits comfortably inside that with
     *  headroom on both sides for tall generated layouts. */
    public static final int BASELINE_Y = 512;

    /** No player present in an instance for this long (ticks) marks it STALE. */
    public static final int STALE_AFTER_TICKS = 20 * 60 * 5; // 5 minutes.
    /** STALE for this much longer (ticks) with still nobody entering gets it wiped and freed. */
    public static final int DELETE_AFTER_TICKS = 20 * 60 * 15; // 15 minutes.

    /** Fixed vertical range cleared on wipe, centered on {@link #BASELINE_Y} -- a placeholder until
     *  real room/recipe content exists and can report its own actual vertical extent to clear. */
    private static final int CLEAR_BELOW_BASELINE = 16;
    private static final int CLEAR_ABOVE_BASELINE = 256;

    public static WyrldInstanceRecord createWyrld(MinecraftServer server, int innerWidth, int innerLength) {
        WyrldSpaceData data = WyrldSpaceData.get(server);
        WyrldSpaceAllocator allocator = data.allocator();
        WyrldSpaceAllocator.Reservation reservation = allocator.allocate(innerWidth, innerLength)
                .orElseThrow(() -> new IllegalStateException("WyrldSpaceAllocator could not find room for a "
                        + innerWidth + "x" + innerLength + " Wyrld -- should not happen given the allocator's universe size."));
        data.commitAllocator(allocator);

        long gameTime = server.overworld().getGameTime();
        WyrldInstanceRecord instance = new WyrldInstanceRecord(UUID.randomUUID(), reservation.full(), reservation.inner(),
                BASELINE_Y, WyrldState.ACTIVE, gameTime);
        data.putInstance(instance);
        TinyWyrldsMod.LOGGER.info("Created Wyrld {} ({}x{} inner, {}x{} padded)", instance.id(),
                innerWidth, innerLength, reservation.full().width(), reservation.full().length());
        return instance;
    }

    /** Teleports {@code player} into {@code wyrldId}'s inner footprint (its generic center),
     *  recording where they came from so {@link #leave} can send them back. Reviving a STALE
     *  instance back to ACTIVE. */
    public static boolean enter(ServerPlayer player, UUID wyrldId) {
        return enterAt(player, wyrldId, null);
    }

    /** Same as {@link #enter}, but teleports to {@code exactPos} instead of the inner footprint's
     *  generic center when {@code exactPos} is non-null -- added 2026-10-14 for {@code
     *  generate.OneOffGenerator}'s real Spawn Location marker ("a 'spawn location' block ... for
     *  where the players will actually arrive"), so a generated dungeon's ENTRANCE piece lands the
     *  player exactly where its author placed that marker, not just somewhere in the middle of the
     *  whole reserved footprint. */
    public static boolean enterAt(ServerPlayer player, UUID wyrldId, BlockPos exactPos) {
        MinecraftServer server = player.level().getServer();
        Optional<WyrldInstanceRecord> maybeInstance = WyrldSpaceData.get(server).getInstance(wyrldId);
        if (maybeInstance.isEmpty()) {
            return false;
        }
        ServerLevel wyrldLevel = server.getLevel(WyrldSpaceDimension.KEY);
        if (wyrldLevel == null) {
            return false;
        }
        WyrldInstanceRecord instance = maybeInstance.get();

        GlobalPos from = GlobalPos.of(player.level().dimension(), player.blockPosition());
        PlayerWyrldData current = player.getData(ModAttachments.WYRLD_DATA);
        player.setData(ModAttachments.WYRLD_DATA, current.enteringWyrld(wyrldId, from));

        BlockPos spawnBlockPos;
        double x, y, z;
        if (exactPos != null) {
            spawnBlockPos = exactPos;
            x = exactPos.getX() + 0.5;
            y = exactPos.getY();
            z = exactPos.getZ() + 0.5;
        } else {
            FreeRect inner = instance.innerRect();
            spawnBlockPos = new BlockPos((int) Math.floor(inner.centerX()), instance.baselineY(), (int) Math.floor(inner.centerZ()));
            x = inner.centerX() + 0.5;
            y = instance.baselineY();
            z = inner.centerZ() + 0.5;
        }
        // Explicit user spec, 2026-10-15: "if a player dies [in a Wyrld] they should respawn at
        // the Spawn Point they were given when entering" -- remembered here (not cleared on leave,
        // see PlayerWyrldData's own doc), read back by WyrldRespawnListener.
        player.setData(ModAttachments.WYRLD_DATA, player.getData(ModAttachments.WYRLD_DATA).withWyrldSpawnPos(spawnBlockPos));
        player.teleportTo(wyrldLevel, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);

        reviveIfStale(server, instance);
        return true;
    }

    /** Shared by {@link #leave} and {@code wyrldprint.entrance.ExitPortalBlock}'s own {@code
     *  Portal#getPortalDestination} -- resolves where a leave should land (and clears the player's
     *  own Wyrld-tracking state) without actually performing the teleport, so the portal block can
     *  hand the resulting {@code TeleportTransition} back to vanilla's own portal-standing system
     *  instead of teleporting directly itself. @return {@code null} if {@code player} isn't
     *  currently tracked as being inside any Wyrld. */
    public static LeaveDestination resolveLeaveDestination(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PlayerWyrldData current = player.getData(ModAttachments.WYRLD_DATA);
        if (current.returnPos().isEmpty()) {
            return null;
        }
        GlobalPos returnPos = current.returnPos().get();
        ServerLevel returnLevel = server.getLevel(returnPos.dimension());
        if (returnLevel == null) {
            return null;
        }
        player.setData(ModAttachments.WYRLD_DATA, current.leavingWyrld());
        return new LeaveDestination(returnLevel, returnPos.pos());
    }

    public record LeaveDestination(ServerLevel level, BlockPos pos) {
    }

    /** Teleports {@code player} back to wherever they entered their current Wyrld from -- kept
     *  for {@code /tinywyrlds leave} and the (deliberately still-registered, just no longer
     *  auto-granted on entry) {@code wyrldprint.entrance.WyrldReturnItem}. The real player-facing
     *  exit mechanic is now {@code wyrldprint.entrance.ExitPortalBlock}, which uses {@link
     *  #resolveLeaveDestination} directly instead of this method. */
    public static boolean leave(ServerPlayer player) {
        LeaveDestination destination = resolveLeaveDestination(player);
        if (destination == null) {
            return false;
        }
        BlockPos pos = destination.pos();
        player.teleportTo(destination.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);
        return true;
    }

    private static void reviveIfStale(MinecraftServer server, WyrldInstanceRecord instance) {
        if (instance.state() != WyrldState.STALE) {
            return;
        }
        WyrldSpaceData.get(server).putInstance(instance.withState(WyrldState.ACTIVE, server.overworld().getGameTime()));
    }

    /** Periodic sweep -- see {@code WyrldLifecycleTicker}. Marks an idle ACTIVE instance STALE,
     *  and wipes+frees a STALE instance nobody's returned to within {@link #DELETE_AFTER_TICKS}. */
    public static void tick(MinecraftServer server) {
        WyrldSpaceData data = WyrldSpaceData.get(server);
        Set<UUID> occupiedWyrlds = occupiedWyrldIds(server);
        long now = server.overworld().getGameTime();

        for (WyrldInstanceRecord instance : data.allInstances().values()) {
            if (occupiedWyrlds.contains(instance.id())) {
                if (instance.state() != WyrldState.ACTIVE || instance.lastActiveGameTime() != now) {
                    data.putInstance(instance.withState(WyrldState.ACTIVE, now));
                }
                continue;
            }
            long idleTicks = now - instance.lastActiveGameTime();
            if (instance.state() == WyrldState.ACTIVE && idleTicks >= STALE_AFTER_TICKS) {
                data.putInstance(instance.withState(WyrldState.STALE, now));
            } else if (instance.state() == WyrldState.STALE && idleTicks >= STALE_AFTER_TICKS + DELETE_AFTER_TICKS) {
                deleteWyrld(server, instance.id());
            }
        }
    }

    private static Set<UUID> occupiedWyrldIds(MinecraftServer server) {
        Set<UUID> result = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getData(ModAttachments.WYRLD_DATA).currentWyrldId().ifPresent(result::add);
        }
        return result;
    }

    /** Wipes an instance's inner footprint to air, removes non-player entities inside it, and
     *  frees its reserved space back to the allocator. Does not evict a player still inside --
     *  callers (the stale sweep) only ever reach this once nobody's occupying the instance. */
    public static void deleteWyrld(MinecraftServer server, UUID wyrldId) {
        WyrldSpaceData data = WyrldSpaceData.get(server);
        Optional<WyrldInstanceRecord> maybeInstance = data.getInstance(wyrldId);
        if (maybeInstance.isEmpty()) {
            return;
        }
        WyrldInstanceRecord instance = maybeInstance.get();
        ServerLevel wyrldLevel = server.getLevel(WyrldSpaceDimension.KEY);
        if (wyrldLevel != null) {
            clearRect(wyrldLevel, instance.innerRect(), instance.baselineY());
        }
        WyrldSpaceAllocator allocator = data.allocator();
        allocator.release(instance.fullRect());
        data.commitAllocator(allocator);
        data.removeInstance(wyrldId);
        TinyWyrldsMod.LOGGER.info("Wiped and freed Wyrld {}", wyrldId);
    }

    private static void clearRect(ServerLevel level, FreeRect rect, int baselineY) {
        int minY = baselineY - CLEAR_BELOW_BASELINE;
        int maxY = baselineY + CLEAR_ABOVE_BASELINE;
        BlockPos.betweenClosed(rect.x1(), minY, rect.z1(), rect.x2() - 1, maxY, rect.z2() - 1)
                .forEach(pos -> {
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                });
        AABB bounds = new AABB(rect.x1(), minY, rect.z1(), rect.x2(), maxY, rect.z2());
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds, e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
    }
}
