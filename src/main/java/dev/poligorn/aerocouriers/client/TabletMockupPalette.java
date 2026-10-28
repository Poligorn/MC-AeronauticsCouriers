package dev.poligorn.aerocouriers.client;

/** Pixel mockup palette (build.py 640×360, 28.10.2026). */
public final class TabletMockupPalette {
    public static final int TXT = rgb(242, 214, 162);
    public static final int SEC = rgb(196, 154, 104);
    public static final int GOLD = rgb(240, 196, 90);
    public static final int CORAL = rgb(236, 128, 104);
    public static final int TEAL = rgb(110, 196, 182);
    public static final int BLUE = rgb(120, 186, 236);
    public static final int LOCKC = rgb(150, 130, 110);
    public static final int CARD = rgb(52, 30, 16);
    public static final int CARDSEL = rgb(74, 44, 22);
    public static final int CARDLK = rgb(40, 26, 16);
    public static final int BRASS = rgb(150, 86, 36);
    public static final int LISTBG = rgb(30, 15, 7);
    public static final int OUT = rgb(38, 20, 8);
    public static final int PANEL_BG = rgb(30, 16, 8);
    public static final int HEADER_BG = rgb(60, 36, 18);
    public static final int PREVIEW_BG = rgb(22, 12, 6);
    public static final int SLOT_FILL = rgb(20, 10, 5);
    public static final int SLOT_EDGE = rgb(80, 50, 24);
    public static final int BTN_PRIMARY_BG = rgb(44, 110, 100);
    public static final int BTN_PRIMARY_TEXT = rgb(14, 30, 26);
    public static final int BTN_MAP_BG = rgb(26, 44, 60);
    public static final int SEP = rgb(90, 56, 26);
    public static final int SEP_DARK = rgb(70, 44, 24);
    public static final int SEND_YELLOW = rgb(255, 233, 74);
    public static final int RX_BLUE = rgb(136, 204, 255);

    private TabletMockupPalette() {
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
