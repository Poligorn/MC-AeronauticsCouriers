package dev.poligorn.aerocouriers.client.board;

import net.minecraft.client.gui.Font;

/** Computes per-row action button positions from list width. */
public record BoardRowLayout(
        int detailsX,
        int detailsW,
        int mapX,
        int crateX,
        int secondaryX,
        int secondaryW,
        boolean hasSecondary,
        int textRight
) {
    public static final int ICON = 18;
    public static final int GAP = 2;
    public static final int DETAILS_MAX = 68;
    public static final int DETAILS_MIN = 44;
    public static final int SECONDARY_W = 76;
    public static final int LIST_PAD_RIGHT = 8;

    public static BoardRowLayout forRow(int sx, int sw, Font font, String detailsLabel, boolean hasSecondary) {
        int right = sx + sw - LIST_PAD_RIGHT;
        int secondaryX = right - SECONDARY_W;
        int cursor = hasSecondary ? secondaryX - GAP : right;

        int detailsW = Math.min(DETAILS_MAX, Math.max(DETAILS_MIN, font.width(detailsLabel) + 10));
        cursor -= detailsW;
        int detailsX = cursor;
        cursor -= GAP;
        cursor -= ICON;
        int crateX = cursor;
        cursor -= GAP;
        cursor -= ICON;
        int mapX = cursor;

        int textRight = mapX - 4;
        return new BoardRowLayout(detailsX, detailsW, mapX, crateX, secondaryX, SECONDARY_W, hasSecondary, textRight);
    }
}
