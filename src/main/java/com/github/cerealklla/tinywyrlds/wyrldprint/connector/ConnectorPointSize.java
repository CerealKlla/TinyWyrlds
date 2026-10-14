package com.github.cerealklla.tinywyrlds.wyrldprint.connector;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The three connector-point gauges, explicit user spec (2026-10-14): "Connector Point 1x2
 * [small], 3x3 [medium], 5x5 [large]" for wall (door-shaped) connectors, and "1x1, 3x3, 5x5" for
 * floor/ceiling (trapdoor-shaped) ones -- {@link #footprint()} is the same number either way (a
 * wall connector's "1x2" is just its own natural door footprint, i.e. no extra fill at size 1).
 * Each size gets its own stained-glass color so the gauge is readable at a glance while authoring
 * ({@link #glassBlock()}) -- an arbitrary but documented choice, not specified by the user.
 */
public enum ConnectorPointSize {
    SMALL(1, Blocks.LIGHT_BLUE_STAINED_GLASS),
    MEDIUM(3, Blocks.YELLOW_STAINED_GLASS),
    LARGE(5, Blocks.RED_STAINED_GLASS);

    public static final Codec<ConnectorPointSize> CODEC = Codec.STRING.xmap(
            s -> ConnectorPointSize.valueOf(s.toUpperCase(java.util.Locale.ROOT)),
            size -> size.name().toLowerCase(java.util.Locale.ROOT)
    );

    private final int footprint;
    private final Block glassBlock;

    ConnectorPointSize(int footprint, Block glassBlock) {
        this.footprint = footprint;
        this.glassBlock = glassBlock;
    }

    public int footprint() {
        return footprint;
    }

    public Block glassBlock() {
        return glassBlock;
    }
}
