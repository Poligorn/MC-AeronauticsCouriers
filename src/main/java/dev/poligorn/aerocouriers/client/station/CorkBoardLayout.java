package dev.poligorn.aerocouriers.client.station;

/**
 * Exact 640×360 / 480×270 rects from cork_board_assets_v2/layout.json.
 * Window is centered; the world around the frame stays visible.
 */
public final class CorkBoardLayout {
    public static final int LARGE_W = 640;
    public static final int LARGE_H = 360;
    public static final int SMALL_W = 480;
    public static final int SMALL_H = 270;
    public static final int NOTE_W = 136;
    public static final int NOTE_H = 108;
    public static final int NOTE_H_COMPACT = 84;
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
    public final boolean compact;
    public final boolean exactFrame;
    public final int cols;
    public final int visibleRows;
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
    public final Rect tabletBtn;
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
    public final int repBarX;
    public final int repBarY;
    public final int repBarW;
    public final int repValueX;
    public final int repValueY;
    public final int rankX;
    public final int rankY;
    public final int footerCountX;
    public final int footerCountY;
    public final int footerHintX;
    public final int footerHintY;

    private CorkBoardLayout(int screenW, int screenH) {
        compact = screenW < 600 || screenH < 340;
        cols = compact ? (screenW < 440 ? 2 : 3) : 4;
        visibleRows = 2;
        noteW = NOTE_W;
        noteH = compact ? NOTE_H_COMPACT : NOTE_H;
        pitchX = 148;
        pitchY = compact ? 90 : 118;
        boolean useCompactFrame = compact;
        int layoutW = useCompactFrame ? SMALL_W : LARGE_W;
        int layoutH = useCompactFrame ? SMALL_H : LARGE_H;
        winW = Math.min(layoutW, Math.max(360, screenW));
        winH = Math.min(layoutH, Math.max(220, screenH));
        exactFrame = winW == layoutW && winH == layoutH;
        winX = (screenW - winW) / 2;
        winY = (screenH - winH) / 2;
        int o = winX;
        int p = winY;
        if (compact) {
            headerPlank = new Rect(o + 11, p + 11, 458, 34);
            filterBar = new Rect(o + 11, p + 45, 458, 16);
            cork = new Rect(o + 11, p + 61, 458, 180);
            footerPlank = new Rect(o + 11, p + 241, 458, 18);
            nameplate = new Rect(o + 15, p + 14, 155, 15);
            timerChip = new Rect(o + 323, p + 15, 142, 13);
            scroll = new Rect(o + 461, p + 64, 6, 174);
            tabletBtn = new Rect(o + 288, p + 243, 176, 13);
            chips = new Rect[] {
                    new Rect(o + 15, p + 47, 92, 12),
                    new Rect(o + 110, p + 47, 104, 12),
                    new Rect(o + 217, p + 47, 76, 12)
            };
            adminBtn = new Rect(o + 400, p + 47, 56, 12);
            firstNoteX = o + 23;
            firstNoteY = p + 65;
            houseX = o + 21;
            houseY = p + 18;
            titleX = o + 35;
            titleY = p + 17;
            subtitleX = o + 35;
            subtitleY = p + 17;
            repLabelX = o + 17;
            repLabelY = p + 33;
            repBarX = o + 72;
            repBarY = p + 34;
            repBarW = 114;
            repValueX = o + 193;
            repValueY = p + 33;
            rankX = o + 360;
            rankY = p + 33;
            footerCountX = o + 17;
            footerCountY = p + 246;
            footerHintX = o + 17;
            footerHintY = p + 246;
        } else {
            headerPlank = new Rect(o + 18, p + 18, 604, 40);
            filterBar = new Rect(o + 18, p + 58, 604, 19);
            cork = new Rect(o + 18, p + 77, 604, 243);
            footerPlank = new Rect(o + 18, p + 320, 604, 23);
            nameplate = new Rect(o + 24, p + 21, 181, 23);
            timerChip = new Rect(o + 476, p + 24, 142, 13);
            scroll = new Rect(o + 614, p + 80, 6, 237);
            tabletBtn = new Rect(o + 441, p + 323, 176, 17);
            chips = new Rect[] {
                    new Rect(o + 26, p + 60, 98, 14),
                    new Rect(o + 128, p + 60, 106, 14),
                    new Rect(o + 238, p + 60, 84, 14)
            };
            adminBtn = new Rect(o + 556, p + 60, 56, 14);
            firstNoteX = o + 30;
            firstNoteY = p + 85;
            houseX = o + 33;
            houseY = p + 29;
            titleX = o + 50;
            titleY = p + 25;
            subtitleX = o + 50;
            subtitleY = p + 34;
            repLabelX = o + 26;
            repLabelY = p + 47;
            repBarX = o + 130;
            repBarY = p + 46;
            repBarW = 150;
            repValueX = o + 288;
            repValueY = p + 47;
            rankX = o + 522;
            rankY = p + 47;
            footerCountX = o + 26;
            footerCountY = p + 323;
            footerHintX = o + 26;
            footerHintY = p + 332;
        }
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
}
