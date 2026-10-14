package com.github.cerealklla.tinywyrlds.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 2D free-rectangle (guillotine-style) packer over the Wyrld Space dimension's X/Z plane. No
 * fixed cell size anywhere -- every reservation is sized to exactly what's requested, so an
 * unusually large or unusually shaped (e.g. a long thin line of connected rooms) Wyrld never hits
 * an artificial limit. See TinyWyrlds' context/design-document.md for the full reasoning -- a
 * fixed max-cell-size grid was proposed, then explicitly rejected by the user: a worst-case
 * perfectly-linear room layout blows a fixed cell size up arbitrarily, and there's no sane way to
 * pick "big enough we'd never hit it" as a real constant.
 *
 * <p><b>Placement</b>: best-fit by leftover area among free rects that fit the padded request,
 * guillotine-split (right + bottom remainder) on allocation. <b>Release</b>: the freed rect is
 * added back and eagerly coalesced with any exactly-adjacent, same-width/length free rect,
 * repeatedly, until no more merges are possible -- since Wyrlds are explicitly short-lived
 * ("frequently reset/wiped"), this gives fragmentation frequent chances to heal rather than only
 * ever growing monotonically. A periodic compaction pass is a known, deliberately deferred
 * follow-up if fragmentation ever becomes a real measured problem -- not built speculatively.
 *
 * <p>Vertical extent is deliberately NOT handled by this allocator at all -- a chunk column loads
 * as a whole regardless of Y, so "stacking" independent instances at different Y-bands within the
 * same X/Z columns was considered and rejected (it couples their load/tick lifecycle for no real
 * benefit). Every instance shares one baseline Y ({@code WyrldManager#BASELINE_Y}) and simply
 * extends as tall as its own content needs from there.
 *
 * <p>Pure in-memory logic over a free list handed in/out by the caller ({@link WyrldSpaceData}) --
 * this class has no persistence of its own and makes no NeoForge/Minecraft calls, so it's fully
 * unit-testable.
 */
public final class WyrldSpaceAllocator {

    /** Safely inside the vanilla world border hard limit (+/-29,999,984) -- this is "the whole
     *  map," not a per-instance worst-case guess; see this class's own doc for why those are
     *  different kinds of constants. */
    public static final int UNIVERSE_HALF_EXTENT = 10_000_000;

    /** Physical buffer kept between any two reservations' usable (inner) footprints, in blocks --
     *  guarantees two instances' real content can never touch, even mid-generation. */
    public static final int BUFFER_MARGIN = 8;

    private final List<FreeRect> freeRects;

    public WyrldSpaceAllocator(List<FreeRect> freeRects) {
        this.freeRects = new ArrayList<>(freeRects);
    }

    public static List<FreeRect> initialUniverse() {
        List<FreeRect> universe = new ArrayList<>();
        universe.add(new FreeRect(-UNIVERSE_HALF_EXTENT, -UNIVERSE_HALF_EXTENT, UNIVERSE_HALF_EXTENT, UNIVERSE_HALF_EXTENT));
        return universe;
    }

    public List<FreeRect> snapshot() {
        return List.copyOf(freeRects);
    }

    /**
     * Reserves a region at least {@code innerWidth} x {@code innerLength} (the real usable
     * placement area), padded by {@link #BUFFER_MARGIN} on every side. Returns the full (padded)
     * rect actually removed from the free list and the inner (usable) rect inside it -- empty only
     * if nothing in the free list is big enough, which shouldn't happen in practice given {@link
     * #UNIVERSE_HALF_EXTENT}.
     */
    public Optional<Reservation> allocate(int innerWidth, int innerLength) {
        int paddedWidth = innerWidth + 2 * BUFFER_MARGIN;
        int paddedLength = innerLength + 2 * BUFFER_MARGIN;

        FreeRect best = null;
        long bestLeftover = Long.MAX_VALUE;
        for (FreeRect candidate : freeRects) {
            if (!candidate.fits(paddedWidth, paddedLength)) {
                continue;
            }
            long leftover = candidate.area() - (long) paddedWidth * paddedLength;
            if (leftover < bestLeftover) {
                bestLeftover = leftover;
                best = candidate;
            }
        }
        if (best == null) {
            return Optional.empty();
        }

        freeRects.remove(best);
        FreeRect full = new FreeRect(best.x1(), best.z1(), best.x1() + paddedWidth, best.z1() + paddedLength);
        FreeRect inner = new FreeRect(full.x1() + BUFFER_MARGIN, full.z1() + BUFFER_MARGIN,
                full.x2() - BUFFER_MARGIN, full.z2() - BUFFER_MARGIN);

        // Guillotine split of the leftover L-shape: a full-height strip to the right of the
        // reservation, plus a reservation-width-only strip beneath it.
        FreeRect rightRemainder = new FreeRect(full.x2(), best.z1(), best.x2(), best.z2());
        FreeRect bottomRemainder = new FreeRect(full.x1(), full.z2(), full.x2(), best.z2());
        if (rightRemainder.width() > 0 && rightRemainder.length() > 0) {
            freeRects.add(rightRemainder);
        }
        if (bottomRemainder.width() > 0 && bottomRemainder.length() > 0) {
            freeRects.add(bottomRemainder);
        }

        return Optional.of(new Reservation(full, inner));
    }

    /** Returns a previously-allocated full (padded) rect to the free pool, coalescing eagerly. */
    public void release(FreeRect full) {
        freeRects.add(full);
        coalesce();
    }

    private void coalesce() {
        boolean mergedAny = true;
        while (mergedAny) {
            mergedAny = false;
            outer:
            for (int i = 0; i < freeRects.size(); i++) {
                for (int j = i + 1; j < freeRects.size(); j++) {
                    FreeRect a = freeRects.get(i);
                    FreeRect b = freeRects.get(j);
                    if (a.isAdjacentSameWidth(b) || a.isAdjacentSameLength(b)) {
                        freeRects.remove(j);
                        freeRects.remove(i);
                        freeRects.add(a.mergedWith(b));
                        mergedAny = true;
                        break outer;
                    }
                }
            }
        }
    }

    public record Reservation(FreeRect full, FreeRect inner) {
    }
}
