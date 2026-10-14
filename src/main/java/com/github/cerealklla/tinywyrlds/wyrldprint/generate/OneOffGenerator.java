package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.space.FreeRect;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintCell;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * First, deliberately simple pass at "Generate One-off" -- picks a random chain of {@code
 * roomCount} ROOM WyrldPrints (between {@code minRooms}/{@code maxRooms}), interleaved with a
 * random CONNECTOR between each pair, reserves a fresh Wyrld sized to fit the whole chain, and
 * stamps each piece one after another along the forward axis.
 *
 * <p><b>Known, deliberate simplification -- not real connector-matching.</b> Pieces are placed
 * purely by their own forward-size + a fixed 2-block gap, with zero regard for where each piece's
 * actual connector points sit or whether their sizes/facings line up -- a real generator needs to
 * read each piece's {@code WyrldPrintRecord#points()}, pick a compatible pair, and offset/rotate
 * accordingly (rotation support doesn't exist anywhere yet either). This version exists so
 * "Generate One-off" does something real and inspectable today rather than nothing; replacing the
 * placement logic with genuine connector-alignment is real, flagged follow-up work.
 */
public final class OneOffGenerator {

    private OneOffGenerator() {
    }

    private static final int GAP_BETWEEN_PIECES = 2;
    private static final int MARGIN = 5;

    /** @return the created Wyrld instance, or {@code null} if no ROOM WyrldPrints are saved at all. */
    public static WyrldInstanceRecord generate(MinecraftServer server, int minRooms, int maxRooms) {
        List<WyrldPrintRecord> rooms = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.ROOM).toList();
        List<WyrldPrintRecord> connectors = WyrldPrintStorage.get().listAll().stream()
                .filter(r -> r.type() == WyrldPrintType.CONNECTOR).toList();
        if (rooms.isEmpty()) {
            return null;
        }

        RandomSource random = RandomSource.create();
        int lowRooms = Math.min(minRooms, maxRooms);
        int highRooms = Math.max(minRooms, maxRooms);
        int roomCount = lowRooms + random.nextInt(highRooms - lowRooms + 1);

        List<WyrldPrintRecord> chain = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            chain.add(rooms.get(random.nextInt(rooms.size())));
            if (i < roomCount - 1 && !connectors.isEmpty()) {
                chain.add(connectors.get(random.nextInt(connectors.size())));
            }
        }

        int totalForward = chain.stream().mapToInt(WyrldPrintRecord::forwardSize).sum()
                + (chain.size() - 1) * GAP_BETWEEN_PIECES;
        int maxLateral = chain.stream().mapToInt(WyrldPrintRecord::lateralSize).max().orElse(10);

        WyrldInstanceRecord instance = WyrldManager.createWyrld(server, totalForward + 2 * MARGIN, maxLateral + 2 * MARGIN);
        ServerLevel level = server.getLevel(WyrldSpaceDimension.KEY);
        if (level == null) {
            return instance;
        }
        FreeRect inner = instance.innerRect();

        int cursorForward = inner.x1() + MARGIN;
        int lateralOrigin = inner.centerZ();
        for (WyrldPrintRecord piece : chain) {
            stampPiece(level, piece, cursorForward, lateralOrigin);
            cursorForward += piece.forwardSize() + GAP_BETWEEN_PIECES;
        }
        return instance;
    }

    private static void stampPiece(ServerLevel level, WyrldPrintRecord record, int worldForwardOrigin, int worldLateralOrigin) {
        List<WyrldPrintCell> sorted = new ArrayList<>(record.cells());
        sorted.sort(Comparator.comparingInt(WyrldPrintCell::relY));
        for (WyrldPrintCell cell : sorted) {
            BlockPos pos = new BlockPos(worldForwardOrigin + cell.relForward(), WyrldManager.BASELINE_Y + cell.relY(),
                    worldLateralOrigin + cell.relLateral());
            level.setBlock(pos, cell.state(), 3);
        }
    }
}
