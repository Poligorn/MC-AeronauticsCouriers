package dev.poligorn.aerocouriers.client.board;

/** Shared row geometry for {@link dev.poligorn.aerocouriers.client.CourierBoardScreen} and offline previews. */
public final class BoardRowMetrics {
    public static final int PAD_TOP = 3;
    public static final int PAD_BOTTOM = 3;
    public static final int LINE1_HEIGHT = 9;
    public static final int LINE_GAP = 2;
    /** Display size for 16×16 row stat sprites (coin/lock/clock/star). */
    public static final int LINE2_ICON = 16;
    public static final int ICON_TEXT_GAP = 4;
    public static final int PAIR_GAP = 8;
    public static final int ID_STATUS_GAP = 6;
    public static final int BUTTON_HEIGHT = 18;
    public static final int CUSTOMER_FACE = 8;
    public static final int FACE_NAME_GAP = 6;

    private BoardRowMetrics() {
    }

    public static int rowHeight() {
        return PAD_TOP + LINE1_HEIGHT + LINE_GAP + LINE2_ICON + PAD_BOTTOM;
    }

    public static int line1Y(int rowTop) {
        return rowTop + PAD_TOP;
    }

    public static int line2Y(int rowTop) {
        return rowTop + PAD_TOP + LINE1_HEIGHT + LINE_GAP;
    }

    public static int buttonY(int rowTop) {
        return rowTop + (rowHeight() - BUTTON_HEIGHT) / 2;
    }
}
