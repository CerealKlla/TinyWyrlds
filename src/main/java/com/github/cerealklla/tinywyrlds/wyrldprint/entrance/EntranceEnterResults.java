package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A global, in-memory (not persisted -- same "accepted minor debris" tradeoff as elsewhere in this
 * suite, e.g. Myssions' own {@code BuyItemTargets} doc) lookup of the last {@link
 * EntranceEnterResult} per entrance id, written by {@link WyrldEntranceEntity#enter}. Exists so an
 * external caller (via {@code api.TinyWyrlds#getEnterResult}) can learn "did this entrance get
 * used, and what did it generate" with a plain synchronous read -- matching this suite's own
 * established "listening mod polls/calls through a bridge" convention -- without needing to find
 * or hold a live reference to the actual {@code WyrldEntranceEntity} instance. Replaced an earlier
 * design built around reading {@code lastEnterResult()} directly off the entity right after a
 * {@code PlayerInteractEvent.EntityInteract} fired, which stopped making sense once entry became a
 * proximity-triggered walk-in rather than a right-click (see {@code
 * WyrldEntranceProximityTicker}'s own doc) -- there's no shared interact event left for a
 * lower-priority listener to piggyback on.
 */
public final class EntranceEnterResults {

    private static final Map<UUID, EntranceEnterResult> RESULTS = new ConcurrentHashMap<>();

    private EntranceEnterResults() {
    }

    public static void record(UUID entranceId, EntranceEnterResult result) {
        RESULTS.put(entranceId, result);
    }

    public static Optional<EntranceEnterResult> get(UUID entranceId) {
        return Optional.ofNullable(RESULTS.get(entranceId));
    }
}
