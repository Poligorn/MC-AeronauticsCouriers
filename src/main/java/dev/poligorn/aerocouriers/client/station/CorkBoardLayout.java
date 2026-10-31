package dev.poligorn.aerocouriers.client.station;

/**
 * Station cork board: 4×2 on 640×360, 3×2 compact notes on 480×270. Never pose-scales a 4×2 layout.
 */
public final class CorkBoardLayout {
    public static final int LARGE_W = 640;
    public static final int LARGE_H = 360;
    public static final int SMALL_W = 480;
    public static final int SMALL_H = 270;
    public static final int FRAME = 8;
    public static final int SCROLL_W = 7;
    public static final int GAP = 6;
    public static final int PIN = 4;

    public final int winX;
    public final int winY;
    public final int winW;
    public final int winH;
    public final boolean compact;
    public final int cols;
    public final int visibleRows;
    public final int headerH;
    public final int footerH;
    public final int innerX;
    public final int innerY;
    public final int innerW;
    public final int innerH;
    public final int corkX;
    public final int corkY;
    public final int corkW;
    public final int corkH;
    public final int noteW;
    public final int noteH;
    public final int scrollX;
    public final int scrollY;
    public final int scrollH;

    private CorkBoardLayout(int screenW, int screenH) {
        compact = screenW < LARGE_W || screenH < LARGE_H;
        cols = compact ? 3 : 4;
        visibleRows = 2;
        winW = Math.min(compact ? SMALL_W : LARGE_W, Math.max(360, screenW - 8));
        winH = Math.min(compact ? SMALL_H : LARGE_H, Math.max(220, screenH - 8));
        winX = (screenW - winW) / 2;
        winY = (screenH - winH) / 2;
        headerH = compact ? 58 : 70;
        footerH = compact ? 28 : 32;
        innerX = winX + FRAME;
        innerY = winY + FRAME;
        innerW = winW - FRAME * 2;
        innerH = winH - FRAME * 2;
        corkX = innerX + 6;
        corkY = innerY + headerH;
        corkW = innerW - 12 - SCROLL_W - 4;
        corkH = innerH - headerH - footerH;
        int cellW = (corkW - GAP * (cols + 1)) / cols;
        int cellH = (corkH - GAP * (visibleRows + 1)) / visibleRows;
        noteW = cellW;
        noteH = cellH;
        scrollX = corkX + corkW + 3;
        scrollY = corkY + 2;
        scrollH = corkH - 4;
    }

    public static CorkBoardLayout of(int screenW, int screenH) {
        return new CorkBoardLayout(Math.max(1, screenW), Math.max(1, screenH));
    }

    public int noteX(int col, int jitter) {
        return corkX + GAP + col * (noteW + GAP) + jitter;
    }

    public int noteY(int rowInView, int jitter) {
        return corkY + GAP + rowInView * (noteH + GAP) + jitter;
    }

    public int jitter(int seed, int axis) {
        int v = (seed * 31 + axis * 17) & 3;
        return v == 0 ? -1 : v == 3 ? 1 : 0;
    }

    public int maxScrollRows(int orderCount) {
        int rows = (orderCount + cols - 1) / cols;
        return Math.max(0, rows - visibleRows);
    }

    public int totalRows(int orderCount) {
        return Math.max(1, (orderCount + cols - 1) / cols);
    }
}
