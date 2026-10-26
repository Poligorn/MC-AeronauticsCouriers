package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

/** GUI sprite ids under {@code assets/aerocouriers/textures/gui/sprites/}. */
public final class AerocouriersGuiSprites {
    public static final ResourceLocation PANEL = id("panel");
    public static final ResourceLocation INSET = id("inset");
    public static final ResourceLocation TITLE_LABEL = id("title_label");
    public static final ResourceLocation TEXT_FIELD = id("text_field");
    public static final ResourceLocation TEXT_FIELD_FOCUSED = id("text_field_highlighted");
    public static final ResourceLocation SCROLLER = id("scroller");
    public static final ResourceLocation SCROLLER_TRACK = id("scroller_track");
    public static final ResourceLocation CONTAINER_SLOT = id("container_slot");
    public static final ResourceLocation ENVELOPE = id("icon/envelope");
    public static final ResourceLocation PAPER_SHEET = id("paper_sheet");
    public static final ResourceLocation PAPER_FIELD = id("paper_field");
    public static final ResourceLocation PAPER_FIELD_FOCUSED = id("paper_field_highlighted");
    public static final ResourceLocation PAPER_SLOT = id("paper_slot");
    public static final ResourceLocation CLIPBOARD_CLIP = id("clipboard_clip");
    public static final ResourceLocation TABLET_FRAME = id("tablet_frame");
    public static final ResourceLocation TABLET_GRID = id("tablet_grid");
    public static final ResourceLocation TABLET_LIST_ROW = id("tablet_list_row");
    public static final ResourceLocation TABLET_SCROLLER = id("tablet_scroller");
    public static final ResourceLocation TABLET_SCROLLER_TRACK = id("tablet_scroller_track");
    public static final ResourceLocation TABLET_DECOR_ANTENNA = id("tablet_decor_antenna");
    public static final ResourceLocation ENVELOPE_TABLET = id("icon/envelope_tablet");
    public static final ResourceLocation BELL = id("icon/bell");
    public static final ResourceLocation BELL_TABLET = id("icon/bell_tablet");
    public static final ResourceLocation ICON_MAP_PIN = id("icon/map_pin_tablet");
    public static final ResourceLocation ICON_CRATE = id("icon/crate_tablet");
    public static final ResourceLocation ICON_COIN = id("icon/coin_tablet");
    public static final ResourceLocation ICON_LOCK = id("icon/lock_tablet");
    public static final ResourceLocation ICON_CLOCK = id("icon/clock_tablet");
    public static final ResourceLocation ICON_STAR = id("icon/star_badge_tablet");
    public static final ResourceLocation ICON_VILLAGE = id("icon/village_tablet");

    private static final WidgetSprites VANILLA_BUTTON = new WidgetSprites(
            vanilla("button"),
            vanilla("button_disabled"),
            vanilla("button_highlighted"),
            vanilla("button_disabled"));
    private static final WidgetSprites MOD_BUTTON = new WidgetSprites(
            id("button"),
            id("button_disabled"),
            id("button_highlighted"),
            id("button_disabled"));
    private static final WidgetSprites VANILLA_TAB = new WidgetSprites(
            vanilla("tab"),
            vanilla("tab"),
            vanilla("tab_highlighted"),
            vanilla("tab_highlighted"));
    private static final WidgetSprites MOD_TAB = new WidgetSprites(
            id("tab"),
            id("tab"),
            id("tab_highlighted"),
            id("tab_highlighted"));
    private static final WidgetSprites VANILLA_TAB_SELECTED = new WidgetSprites(
            vanilla("tab_selected"),
            vanilla("tab"),
            vanilla("tab_selected_highlighted"),
            vanilla("tab_highlighted"));
    private static final WidgetSprites MOD_TAB_SELECTED = new WidgetSprites(
            id("tab_selected"),
            id("tab"),
            id("tab_highlighted"),
            id("tab_highlighted"));
    private static final WidgetSprites TABLET_BUTTON = new WidgetSprites(
            id("tablet_button"),
            id("tablet_button_disabled"),
            id("tablet_button_highlighted"),
            id("tablet_button_disabled"));
    private static final WidgetSprites TABLET_TAB = new WidgetSprites(
            id("tablet_tab"),
            id("tablet_tab"),
            id("tablet_tab_highlighted"),
            id("tablet_tab_highlighted"));
    private static final WidgetSprites TABLET_TAB_SELECTED = new WidgetSprites(
            id("tablet_tab_selected"),
            id("tablet_tab"),
            id("tablet_tab_highlighted"),
            id("tablet_tab_highlighted"));

    private AerocouriersGuiSprites() {
    }

    public static WidgetSprites buttonSprites() {
        return CouriersClientConfig.useVanillaSprites() ? VANILLA_BUTTON : MOD_BUTTON;
    }

    public static WidgetSprites tabSprites(boolean selected) {
        if (CouriersClientConfig.useVanillaSprites()) {
            return selected ? VANILLA_TAB_SELECTED : VANILLA_TAB;
        }
        return selected ? MOD_TAB_SELECTED : MOD_TAB;
    }

    public static WidgetSprites tabletButtonSprites() {
        return TABLET_BUTTON;
    }

    public static WidgetSprites tabletTabSprites(boolean selected) {
        return selected ? TABLET_TAB_SELECTED : TABLET_TAB;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerocouriers", path);
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace("widget/" + path);
    }
}
