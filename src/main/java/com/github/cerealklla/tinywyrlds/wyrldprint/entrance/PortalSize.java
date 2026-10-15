package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

/** The three flat Exit Portal sizes -- explicit user spec, 2026-10-15: "I'd like 3 variants, a
 *  1x2, 3x3, and 5x5 flat portal." {@link #width()} is the footprint along the portal's own
 *  horizontal axis (the "flat" dimension you walk through, not into); {@link #height()} is always
 *  the same as {@link #width()} except {@link #SMALL}, whose natural shape is "1 wide, 2 tall" (a
 *  plain doorway) rather than a literal 1x1 -- same "no extra fill at size 1" precedent {@code
 *  connector.ConnectorPointSize} already set for this exact size family. */
public enum PortalSize {
    SMALL(1, 2),
    MEDIUM(3, 3),
    LARGE(5, 5);

    private final int width;
    private final int height;

    PortalSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
