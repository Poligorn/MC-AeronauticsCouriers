package dev.poligorn.aerocouriers.client.board;

/** Computes per-row action button positions from list width (map/crate icons + optional secondary). */
public record BoardRowLayout(
        int mapX,
        int crateX,
        int secondaryX,
        int secondaryW,
        boolean hasSecondary,
        int textRight
) {
    public static final int ICON = 18;
    public static final int GAP = 2;
    public static final int SECONDARY_W = 76;
    public static final int LIST_PAD_RIGHT = 8;

    public static BoardRowLayout forRow(int sx, int sw, boolean hasSecondary) {
        int right = sx + sw - LIST_PAD_RIGHT;
        int secondaryX = right - SECONDARY_W;
        int iconsRight = hasSecondary ? secondaryX - GAP : right;
        int crateX = iconsRight - ICON;
        int mapX = crateX - GAP - ICON;
        int textRight = mapX - 4;
        return new BoardRowLayout(mapX, crateX, secondaryX, SECONDARY_W, hasSecondary, textRight);
    }

    /** Open board rows: map + crate icons only (mockup 1_board). */
    /** Inset from list right edge to card content (mockup x1-11). */
    public static final int CARD_RIGHT_INSET = 11;

    public static BoardRowLayout forOpenBoardRow(int sx, int sw) {
        int cardRight = sx + sw - LIST_PAD_RIGHT - CARD_RIGHT_INSET;
        int rewardRight = cardRight - 44;
        int crateX = cardRight - 20;
        int mapX = cardRight - 38;
        int textRight = rewardRight;
        return new BoardRowLayout(mapX, crateX, 0, 0, false, textRight);
    }
}
