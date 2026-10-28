package dev.poligorn.aerocouriers.client.board;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Compact filter-row glyphs (default Minecraft font); ASCII fallbacks when missing. */
public final class TabletFilterGlyphs {
    private static final String SIZE_PREF = "\u25A3";
    private static final String SIZE_FALLBACK = "#";
    private static final String RANK_PREF = "\u2605";
    private static final String RANK_FALLBACK = "*";
    private static final String TYPE_VILLAGE_PREF = "\u2302";
    private static final String TYPE_VILLAGE_FALLBACK = "V";
    private static final String TYPE_PLAYER_PREF = "\u263A";
    private static final String TYPE_PLAYER_FALLBACK = "P";
    private static final String TYPE_ALL_PREF = "\u25CB";
    private static final String TYPE_ALL_FALLBACK = "o";
    private static final String TYPE_RARE_PREF = "\u2726";
    private static final String TYPE_RARE_FALLBACK = "+";
    private static final String STATUS_PREF = "\u25F7";
    private static final String STATUS_FALLBACK = "o";
    private static final String ROLE_PREF = "\u2691";
    private static final String ROLE_FALLBACK = "!";
    private static final String SORT_PREF = "\u21C5";
    private static final String SORT_FALLBACK = "^";

    private TabletFilterGlyphs() {
    }

    public static String size(Font font) {
        return pick(font, SIZE_PREF, SIZE_FALLBACK);
    }

    public static String rank(Font font) {
        return pick(font, RANK_PREF, RANK_FALLBACK);
    }

    public static String typeForBoard(Font font, BoardContractKind kind) {
        return switch (kind) {
            case VILLAGE -> pick(font, TYPE_VILLAGE_PREF, TYPE_VILLAGE_FALLBACK);
            case PLAYER -> pick(font, TYPE_PLAYER_PREF, TYPE_PLAYER_FALLBACK);
            case RARE -> pick(font, TYPE_RARE_PREF, TYPE_RARE_FALLBACK);
            case ALL -> pick(font, TYPE_ALL_PREF, TYPE_ALL_FALLBACK);
        };
    }

    public static String typeForAdmin(Font font, AdminContractKind kind) {
        return switch (kind) {
            case NPC -> pick(font, TYPE_VILLAGE_PREF, TYPE_VILLAGE_FALLBACK);
            case P2P -> pick(font, TYPE_PLAYER_PREF, TYPE_PLAYER_FALLBACK);
            case ALL -> pick(font, TYPE_ALL_PREF, TYPE_ALL_FALLBACK);
        };
    }

    public static String status(Font font) {
        return pick(font, STATUS_PREF, STATUS_FALLBACK);
    }

    public static String role(Font font) {
        return pick(font, ROLE_PREF, ROLE_FALLBACK);
    }

    public static String sort(Font font) {
        return pick(font, SORT_PREF, SORT_FALLBACK);
    }

    public static Component label(String glyph, Component value) {
        return Component.literal(glyph + " ").append(value);
    }

    public static Component label(String glyph, String value) {
        return Component.literal(glyph + " " + value);
    }

    public static Component tooltip(String filterNameKey, Component value) {
        return Component.translatable("ui.aerocouriers.filters.tooltip",
                Component.translatable(filterNameKey),
                value);
    }

    private static String pick(Font font, String preferred, String fallback) {
        if (font.width(preferred) > 0) {
            return preferred;
        }
        return fallback;
    }
}
