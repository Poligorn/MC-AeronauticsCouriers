package dev.poligorn.aerocouriers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code aerocouriers-client.toml} (GDD §10.1, §10.3). {@code [button]} places the inventory button;
 * {@code [zones]} drives the zone outline particles.
 */
public final class CouriersClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<ButtonPosition> BUTTON_POSITION;
    public static final ModConfigSpec.IntValue BUTTON_CUSTOM_X;
    public static final ModConfigSpec.IntValue BUTTON_CUSTOM_Y;

    public static final ModConfigSpec.BooleanValue ZONES_ENABLED;
    public static final ModConfigSpec.DoubleValue ZONES_PARTICLE_DENSITY;
    public static final ModConfigSpec.IntValue ZONES_RENDER_DISTANCE;

    public static final ModConfigSpec.BooleanValue USE_VANILLA_SPRITES;

    public static final ModConfigSpec.EnumValue<WaypointSymbolMode> WAYPOINT_SYMBOL_MODE;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_WAYPOINT_SYMBOL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("button");
        BUTTON_POSITION = b.comment(
                        "CORNER - bottom-left corner of the screen; INVENTORY_SIDE - left of the inventory window;",
                        "CUSTOM - custom_x/custom_y; HIDDEN - no button (key binding only)")
                .defineEnum("position", ButtonPosition.INVENTORY_SIDE);
        BUTTON_CUSTOM_X = b.comment("Only for CUSTOM").defineInRange("custom_x", 2, 0, 4000);
        BUTTON_CUSTOM_Y = b.comment("Only for CUSTOM").defineInRange("custom_y", 2, 0, 4000);
        b.pop();

        b.push("zones");
        ZONES_ENABLED = b.comment("Show the pickup/delivery zones of your own contracts as particle outlines")
                .define("enabled", true);
        ZONES_PARTICLE_DENSITY = b.comment("Particles per block of outline")
                .defineInRange("particle_density", 1.0, 0.05, 8.0);
        ZONES_RENDER_DISTANCE = b.comment("Zones farther than this are not drawn, blocks")
                .defineInRange("render_distance", 128, 8, 1024);
        b.pop();

        b.push("gui");
        USE_VANILLA_SPRITES = b.comment(
                        "Use minecraft:widget/* sprites for buttons and tabs (e.g. when Create Style Interface is installed)")
                .define("useVanillaSprites", false);
        b.pop();

        b.push("waypoints");
        WAYPOINT_SYMBOL_MODE = b.comment(
                        "How order waypoints look on Xaero's minimap when this mod places them.",
                        "ICON — crate (delivery) / truck (pickup) Unicode glyphs;",
                        "FIRST_LETTER — first character of the order label;",
                        "CUSTOM — customWaypointSymbol")
                .defineEnum("waypointSymbolMode", WaypointSymbolMode.ICON);
        CUSTOM_WAYPOINT_SYMBOL = b.comment("Single-character or emoji marker when waypointSymbolMode is CUSTOM")
                .define("customWaypointSymbol", "\u2713");
        b.pop();

        SPEC = b.build();
    }

    /** Client-only; safe when config is not loaded yet (defaults to mod sprites). */
    public static boolean useVanillaSprites() {
        try {
            return USE_VANILLA_SPRITES.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static WaypointSymbolMode waypointSymbolMode() {
        try {
            return WAYPOINT_SYMBOL_MODE.get();
        } catch (IllegalStateException ignored) {
            return WaypointSymbolMode.ICON;
        }
    }

    public static String customWaypointSymbol() {
        try {
            String raw = CUSTOM_WAYPOINT_SYMBOL.get();
            return raw == null || raw.isEmpty() ? "?" : raw.substring(0, Math.min(2, raw.length()));
        } catch (IllegalStateException ignored) {
            return "?";
        }
    }

    public enum ButtonPosition {
        CORNER,
        INVENTORY_SIDE,
        CUSTOM,
        HIDDEN
    }

    private CouriersClientConfig() {
    }
}
