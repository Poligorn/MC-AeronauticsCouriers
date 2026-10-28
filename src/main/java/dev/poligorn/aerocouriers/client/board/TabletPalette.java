package dev.poligorn.aerocouriers.client.board;

/** Approved tablet mockup palette (GUI scale 3, 640×360): colour carries meaning. */
public final class TabletPalette {
    public static final int TXT = 0xFFF2D6A2;
    public static final int SEC = 0xFFC49A68;
    public static final int GOLD = 0xFFF0C45A;
    public static final int CORAL = 0xFFEC8068;
    public static final int TEAL = 0xFF6EC4B6;
    public static final int BLUE = 0xFF78BAEC;
    public static final int LOCKC = 0xFF96826E;

    public static final int CARD = 0xFF341E10;
    public static final int CARD_SEL = 0xFF4A2C16;
    public static final int CARD_LOCKED = 0xFF281A10;
    public static final int CARD_BORDER = 0xFF462C18;
    public static final int CARD_BORDER_SEL = 0xFF6E4822;
    public static final int BRASS = 0xFF965624;
    public static final int BRASS_LIGHT = 0xFFC87D32;
    public static final int BRASS_DIM = 0xFF6E461E;
    public static final int LIST_BG = 0xFF1E0F07;
    public static final int CHIP_BG = 0xFF24140A;
    public static final int TAB_BG = 0xFF160A05;

    public static final int OUTLINE = 0xFF261408;
    public static final int PANEL_BG = 0xFF1E1008;
    public static final int PANEL_HEADER = 0xFF3C2412;
    public static final int BEVEL_LIGHT = 0xFFBE8C3C;
    public static final int BEVEL_DARK = 0xFF644018;
    public static final int DIVIDER = 0xFF5A381A;
    public static final int DIVIDER_SOFT = 0xFF462C18;
    public static final int PREVIEW_BG = 0xFF160C06;
    public static final int SLOT_BG = 0xFF140A05;
    public static final int SLOT_BORDER = 0xFF503218;
    public static final int SLOT_SHADOW = 0xFF0A0502;

    public static final int CLOSE_BG = 0xFF341810;
    public static final int RISK_BG = 0xFF341810;
    public static final int NEW_BG = 0xFF1A3C36;
    public static final int WARN_BG = 0xFF461E16;
    public static final int PRIMARY_BG = 0xFF2C6E64;
    public static final int PRIMARY_TEXT = 0xFF0E1E1A;
    public static final int MAP_BG = 0xFF1A2C3C;
    public static final int DISABLED_BG = 0xFF2E2218;
    public static final int DISABLED_BORDER = 0xFF564638;
    public static final int TOOLTIP_BG = 0xFF16061C;
    public static final int TOOLTIP_BORDER = 0xFF582C84;
    /** Board dimming behind overlays (≈150/255 black, as in the mockup). */
    public static final int SCRIM = 0x96000000;

    private TabletPalette() {
    }

    public static int hoverBrighten(int argb) {
        int a = argb >>> 24;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 18);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 14);
        int b = Math.min(255, (argb & 0xFF) + 10);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
