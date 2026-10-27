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
    public static final ModConfigSpec.BooleanValue NOTIFICATION_SOUNDS;
    public static final ModConfigSpec.BooleanValue SOUNDS_ENABLED;
    public static final ModConfigSpec.DoubleValue SOUNDS_VOLUME;

    public static final ModConfigSpec.EnumValue<WaypointSymbolMode> WAYPOINT_SYMBOL_MODE;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_WAYPOINT_SYMBOL;

    public static final ModConfigSpec.BooleanValue NAV_BEAM_ENABLED;
    public static final ModConfigSpec.DoubleValue NAV_BEAM_HEIGHT;
    public static final ModConfigSpec.IntValue NAV_BEAM_RENDER_DISTANCE;
    public static final ModConfigSpec.DoubleValue NAV_BEAM_FADE_START;
    public static final ModConfigSpec.ConfigValue<String> NAV_BEAM_COLOR;

    public static final ModConfigSpec.BooleanValue NAV_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<HudCorner> NAV_HUD_CORNER;
    public static final ModConfigSpec.IntValue NAV_HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue NAV_HUD_OFFSET_Y;

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
        NOTIFICATION_SOUNDS = b.comment("Play a sound when a new order notification arrives")
                .define("notificationSounds", true);
        b.pop();

        b.push("sounds");
        SOUNDS_ENABLED = b.comment("Tablet UI and order accept/deny feedback sounds")
                .define("sounds_enabled", true);
        SOUNDS_VOLUME = b.comment("Volume multiplier for couriers UI feedback sounds")
                .defineInRange("sounds_volume", 1.0, 0.0, 2.0);
        b.pop();

        b.push("waypoints");
        WAYPOINT_SYMBOL_MODE = b.comment(
                        "How order waypoints look on Xaero's minimap when this mod places them.",
                        "ICON — crate (📦) Unicode glyph for every order waypoint;",
                        "FIRST_LETTER — first character of the order label;",
                        "CUSTOM — customWaypointSymbol")
                .defineEnum("waypointSymbolMode", WaypointSymbolMode.ICON);
        CUSTOM_WAYPOINT_SYMBOL = b.comment("Single-character or emoji marker when waypointSymbolMode is CUSTOM")
                .define("customWaypointSymbol", "\u2713");
        b.pop();

        b.push("navigation");
        NAV_BEAM_ENABLED = b.comment("Vertical beacon beam at pickup/delivery for your active hauls")
                .define("beam_enabled", true);
        NAV_BEAM_HEIGHT = b.comment("Beam height in blocks").defineInRange("beam_height", 256.0, 16.0, 512.0);
        NAV_BEAM_RENDER_DISTANCE = b.comment("Do not draw the beam farther than this (blocks)")
                .defineInRange("beam_render_distance", 512, 32, 2048);
        NAV_BEAM_FADE_START = b.comment("Full opacity until this distance; fades when closer (blocks)")
                .defineInRange("beam_fade_start", 12.0, 2.0, 64.0);
        NAV_BEAM_COLOR = b.comment("Beam RGB as \"r,g,b\" (0–1), default gold")
                .define("beam_color", "1.0,0.75,0.2");
        NAV_HUD_ENABLED = b.comment("Compass HUD with distance and order id").define("hud_enabled", true);
        NAV_HUD_CORNER = b.comment("Screen corner for the navigation HUD (offsets apply inward from that corner)")
                .defineEnum("hud_corner", HudCorner.TOP_LEFT);
        NAV_HUD_OFFSET_X = b.defineInRange("hud_offset_x", 8, 0, 4000);
        NAV_HUD_OFFSET_Y = b.defineInRange("hud_offset_y", 8, 0, 4000);
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

    public static boolean notificationSoundsEnabled() {
        try {
            return NOTIFICATION_SOUNDS.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static boolean couriersSoundsEnabled() {
        try {
            return SOUNDS_ENABLED.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static float couriersSoundsVolume() {
        try {
            return SOUNDS_VOLUME.get().floatValue();
        } catch (IllegalStateException ignored) {
            return 1.0F;
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

    public enum HudCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public static boolean navBeamEnabled() {
        try {
            return NAV_BEAM_ENABLED.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static boolean navHudEnabled() {
        try {
            return NAV_HUD_ENABLED.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static float[] navBeamColorRgb() {
        try {
            String raw = NAV_BEAM_COLOR.get();
            if (raw == null) {
                return new float[] {1f, 0.75f, 0.2f};
            }
            String[] parts = raw.split(",");
            if (parts.length < 3) {
                return new float[] {1f, 0.75f, 0.2f};
            }
            return new float[] {
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim())
            };
        } catch (IllegalStateException | NumberFormatException ignored) {
            return new float[] {1f, 0.75f, 0.2f};
        }
    }

    /** Top-left of the HUD box in scaled GUI pixels. */
    public static int[] navHudPosition(int screenW, int screenH) {
        int boxW = 118;
        int boxH = 42;
        int ox = safeInt(NAV_HUD_OFFSET_X, 8);
        int oy = safeInt(NAV_HUD_OFFSET_Y, 8);
        HudCorner corner;
        try {
            corner = NAV_HUD_CORNER.get();
        } catch (IllegalStateException ignored) {
            corner = HudCorner.TOP_LEFT;
        }
        return switch (corner) {
            case TOP_LEFT -> new int[] {ox, oy};
            case TOP_RIGHT -> new int[] {screenW - boxW - ox, oy};
            case BOTTOM_LEFT -> new int[] {ox, screenH - boxH - oy};
            case BOTTOM_RIGHT -> new int[] {screenW - boxW - ox, screenH - boxH - oy};
        };
    }

    private static int safeInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private CouriersClientConfig() {
    }
}
