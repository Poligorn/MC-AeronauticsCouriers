// Offline layout trace mirroring BoardRowMetrics + BoardRowLayout (no Minecraft).
// Run: javac tools/board_row_layout_trace.java && java -cp tools board_row_layout_trace
class board_row_layout_trace {
    static final int ROW_FRAME_SIDE = 6;
    static final int ROW_INSET_LEFT = 4;
    static final int PAD_TOP = 3;
    static final int LINE1_HEIGHT = 9;
    static final int LINE_GAP = 2;
    static final int LINE2_ICON = 16;
    static final int PAD_BOTTOM = 3;
    static final int BUTTON_HEIGHT = 18;
    static final int ICON = 18;
    static final int GAP = 2;
    static final int DETAILS_MAX = 72;
    static final int DETAILS_MIN = 40;
    static final int DETAILS_PAD = 10;
    static final int SECONDARY_W = 76;
    static final int LIST_PAD_RIGHT = 8;

    static int rowHeight() {
        return PAD_TOP + LINE1_HEIGHT + LINE_GAP + LINE2_ICON + PAD_BOTTOM;
    }

    static int line1Y(int rowTop) {
        return rowTop + PAD_TOP;
    }

    static int line2Y(int rowTop) {
        return rowTop + PAD_TOP + LINE1_HEIGHT + LINE_GAP;
    }

    static int buttonY(int rowTop) {
        return rowTop + (rowHeight() - BUTTON_HEIGHT) / 2;
    }

    static void layoutRow(int index, int sx, int sw, int listY, boolean hasSecondary) {
        int rowY = listY + index * rowHeight();
        int frameLeft = sx + ROW_FRAME_SIDE;
        int textLeft = frameLeft + ROW_INSET_LEFT;
        int right = sx + sw - LIST_PAD_RIGHT;
        int secondaryX = right - SECONDARY_W;
        int detailsRight = hasSecondary ? secondaryX - GAP : right;
        int detailsW = DETAILS_MAX;
        int detailsX = detailsRight - detailsW;
        int crateX = detailsX - GAP - ICON;
        int mapX = crateX - GAP - ICON;
        int textRight = mapX - 4;
        int frameRight = hasSecondary ? secondaryX + SECONDARY_W : detailsX + detailsW;
        int frameW = frameRight - frameLeft;
        int btnY = buttonY(rowY);
        System.out.printf(
                "row %d frame=(%d,%d %dx%d) line1Y=%d line2Y=%d text=[%d,%d] map=(%d,%d) crate=(%d,%d) details=(%d,%d) btnY=%d%n",
                index, frameLeft, rowY, frameW, rowHeight(), line1Y(rowY), line2Y(rowY), textLeft, textRight, mapX,
                btnY, crateX, btnY, detailsX, btnY, btnY);
    }

    public static void main(String[] args) {
        int sx = 120;
        int sw = 520;
        int listY = 80;
        System.out.println("rowHeight=" + rowHeight());
        for (int i = 0; i < 7; i++) {
            layoutRow(i, sx, sw, listY, i % 2 == 0);
        }
    }
}
