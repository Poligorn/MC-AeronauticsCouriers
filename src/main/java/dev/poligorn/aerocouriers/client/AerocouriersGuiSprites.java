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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerocouriers", path);
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace("widget/" + path);
    }
}
