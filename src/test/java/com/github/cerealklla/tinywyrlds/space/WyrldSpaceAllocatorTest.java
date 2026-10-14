package com.github.cerealklla.tinywyrlds.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WyrldSpaceAllocatorTest {

    @Test
    void initialUniverseWithAHoleNeverAllocatesInsideIt() {
        FreeRect hole = new FreeRect(-100, -100, 100, 100);
        WyrldSpaceAllocator allocator = new WyrldSpaceAllocator(WyrldSpaceAllocator.initialUniverse(List.of(hole)));

        // Repeatedly allocate small reservations and release half of them -- regardless of where
        // the packer lands each one, none should ever overlap the reserved hole.
        for (int i = 0; i < 20; i++) {
            WyrldSpaceAllocator.Reservation reservation = allocator.allocate(30, 30).orElseThrow();
            assertFalse(overlaps(reservation.full(), hole), "a reservation must never land inside the reserved hole");
        }
    }

    @Test
    void initialUniverseWithAHoleStillCoversTheRestOfTheUniverse() {
        FreeRect hole = new FreeRect(-100, -100, 100, 100);
        List<FreeRect> withHole = WyrldSpaceAllocator.initialUniverse(List.of(hole));

        long holeArea = hole.area();
        long fullUniverseArea = (long) (2L * WyrldSpaceAllocator.UNIVERSE_HALF_EXTENT) * (2L * WyrldSpaceAllocator.UNIVERSE_HALF_EXTENT);
        long piecesArea = withHole.stream().mapToLong(FreeRect::area).sum();

        assertEquals(fullUniverseArea - holeArea, piecesArea, "the hole's area should be the only area missing from the universe");
    }

    @Test
    void allocateReturnsPaddedFullRectAndInnerUsableRect() {
        WyrldSpaceAllocator allocator = new WyrldSpaceAllocator(WyrldSpaceAllocator.initialUniverse());

        WyrldSpaceAllocator.Reservation reservation = allocator.allocate(20, 30).orElseThrow();

        assertEquals(20, reservation.inner().width());
        assertEquals(30, reservation.inner().length());
        assertEquals(20 + 2 * WyrldSpaceAllocator.BUFFER_MARGIN, reservation.full().width());
        assertEquals(30 + 2 * WyrldSpaceAllocator.BUFFER_MARGIN, reservation.full().length());
    }

    @Test
    void twoAllocationsNeverOverlapEvenWhenWildlyDifferentShapes() {
        WyrldSpaceAllocator allocator = new WyrldSpaceAllocator(WyrldSpaceAllocator.initialUniverse());

        // A pathological perfectly-linear 20-room layout (10x10 each) and a compact 40-room event
        // dungeon landing in the same square-ish footprint -- neither should be artificially capped,
        // and their reserved regions must never touch.
        WyrldSpaceAllocator.Reservation line = allocator.allocate(200, 10).orElseThrow();
        WyrldSpaceAllocator.Reservation big = allocator.allocate(400, 400).orElseThrow();

        assertFalse(overlaps(line.full(), big.full()), "two reservations must never overlap");
    }

    @Test
    void releasingEverythingCoalescesBackToOneFreeRect() {
        WyrldSpaceAllocator allocator = new WyrldSpaceAllocator(WyrldSpaceAllocator.initialUniverse());

        WyrldSpaceAllocator.Reservation a = allocator.allocate(50, 50).orElseThrow();
        WyrldSpaceAllocator.Reservation b = allocator.allocate(30, 90).orElseThrow();

        allocator.release(a.full());
        allocator.release(b.full());

        assertEquals(1, allocator.snapshot().size(), "releasing every reservation should coalesce the free list back down to the original single universe rect");
        FreeRect sole = allocator.snapshot().get(0);
        assertEquals((long) (2L * WyrldSpaceAllocator.UNIVERSE_HALF_EXTENT) * (2L * WyrldSpaceAllocator.UNIVERSE_HALF_EXTENT), sole.area());
    }

    @Test
    void releasedSpaceIsReusableByALaterDifferentlyShapedRequest() {
        WyrldSpaceAllocator allocator = new WyrldSpaceAllocator(WyrldSpaceAllocator.initialUniverse());

        WyrldSpaceAllocator.Reservation first = allocator.allocate(100, 100).orElseThrow();
        allocator.release(first.full());

        // Nothing about the freed space's old shape should constrain a later, differently-shaped request.
        WyrldSpaceAllocator.Reservation second = allocator.allocate(10, 500).orElseThrow();

        assertTrue(second.inner().width() == 10 && second.inner().length() == 500);
    }

    private static boolean overlaps(FreeRect a, FreeRect b) {
        return a.x1() < b.x2() && b.x1() < a.x2() && a.z1() < b.z2() && b.z1() < a.z2();
    }
}
