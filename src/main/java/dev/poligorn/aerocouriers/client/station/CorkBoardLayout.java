package dev.poligorn.aerocouriers.client.station;

import net.minecraft.util.Mth;

/**
 * Station board geometry. Notes stay 136×108; column count follows the cork width
 * (4 / 3 / 2). The window is centred; the world around the frame stays visible.
 */
public final class CorkBoardLayout {
    public static final int LARGE_W = 640;
    public static final int LARGE_H = 360;
    public static final int SMALL_W = 480;
    public static final int SMALL_H = 270;
    public static final int NOTE_W = 136;
    public static final int NOTE_H = 108;
    public static final int PITCH_X = 148;
    public static final int PITCH_Y = 118;
    public static final int MAX_COLS = 4;
    /** 8 px glyph cell. */
    public static final int TEXT = 8;
    /** One footer line (count + optional hint). */
    public static final int FOOTER_H = 16;
    /**
     * Transparent / world margin under the last inner pixel of the frame sprite
     * (compact hole ends at y=258 of 270; large hole ends at y=342 of 360).
     */
    public static final int FRAME_INSET_NARROW = 12;
    public static final int FRAME_INSET_LARGE = 18;
    private static final int[][] JITTER = {
            {0, 1}, {1, -1}, {-1, 0}, {0, 1}, {1, 0}, {-1, 1}, {0, -1}, {1, 0}
    };

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public Rect offset(int dx, int dy) {
            return new Rect(x + dx, y + dy, w, h);
        }
    }

    public final int winX;
    public final int winY;
    public final int winW;
    public final int winH;
    /** Narrow chrome (480×270 insets). Notes are never shrunk. */
    public final boolean narrow;
    public final boolean exactFrame;
    public final int cols;
    public final int visibleRows;
    /** Rows that intersect the cork (partial last row is scissored). */
    public final int drawRows;
    public final int noteW;
    public final int noteH;
    public final int pitchX;
    public final int pitchY;
    public final Rect headerPlank;
    public final Rect filterBar;
    public final Rect cork;
    public final Rect footerPlank;
    public final Rect nameplate;
    public final Rect timerChip;
    public final Rect scroll;
    public final Rect adminBtn;
    public final Rect[] chips;
    public final int firstNoteX;
    public final int firstNoteY;
    public final int titleX;
    public final int titleY;
    public final int subtitleX;
    public final int subtitleY;
    public final int houseX;
    public final int houseY;
    public final int repLabelX;
    public final int repLabelY;
    public final int rankX;
    public final int rankY;
    public final int footerCountX;
    public final int footerCountY;
    public final int footerHintX;
    public final int footerHintY;

    private CorkBoardLayout(int screenW, int screenH) {
        boolean large = screenW >= LARGE_W && screenH >= LARGE_H;
        boolean mid = !large && screenW >= SMALL_W && screenH >= SMALL_H;
        narrow = !large;
        if (large) {
            winW = LARGE_W;
            winH = LARGE_H;
            exactFrame = true;
        } else if (mid) {
            winW = SMALL_W;
            winH = SMALL_H;
            exactFrame = true;
        } else {
            winW = Math.min(LARGE_W, Math.max(360, screenW));
            winH = Math.min(LARGE_H, Math.max(220, screenH));
            exactFrame = false;
        }
        winX = (screenW - winW) / 2;
        winY = (screenH - winH) / 2;
        int o = winX;
        int p = winY;
        noteW = NOTE_W;
        noteH = NOTE_H;
        pitchX = PITCH_X;
        pitchY = PITCH_Y;
        int side = narrow ? 11 : 18;
        int corkTop = narrow ? 82 : 77;
        int preferredCork = narrow ? 180 : 243;
        int innerBottom = p + winH - (narrow ? FRAME_INSET_NARROW : FRAME_INSET_LARGE);
        int corkH = Math.min(preferredCork, Math.max(NOTE_H, innerBottom - FOOTER_H - (p + corkTop)));
        cork = new Rect(o + side, p + corkTop, winW - 2 * side, corkH);
        footerPlank = new Rect(o + side, cork.y() + cork.h(), winW - 2 * side, FOOTER_H);
        footerCountX = o + side + 6;
        footerCountY = footerPlank.y() + (FOOTER_H - TEXT) / 2;
        footerHintX = footerCountX;
        footerHintY = footerCountY;
        if (narrow) {
            headerPlank = new Rect(o + 11, p + 11, winW - 22, 54);
            filterBar = new Rect(o + 11, p + 66, winW - 22, 16);
            nameplate = new Rect(o + 15, p + 14, Math.min(220, winW - 170), 23);
            timerChip = new Rect(o + winW - 157, p + 15, 142, 13);
            scroll = new Rect(o + winW - 19, cork.y() + 3, 6, Math.max(24, cork.h() - 8));
            chips = new Rect[] {
                    new Rect(o + 15, p + 68, 92, 12),
                    new Rect(o + 110, p + 68, 104, 12),
                    new Rect(o + 217, p + 68, 76, 12)
            };
            adminBtn = new Rect(o + winW - 80, p + 68, 56, 12);
            houseX = o + 21;
            houseY = p + 18;
            titleX = o + 35;
            titleY = p + 17;
            subtitleX = o + 35;
            subtitleY = p + 26;
            repLabelX = o + 17;
            repLabelY = p + 40;
            rankX = o + 17;
            rankY = p + 50;
        } else {
            headerPlank = new Rect(o + 18, p + 18, 604, 40);
            filterBar = new Rect(o + 18, p + 58, 604, 19);
            nameplate = new Rect(o + 24, p + 21, 181, 23);
            timerChip = new Rect(o + 476, p + 24, 142, 13);
            scroll = new Rect(o + 614, cork.y() + 3, 6, Math.max(24, cork.h() - 6));
            chips = new Rect[] {
                    new Rect(o + 26, p + 60, 98, 14),
                    new Rect(o + 128, p + 60, 106, 14),
                    new Rect(o + 238, p + 60, 84, 14)
            };
            adminBtn = new Rect(o + 556, p + 60, 56, 14);
            houseX = o + 33;
            houseY = p + 29;
            titleX = o + 50;
            titleY = p + 25;
            subtitleX = o + 50;
            subtitleY = p + 34;
            repLabelX = o + 26;
            repLabelY = p + 47;
            rankX = o + 26;
            rankY = p + 47;
        }
        int pad = 12;
        cols = Mth.clamp((cork.w() - pad) / PITCH_X, 1, MAX_COLS);
        int gridW = cols * PITCH_X - (PITCH_X - NOTE_W);
        firstNoteX = cork.x() + Math.max(pad / 2, (cork.w() - gridW) / 2);
        firstNoteY = cork.y() + 8;
        int usable = Math.max(NOTE_H, cork.h() - 8);
        visibleRows = Math.max(1, (usable - NOTE_H) / PITCH_Y + 1);
        drawRows = Math.max(visibleRows, (usable + PITCH_Y - 1) / PITCH_Y);
    }

    public static CorkBoardLayout of(int screenW, int screenH) {
        return new CorkBoardLayout(Math.max(1, screenW), Math.max(1, screenH));
    }

    public int noteX(int col, int index) {
        return firstNoteX + col * pitchX + jitter(index, 0);
    }

    public int noteY(int rowInView, int index) {
        return firstNoteY + rowInView * pitchY + jitter(index, 1);
    }

    public int jitter(int index, int axis) {
        int[] pair = JITTER[Math.floorMod(index, JITTER.length)];
        return pair[axis];
    }

    public int maxScrollRows(int orderCount) {
        int rows = (orderCount + cols - 1) / cols;
        return Math.max(0, rows - visibleRows);
    }

    public int totalRows(int orderCount) {
        return Math.max(1, (orderCount + cols - 1) / cols);
    }

    public int frameInsetBottom() {
        return narrow ? FRAME_INSET_NARROW : FRAME_INSET_LARGE;
    }

    /** Footer plank and the 8 px text cell sit inside the frame's inner hole. */
    public boolean footerFitsInFrame() {
        int innerBottom = winY + winH - frameInsetBottom();
        int textBottom = footerCountY + TEXT;
        return footerPlank.y() >= cork.y() + cork.h()
                && footerPlank.y() + footerPlank.h() <= innerBottom
                && footerCountY >= footerPlank.y()
                && textBottom <= footerPlank.y() + footerPlank.h()
                && textBottom <= innerBottom
                && footerPlank.x() >= winX
                && footerPlank.x() + footerPlank.w() <= winX + winW;
    }
}
