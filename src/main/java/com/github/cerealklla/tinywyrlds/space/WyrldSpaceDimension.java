package com.github.cerealklla.tinywyrlds.space;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The one permanent, shared dimension every Wyrld instance lives inside -- registered via
 * datapack ({@code data/tinywyrlds/dimension/wyrld_space.json} + its {@code dimension_type}), not
 * created dynamically at runtime. A real, custom {@code min_y=0}/{@code height=2032} void-flat
 * dimension, same mechanism the Nether/End already use, just owned by this mod.
 *
 * <p>Isolated per-instance dimensions were considered and rejected: NeoForge/vanilla don't support
 * creating real dimensions at runtime without unofficial reflection hacks, and each live dimension
 * carries its own lighting/entity-tick/chunk-manager overhead. Instead, every Wyrld is a bounded,
 * non-overlapping region packed into this one dimension by {@link WyrldSpaceAllocator} -- see
 * context/design-document.md for the full back-and-forth that led here.
 */
public final class WyrldSpaceDimension {

    private WyrldSpaceDimension() {
    }

    public static final ResourceKey<Level> KEY =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "wyrld_space"));
}
