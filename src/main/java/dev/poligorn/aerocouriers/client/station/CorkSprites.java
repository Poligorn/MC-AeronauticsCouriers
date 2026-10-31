package dev.poligorn.aerocouriers.client.station;

import net.minecraft.resources.ResourceLocation;

/** GUI sprite ids under {@code textures/gui/sprites/cork/} (cork_board_assets_v2, 1:1 GUI px). */
public final class CorkSprites {
    public static final ResourceLocation FRAME_FULL = id("frame_full_640x360");
    public static final ResourceLocation FRAME_COMPACT = id("frame_full_compact_480x270");
    public static final ResourceLocation FRAME_9 = id("frame_9slice_48x48");
    public static final ResourceLocation FRAME_9_COMPACT = id("frame_9slice_compact_40x40");
    public static final ResourceLocation CORK_AREA = id("cork_area_604x243");
    public static final ResourceLocation CORK_AREA_COMPACT = id("cork_area_compact_458x180");
    public static final ResourceLocation CORK_TILE = id("cork_tile_64x64");
    public static final ResourceLocation CORK_VIGNETTE = id("cork_vignette_9slice_16x16");
    public static final ResourceLocation WOOD_DARK_H = id("wood_dark_tile_h_64x32");
    public static final ResourceLocation WOOD_MID_H = id("wood_mid_tile_h_64x32");
    public static final ResourceLocation ADMIN_BG = id("admin_bg_tile_8x8");
    public static final ResourceLocation ADMIN_SECTION = id("admin_section_frame_32x32");
    public static final ResourceLocation DIALOG = id("dialog_frame_32x32");
    public static final ResourceLocation NAMEPLATE = id("nameplate_brass_24x23");
    public static final ResourceLocation HOUSE_BOX = id("house_icon_box_17x13");
    public static final ResourceLocation CHIP_DARK = id("chip_dark_12x13");
    public static final ResourceLocation REP_TRACK = id("rep_bar_track_24x7");
    public static final ResourceLocation BANNER = id("banner_warning_24x16");
    public static final ResourceLocation GLASS = id("glass_overlay_604x243");
    public static final ResourceLocation EMPTY_MAIL = id("mailbox_empty_28x40");
    public static final ResourceLocation SCROLL_TRACK = id("scrollbar_track_6x32");
    public static final ResourceLocation SCROLL_THUMB = id("scrollbar_thumb_4x24");
    public static final ResourceLocation CHIP = id("filter_chip_normal_40x14");
    public static final ResourceLocation CHIP_ON = id("filter_chip_active_40x14");
    public static final ResourceLocation BTN = id("button_normal_24x14");
    public static final ResourceLocation BTN_HOVER = id("button_hover_24x14");
    public static final ResourceLocation BTN_OFF = id("button_disabled_24x14");
    public static final ResourceLocation BTN_DANGER = id("button_danger_24x14");
    public static final ResourceLocation BTN_GOLD = id("button_gold_24x14");
    public static final ResourceLocation BTN_TEAL = id("button_teal_24x14");
    public static final ResourceLocation BTN_SEL = id("button_selected_24x14");
    public static final ResourceLocation INPUT = id("text_input_normal_12x14");
    public static final ResourceLocation INPUT_FOCUS = id("text_input_focus_12x14");
    public static final ResourceLocation INPUT_ERR = id("text_input_error_12x14");
    public static final ResourceLocation TOGGLE_ON = id("toggle_on_18x9");
    public static final ResourceLocation TOGGLE_OFF = id("toggle_off_18x9");
    public static final ResourceLocation CHECK_ON = id("checkbox_on_9x9");
    public static final ResourceLocation CHECK_OFF = id("checkbox_off_9x9");
    public static final ResourceLocation PIN_SILVER = id("pin_silver_7x7");
    public static final ResourceLocation PIN_GOLD = id("pin_gold_7x7");
    public static final ResourceLocation PIN_PINK = id("pin_pink_7x7");
    public static final ResourceLocation PIN_GREY = id("pin_grey_locked_7x7");
    public static final ResourceLocation WAX = id("wax_seal_17x17");
    public static final ResourceLocation RIBBON = id("ribbon_gem_11x26");
    public static final ResourceLocation SHADOW = id("note_shadow_4x4");
    public static final ResourceLocation HOVER = id("note_border_hover_overlay_138x110");
    public static final ResourceLocation BORDER_EXPIRE = id("note_border_expiring_overlay_136x108");
    public static final ResourceLocation BORDER_DOING = id("note_border_doing_overlay_136x108");
    public static final ResourceLocation DOING_STAMP = id("stamp_doing_frame_120x22");
    public static final ResourceLocation BADGE_EXP = id("badge_expensive_bg_12x11");
    public static final ResourceLocation BADGE_RARE = id("badge_rare_bg_12x11");
    public static final ResourceLocation BADGE_EXPIRE = id("badge_expiring_bg_12x11");
    public static final ResourceLocation BADGE_EXP_ICON = id("badge_expensive_iconchip_15x11");
    public static final ResourceLocation BADGE_RARE_ICON = id("badge_rare_iconchip_15x11");
    public static final ResourceLocation EMPTY_PAPER = id("note_paper_normal_280x118");
    public static final ResourceLocation TOOLTIP = id("tooltip_frame_12x12");
    public static final ResourceLocation ICON_HOUSE = icon("house_7x7");
    public static final ResourceLocation ICON_FACE = icon("face_7x7");
    public static final ResourceLocation ICON_BOX = icon("box_9x7");
    public static final ResourceLocation ICON_CLOCK = icon("clock_9x8");
    public static final ResourceLocation ICON_LOCK = icon("lock_7x7");
    public static final ResourceLocation ICON_WARN = icon("warn_7x6");
    public static final ResourceLocation ICON_STAR = icon("star_9x9");
    public static final ResourceLocation ICON_ARROW = icon("arrow_7x5");
    public static final ResourceLocation ICON_GEM = icon("gem_7x6");
    public static final ResourceLocation ICON_TABLET = icon("tablet_9x7");
    public static final ResourceLocation ICON_CHECK = icon("check_7x5");
    public static final ResourceLocation ICON_CROSS = icon("cross_5x5");
    public static final ResourceLocation ICON_GEAR = icon("gear_7x7");
    public static final ResourceLocation ICON_PLUS = icon("plus_5x5");
    public static final ResourceLocation ICON_MINUS = icon("minus_5x5");
    public static final ResourceLocation ICON_TRASH = icon("trash_7x6");
    public static final ResourceLocation ICON_REFRESH = icon("refresh_7x6");
    public static final ResourceLocation ICON_PENCIL = icon("pencil_8x8");
    public static final ResourceLocation ICON_SORT = icon("sort_updown_5x8");

