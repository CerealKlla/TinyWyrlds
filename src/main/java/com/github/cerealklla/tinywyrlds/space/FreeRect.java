package com.github.cerealklla.tinywyrlds.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An axis-aligned rectangle on the Wyrld Space dimension's X/Z plane, half-open: [x1, x2) x
 * [z1, z2). Used both for a free region in {@link WyrldSpaceAllocator}'s free list and for a
 * reserved Wyrld's own footprint (full/padded or inner/usable, see that class's own doc).
 */
public record FreeRect(int x1, int z1, int x2, int z2) {

    public static final Codec<FreeRect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x1").forGetter(FreeRect::x1),
            Codec.INT.fieldOf("z1").forGetter(FreeRect::z1),
            Codec.INT.fieldOf("x2").forGetter(FreeRect::x2),
            Codec.INT.fieldOf("z2").forGetter(FreeRect::z2)
    ).apply(instance, FreeRect::new));

    public int width() {
        return x2 - x1;
    }

    public int length() {
        return z2 - z1;
    }

    public long area() {
        return (long) width() * length();
    }

    public boolean fits(int neededWidth, int neededLength) {
        return width() >= neededWidth && length() >= neededLength;
    }

    public int centerX() {
        return x1 + width() / 2;
    }

    public int centerZ() {
        return z1 + length() / 2;
    }

    /** True if this rect and {@code other} share a full edge of matching width, at the same X range. */
    public boolean isAdjacentSameWidth(FreeRect other) {
        return x1 == other.x1 && x2 == other.x2 && (z2 == other.z1 || other.z2 == z1);
    }

    /** True if this rect and {@code other} share a full edge of matching length, at the same Z range. */
    public boolean isAdjacentSameLength(FreeRect other) {
        return z1 == other.z1 && z2 == other.z2 && (x2 == other.x1 || other.x2 == x1);
    }

    /** Merges this rect with an adjacent, same-width-or-length rect into the one bigger rect spanning both. */
    public FreeRect mergedWith(FreeRect other) {
        return new FreeRect(Math.min(x1, other.x1), Math.min(z1, other.z1), Math.max(x2, other.x2), Math.max(z2, other.z2));
    }
}
