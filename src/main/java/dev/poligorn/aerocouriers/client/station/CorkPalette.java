package dev.poligorn.aerocouriers.client.station;

/** Text and chrome colours from cork_board_assets_v2/palette.json (final WCAG set, not mockup leftovers). */
public final class CorkPalette {
    public static final int HEADER_TITLE = rgb(0xF0C45A);
    public static final int HEADER_SUBTITLE = rgb(0xC49A68);
    public static final int HEADER_TEXT = rgb(0xF2D6A2);
    public static final int HEADER_MUTED = rgb(0xC49A68);
    public static final int REPUTATION = rgb(0x6EC4B6);
    public static final int TIMER = rgb(0x78BAEC);
    public static final int FILTER = rgb(0xF2D6A2);
    public static final int FILTER_ACTIVE = rgb(0xFFDE82);
    public static final int FOOTER = rgb(0xF2D6A2);
    public static final int FOOTER_HINT = rgb(0xC4AC8E);
    public static final int BANNER = rgb(0xF2D6A2);
    public static final int BANNER_ICON = rgb(0xF0C45A);
    public static final int BUTTON = rgb(0xF2D6A2);
    public static final int BUTTON_GOLD = rgb(0xF0C45A);
    public static final int BUTTON_DANGER = rgb(0xEC8068);
    public static final int BUTTON_TEAL = rgb(0x0E2C26);
    public static final int BUTTON_DISABLED = rgb(0xB09C84);
    public static final int ADMIN_TITLE = rgb(0xF0C45A);
    public static final int ADMIN_LABEL = rgb(0xC49A68);
    public static final int ADMIN_VALUE = rgb(0xF2D6A2);
    public static final int ADMIN_COMMAND = rgb(0x96C8EC);
    public static final int ADMIN_HINT = rgb(0xBEA88C);
    public static final int ADMIN_OP = rgb(0xEC8068);
    public static final int ADMIN_OK = rgb(0x6EC4B6);
    public static final int ADMIN_ERROR = rgb(0xEC8068);
    public static final int INPUT = rgb(0xF2D6A2);
    public static final int GOLD = rgb(0xF0C45A);
    public static final int TEAL = rgb(0x6EC4B6);
    public static final int TEAL_FILL = rgb(0x3C9682);
    public static final int TEAL_HI = rgb(0xAAF0E2);
    public static final int BAR_TICK = rgb(0xECD6A0);
    public static final int SHADOW = 0x601E0E04;
    public static final int SHADOW_HOVER = 0x781E0E04;
    public static final int SHADOW_FAR = 0x281E0E04;

    public static final int NOTE_INK = rgb(0x3A2210);
    public static final int NOTE_MUTED = rgb(0x745538);
    public static final int NOTE_REWARD = rgb(0x613800);
    public static final int NOTE_DEPOSIT = rgb(0xA43624);
    public static final int EXP_INK = rgb(0x3E2006);
    public static final int EXP_MUTED = rgb(0x80541A);
    public static final int EXP_REWARD = rgb(0x703200);
    public static final int EXP_DEPOSIT = rgb(0xA83422);
    public static final int RARE_INK = rgb(0x3E1434);
    public static final int RARE_MUTED = rgb(0x82466E);
    public static final int RARE_REWARD = rgb(0x682E00);
    public static final int RARE_DEPOSIT = rgb(0xA4323A);
    public static final int BLEND_INK = rgb(0x3E1C18);
    public static final int BLEND_MUTED = rgb(0x805038);
    public static final int LOCK_INK = rgb(0x3B3733);
    public static final int LOCK_MUTED = rgb(0x3A3733);
    public static final int EXPIRE_TIMER = rgb(0xB2241C);
    public static final int EXPIRE_BAR = rgb(0xE64632);
    public static final int EXPIRE_TRACK = rgb(0x783828);
    public static final int DOING_TITLE = rgb(0x11584F);
    public static final int DOING_NICK = rgb(0x0E4640);
    public static final int BADGE_EXP = rgb(0xFFE190);
    public static final int BADGE_RARE = rgb(0xFFDEF0);
    public static final int BADGE_EXPIRE = rgb(0xFFE2C8);
    public static final int SHEET_OK = rgb(0x1A683C);

    private CorkPalette() {
    }

    public static int rgb(int hex) {
        return 0xFF000000 | (hex & 0xFFFFFF);
    }

    public static int ink(CorkNoteStyle style) {
        if (style.locked()) {
            return LOCK_INK;
        }
        if (style.expensive() && style.rare()) {
            return BLEND_INK;
        }
        if (style.expensive()) {
            return EXP_INK;
        }
        if (style.rare()) {
            return RARE_INK;
        }
        return NOTE_INK;
    }

    public static int muted(CorkNoteStyle style) {
        if (style.locked()) {
            return LOCK_MUTED;
        }
        if (style.expensive() && style.rare()) {
            return BLEND_MUTED;
        }
        if (style.expensive()) {
            return EXP_MUTED;
        }
        if (style.rare()) {
            return RARE_MUTED;
        }
        return NOTE_MUTED;
    }

    public static int reward(CorkNoteStyle style) {
        if (style.locked()) {
            return LOCK_INK;
        }
        if (style.expensive() || (style.expensive() && style.rare())) {
            return EXP_REWARD;
        }
        if (style.rare()) {
            return RARE_REWARD;
        }
        return NOTE_REWARD;
    }

    public static int deposit(CorkNoteStyle style) {
        if (style.locked()) {
            return LOCK_INK;
        }
        if (style.expensive()) {
            return EXP_DEPOSIT;
        }
        if (style.rare()) {
            return RARE_DEPOSIT;
        }
        return NOTE_DEPOSIT;
    }
}
