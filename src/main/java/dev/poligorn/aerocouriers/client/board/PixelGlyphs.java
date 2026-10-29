package dev.poligorn.aerocouriers.client.board;

import net.minecraft.client.gui.GuiGraphics;

/** 1-GUI-px bitmap icons from the approved tablet mockup ({@code #} = pixel). */
public final class PixelGlyphs {
    public static final String[] HOUSE = {"...#...", "..###..", ".#####.", "#######", ".##.##.", ".##.##.", ".#####."};
    public static final String[] FACE = {"..###..", ".#####.", ".#.#.#.", ".#####.", "..###..", ".#####.", "#######"};
    public static final String[] ARROW = {"....#..", ".....#.", "#######", ".....#.", "....#.."};
    public static final String[] WARN = {"...#...", "..###..", "..#.#..", ".##.##.", ".##.##.", "#######"};
    public static final String[] CLOCK = {"..#####..", ".#.....#.", "#...#...#", "#...#...#", "#...##..#", "#.......#",
            ".#.....#.", "..#####.."};
    public static final String[] BOX = {"#########", "#...#...#", "#...#...#", "#########", "#...#...#", "#...#...#",
            "#########"};
    public static final String[] LOCK = {"..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"};
    public static final String[] GRID = BOX;
    /** 9×5 cell icon on the waybill «all cells» link. */
    public static final String[] SHEET_GRID = {
            "#########",
            "#.#.#.#.#",
            "#########",
            "#.#.#.#.#",
            "#########"
    };
    public static final String[] PIN = {"..###..", ".#####.", ".##.##.", ".#####.", "..###..", "...#...", "...#..."};
    public static final String[] CHECK = {"......#", ".....#.", "#...#..", ".#.#...", "..#...."};
    public static final String[] CROSS = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
    public static final String[] UP = {"...#...", "..###..", ".#####.", "...#...", "...#..."};
    public static final String[] CHEST = {"#########", "#.......#", "#########", "#...#...#", "#...#...#", "#########"};
    public static final String[] DASH_ARROW_TIP = ARROW;
    public static final String[] UP_DOWN = {"..#.....", ".###..#.", "..#...#.", "..#...#.", "..#...#.", "..#..###",
            "..#...#."};

    private PixelGlyphs() {
    }

    public static int width(String[] rows) {
        return rows[0].length();
    }

    public static int height(String[] rows) {
        return rows.length;
    }

    public static void draw(GuiGraphics graphics, int x, int y, String[] rows, int color) {
        for (int j = 0; j < rows.length; j++) {
            String row = rows[j];
            int run = -1;
            for (int i = 0; i <= row.length(); i++) {
                boolean on = i < row.length() && row.charAt(i) == '#';
                if (on && run < 0) {
                    run = i;
                } else if (!on && run >= 0) {
                    graphics.fill(x + run, y + j, x + i, y + j + 1, color);
                    run = -1;
                }
            }
        }
    }
}
