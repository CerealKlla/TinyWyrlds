package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real Θ(1) lookup behind {@code api.TinyWyrlds#toggleState} -- a plain {@code Map}, not a
 * cached snapshot, kept correct entirely by {@link TaggedInteractiveBlockEntity}'s own lifecycle
 * (registering/deregistering itself) rather than by anything external tracking positions. Only ever
 * holds entries for a block that actually has a non-blank tag and a real {@code dungeonInstanceId}
 * -- every other block entity in a Wyrld (chests, signs, decorations) never touches this at all, so
 * the map's size scales with how many tagged interactive blocks exist, never with a dungeon's total
 * content. In-memory only, same "Wyrlds are ephemeral, don't persist this separately" convention as
 * {@code wyrldprint.entrance.EntranceEnterResults}.
 */
public final class TaggedInteractiveRegistry {

    private record Key(UUID dungeonInstanceId, String tag) {
    }

    private static final Map<Key, TaggedInteractiveBlockEntity> ENTRIES = new ConcurrentHashMap<>();

    private TaggedInteractiveRegistry() {
    }

    static void register(UUID dungeonInstanceId, String tag, TaggedInteractiveBlockEntity blockEntity) {
        ENTRIES.put(new Key(dungeonInstanceId, tag), blockEntity);
    }

    /** Only removes the mapping if {@code blockEntity} is still the one currently registered for this key -- a stale deregister call (e.g. the old block entity's own {@code setRemoved} firing after a piston move already let the new one re-register under the same key) must never evict the new, correct entry. */
    static void unregister(UUID dungeonInstanceId, String tag, TaggedInteractiveBlockEntity blockEntity) {
        ENTRIES.remove(new Key(dungeonInstanceId, tag), blockEntity);
    }

    public static Optional<TaggedInteractiveBlockEntity> find(UUID dungeonInstanceId, String tag) {
        return Optional.ofNullable(ENTRIES.get(new Key(dungeonInstanceId, tag)));
    }
}
