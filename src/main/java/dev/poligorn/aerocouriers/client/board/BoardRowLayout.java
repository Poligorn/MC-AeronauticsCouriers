package dev.poligorn.aerocouriers.client.board;

import net.minecraft.client.gui.Font;

/** Computes per-row action button positions from list width. */
public record BoardRowLayout(
        int detailsX,
        int detailsW,
        String detailsLabelShown,
        int mapX,
        int crateX,
        int secondaryX,
        int secondaryW,
        boolean hasSecondary,
        int textRight
) {
    public static final int ICON = 18;
    public static final int GAP = 2;
    public static final int DETAILS_MAX = 72;
    public static final int DETAILS_MIN = 40;
    public static final int DETAILS_PAD = 10;
    public static final int SECONDARY_W = 76;
    public static final int LIST_PAD_RIGHT = 8;

    public static BoardRowLayout forRow(int sx, int sw, Font font, String detailsLabel, boolean hasSecondary) {
        int right = sx + sw - LIST_PAD_RIGHT;
        int secondaryX = right - SECONDARY_W;
        int detailsRight = hasSecondary ? secondaryX - GAP : right;

        int textRoom = DETAILS_MAX - DETAILS_PAD;
        String shown = BoardRowRenderer.clip(font, detailsLabel, textRoom);
        int detailsW = Math.min(DETAILS_MAX, Math.max(DETAILS_MIN, font.width(shown) + DETAILS_PAD));
        if (font.width(shown) + DETAILS_PAD > detailsW) {
            shown = BoardRowRenderer.clip(font, detailsLabel, detailsW - DETAILS_PAD);
            detailsW = Math.max(DETAILS_MIN, font.width(shown) + DETAILS_PAD);
        }

        int detailsX = detailsRight - detailsW;
        int crateX = detailsX - GAP - ICON;
        int mapX = crateX - GAP - ICON;
        int textRight = mapX - 4;
        return new BoardRowLayout(detailsX, detailsW, shown, mapX, crateX, secondaryX, SECONDARY_W, hasSecondary,
                textRight);
    }
}