    private CorkSprites() {
    }

    public static ResourceLocation paper(CorkNoteStyle style, boolean compact) {
        String suffix = compact ? "_compact_136x84" : "_136x108";
        if (style.locked()) {
            return id("note_paper_locked" + suffix);
        }
        if (style.expensive() && style.rare()) {
            return id("note_paper_expensive_rare" + suffix);
        }
        if (style.expensive()) {
            return id("note_paper_expensive" + suffix);
        }
        if (style.rare()) {
            return id("note_paper_rare" + suffix);
        }
        return id("note_paper_normal" + suffix);
    }

    public static ResourceLocation crate(String size) {
        return switch (size.toLowerCase()) {
            case "s" -> id("crate_s_52x52");
            case "m" -> id("crate_m_52x52");
            case "l" -> id("crate_l_52x52");
            case "xl" -> id("crate_xl_52x52");
            default -> id("crate_blank_52x52");
        };
    }

    public static ResourceLocation stamp(String size, boolean filled) {
        String letter = size.toLowerCase();
        if (!letter.equals("s") && !letter.equals("m") && !letter.equals("l") && !letter.equals("xl")) {
            letter = "s";
        }
        return id("stamp_" + letter + (filled ? "_filled_17x15" : "_outline_17x15"));
    }

    public static ResourceLocation pin(CorkNoteStyle style) {
        if (style.locked()) {
            return PIN_GREY;
        }
        if (style.expensive()) {
            return PIN_GOLD;
        }
        if (style.rare()) {
            return PIN_PINK;
        }
        return PIN_SILVER;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("aerocouriers", "cork/" + name);
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath("aerocouriers", "cork/icons/" + name);
    }
}
