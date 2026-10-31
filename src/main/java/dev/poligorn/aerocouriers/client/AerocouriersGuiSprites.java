package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

/** GUI sprite ids under {@code assets/aerocouriers/textures/gui/sprites/}. */
public final class AerocouriersGuiSprites {
    public static final ResourceLocation TABLET_FRAME = id("tablet_frame");
    public static final ResourceLocation TABLET_DECOR_ANTENNA = id("tablet_decor_antenna");
    public static final ResourceLocation TABLET_LOADING_GEAR = id("tablet_loading_gear");
    public static final ResourceLocation ICON_MAP_PIN = id("icon/map_pin_tablet");
    public static final ResourceLocation ICON_CRATE = id("icon/crate_tablet");
    public static final ResourceLocation ICON_LOCK = id("icon/lock_tablet");
    public static final ResourceLocation ICON_CLOCK = id("icon/clock_tablet");
    public static final ResourceLocation ICON_STAR = id("icon/star_badge_tablet");
    public static final ResourceLocation ICON_VILLAGE = id("icon/village_tablet");
    public static final ResourceLocation ICON_RARE_CARGO = id("icon/rare_cargo_tablet");
    /** Flat gray control. Inactive route settings use this instead of the brass "pressed" disabled sprite. */
    public static final ResourceLocation TABLET_BUTTON_INACTIVE = id("tablet_button_inactive");

    public static final ResourceLocation CORK_FRAME = id("cork/frame");
    public static final ResourceLocation CORK_HEADER = id("cork/header");
    public static final ResourceLocation CORK_TILE = id("cork/tile");
    public static final ResourceLocation CORK_INTERIOR = id("cork/interior");
    public static final ResourceLocation CORK_PAPER = id("cork/paper");
    public static final ResourceLocation CORK_PAPER_GOLD = id("cork/paper_gold");
    public static final ResourceLocation CORK_PAPER_PINK = id("cork/paper_pink");
    public static final ResourceLocation CORK_PAPER_BLEND = id("cork/paper_blend");
    public static final ResourceLocation CORK_PAPER_LOCKED = id("cork/paper_locked");
    public static final ResourceLocation CORK_PIN_RED = id("cork/pin_red");
    public static final ResourceLocation CORK_PIN_TEAL = id("cork/pin_teal");
    public static final ResourceLocation CORK_PIN_BRASS = id("cork/pin_brass");
    public static final ResourceLocation CORK_PIN_PINK = id("cork/pin_pink");
    public static final ResourceLocation CORK_WAX = id("cork/wax");
    public static final ResourceLocation CORK_RIBBON = id("cork/ribbon");
    public static final ResourceLocation CORK_LOCK = id("cork/lock");
    public static final ResourceLocation CORK_STAMP = id("cork/stamp");
    public static final ResourceLocation CORK_PROGRESS = id("cork/progress");
    public static final ResourceLocation CORK_PANEL = id("cork/panel");
    public static final ResourceLocation CORK_CHIP = id("cork/chip");
    public static final ResourceLocation CORK_BUTTON = id("cork/button");
    public static final ResourceLocation CORK_BUTTON_HOVER = id("cork/button_hover");
    public static final ResourceLocation CORK_BUTTON_DANGER = id("cork/button_danger");
    public static final ResourceLocation CORK_BUTTON_DANGER_HOVER = id("cork/button_danger_hover");
    public static final ResourceLocation CORK_SWITCH_ON = id("cork/switch_on");
    public static final ResourceLocation CORK_SWITCH_OFF = id("cork/switch_off");

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
    private static final WidgetSprites VANILLA_TAB_SELECTED = new WidgetSprites(
            vanilla("tab_selected"),
            vanilla("tab"),
            vanilla("tab_selected_highlighted"),
            vanilla("tab_highlighted"));
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

    /** Vanilla widget tabs. The tablet uses {@link #tabletTabSprites}. */
    public static WidgetSprites tabSprites(boolean selected) {
        return selected ? VANILLA_TAB_SELECTED : VANILLA_TAB;
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
