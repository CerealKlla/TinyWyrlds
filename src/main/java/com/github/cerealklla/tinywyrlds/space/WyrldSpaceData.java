package com.github.cerealklla.tinywyrlds.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintArea;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Server-wide (not per-dimension -- fetched via {@code server.getDataStorage()}, same pattern
 * Cartographyr's own {@code CartographySavedData} uses) persistent store backing {@code
 * WyrldManager}: the {@link WyrldSpaceAllocator}'s free list, and every live {@link
 * WyrldInstanceRecord} keyed by its own UUID.
 *
 * <p>The allocator itself is NOT persisted directly -- {@link #allocator()} hands out a fresh
 * {@link WyrldSpaceAllocator} wrapping a copy of the stored free list, and the caller must call
 * {@link #commitAllocator} afterward to write any mutation back. This avoids an allocator silently
 * mutating this store's state without an explicit {@code setDirty()}.
 */
public final class WyrldSpaceData extends SavedData {

    public static final SavedDataType<WyrldSpaceData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "wyrld_space_data"),
            WyrldSpaceData::new,
            codec()
    );

    private final List<FreeRect> freeRects;
    private final Map<UUID, WyrldInstanceRecord> instances;

    private WyrldSpaceData() {
        this(WyrldSpaceAllocator.initialUniverse(List.of(WyrldPrintArea.RESERVED_BOUNDS)), Map.of());
    }

    private WyrldSpaceData(List<FreeRect> freeRects, Map<UUID, WyrldInstanceRecord> instances) {
        this.freeRects = new ArrayList<>(freeRects);
        this.instances = new HashMap<>(instances);
    }

    private static Codec<WyrldSpaceData> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.list(FreeRect.CODEC).fieldOf("free_rects").forGetter(d -> d.freeRects),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, WyrldInstanceRecord.CODEC)
                        .optionalFieldOf("instances", Map.of()).forGetter(d -> d.instances)
        ).apply(i, WyrldSpaceData::new));
    }

    public static WyrldSpaceData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public WyrldSpaceAllocator allocator() {
        return new WyrldSpaceAllocator(freeRects);
    }

    public void commitAllocator(WyrldSpaceAllocator allocator) {
        freeRects.clear();
        freeRects.addAll(allocator.snapshot());
        setDirty();
    }

    public void putInstance(WyrldInstanceRecord instance) {
        instances.put(instance.id(), instance);
        setDirty();
    }

    public Optional<WyrldInstanceRecord> getInstance(UUID id) {
        return Optional.ofNullable(instances.get(id));
    }

    public void removeInstance(UUID id) {
        if (instances.remove(id) != null) {
            setDirty();
        }
    }

    public Map<UUID, WyrldInstanceRecord> allInstances() {
        return Map.copyOf(instances);
    }
}
