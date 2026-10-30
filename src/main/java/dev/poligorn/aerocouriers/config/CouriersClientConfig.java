package dev.poligorn.aerocouriers.config;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code aerocouriers-client.toml} (GDD §10.1, §10.3). {@code [button]} places the inventory button;
 * {@code [zones]} drives the zone outline particles; {@code [route]} the route line on Xaero's maps.
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
    public static final ModConfigSpec.BooleanValue TABLET_GRID_ANIMATION;
    public static final ModConfigSpec.DoubleValue TABLET_GRID_SPEED;
    public static final ModConfigSpec.BooleanValue UI_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue NOTIFICATION_SOUNDS;
    public static final ModConfigSpec.BooleanValue SOUNDS_ENABLED;
    public static final ModConfigSpec.DoubleValue SOUNDS_VOLUME;

    public static final ModConfigSpec.EnumValue<WaypointSymbolMode> WAYPOINT_SYMBOL_MODE;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_WAYPOINT_SYMBOL;

    public static final ModConfigSpec.BooleanValue NAV_BEAM_ENABLED;
    public static final ModConfigSpec.DoubleValue NAV_BEAM_HEIGHT;
    public static final ModConfigSpec.IntValue NAV_BEAM_RENDER_DISTANCE;
    public static final ModConfigSpec.DoubleValue NAV_BEAM_FADE_START;
    public static final ModConfigSpec.ConfigValue<String> NAV_BEAM_PICKUP_COLOR;
    public static final ModConfigSpec.ConfigValue<String> NAV_BEAM_DELIVERY_COLOR;

    public static final ModConfigSpec.BooleanValue ROUTE_ENABLED;
    public static final ModConfigSpec.BooleanValue ROUTE_WORLD_MAP;
    public static final ModConfigSpec.BooleanValue ROUTE_MINIMAP;
    public static final ModConfigSpec.BooleanValue ROUTE_LABEL;
    public static final ModConfigSpec.BooleanValue ROUTE_ERASE_PASSED;
    public static final ModConfigSpec.BooleanValue ROUTE_WAYBILL_PREVIEW;
    public static final ModConfigSpec.BooleanValue ROUTE_OTHER_ORDERS;
    public static final ModConfigSpec.IntValue ROUTE_CORRIDOR;
    public static final ModConfigSpec.DoubleValue ROUTE_REROUTE_DELAY;
    public static final ModConfigSpec.EnumValue<RouteThickness> ROUTE_THICKNESS;
    public static final ModConfigSpec.BooleanValue ROUTE_CONTRAST;
    public static final ModConfigSpec.EnumValue<RouteEtaMode> ROUTE_ETA_MODE;
    public static final ModConfigSpec.DoubleValue ROUTE_FIXED_SPEED;
    public static final ModConfigSpec.IntValue ROUTE_BREADCRUMB_STEP;

    public static final ModConfigSpec.EnumValue<ToastCorner> TOAST_CORNER;
    public static final ModConfigSpec.IntValue TOAST_MAX_VISIBLE;
    public static final ModConfigSpec.IntValue TOAST_SECONDS;
    public static final ModConfigSpec.BooleanValue DO_NOT_DISTURB;
    public static final ModConfigSpec.EnumValue<TaskPanelMode> TASK_PANEL_MODE;
    private static final java.util.Map<NoticeChannel.Row, ModConfigSpec.BooleanValue> CATEGORY_TOAST =
            new java.util.EnumMap<>(NoticeChannel.Row.class);
    private static final java.util.Map<NoticeChannel.Row, ModConfigSpec.BooleanValue> CATEGORY_SOUND =
            new java.util.EnumMap<>(NoticeChannel.Row.class);
    private static final java.util.Map<NoticeChannel.Row, ModConfigSpec.BooleanValue> CATEGORY_CHAT =
            new java.util.EnumMap<>(NoticeChannel.Row.class);

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
        TABLET_GRID_ANIMATION = b.comment(
                        "Slowly scroll the orange grid on order-tablet screens (content area only; frame and widgets stay fixed)")
                .define("tablet_grid_animation", true);
        TABLET_GRID_SPEED = b.comment("Grid scroll speed in pixels per second toward the bottom-left corner")
                .defineInRange("tablet_grid_speed", 1.0, 0.0, 32.0);
        UI_ANIMATIONS = b.comment(
                        "Light motion on tablet screens (open/close, tabs, lists, buttons, profile XP bar)")
                .define("ui_animations", true);
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
                        "ICON — crate (📦) on the pre-accept preview pin only.",
                        "Accepted pickup and delivery pins always use the letters A and B.",
                        "FIRST_LETTER — first character of the order label;",
                        "CUSTOM — customWaypointSymbol")
                .defineEnum("waypointSymbolMode", WaypointSymbolMode.ICON);
        CUSTOM_WAYPOINT_SYMBOL = b.comment("Single-character or emoji marker when waypointSymbolMode is CUSTOM")
                .define("customWaypointSymbol", "\u2713");
        b.pop();

        b.push("navigation");
        NAV_BEAM_ENABLED = b.comment("Vertical beams at your active hauls: yellow = pickup, blue = delivery")
                .translation("aerocouriers.configuration.beam_enabled")
                .define("beam_enabled", true);
        NAV_BEAM_HEIGHT = b.comment("Beam height in blocks")
                .translation("aerocouriers.configuration.beam_height")
                .defineInRange("beam_height", 256.0, 16.0, 512.0);
        NAV_BEAM_RENDER_DISTANCE = b.comment("Do not draw the beam farther than this (blocks)")
                .translation("aerocouriers.configuration.beam_render_distance")
                .defineInRange("beam_render_distance", 512, 32, 2048);
        NAV_BEAM_FADE_START = b.comment("Full opacity until this distance; fades when closer (blocks)")
                .translation("aerocouriers.configuration.beam_fade_start")
                .defineInRange("beam_fade_start", 12.0, 2.0, 64.0);
        NAV_BEAM_PICKUP_COLOR = b.comment(
                        "Pickup beam RGB as \"r,g,b\" (0–1). Default is Xaero waypoint yellow #FFFF55")
                .translation("aerocouriers.configuration.beam_pickup_color")
                .define("beam_pickup_color", "1.0,1.0,0.33333334");
        NAV_BEAM_DELIVERY_COLOR = b.comment(
                        "Delivery beam RGB as \"r,g,b\" (0–1). Default is Xaero waypoint aqua #55FFFF")
                .translation("aerocouriers.configuration.beam_delivery_color")
                .define("beam_delivery_color", "0.33333334,1.0,1.0");
        b.pop();

        b.push("route");
        ROUTE_ENABLED = b.comment("Draw the delivery route line on Xaero's World Map and minimap (needs Xaero)")
                .define("enabled", true);
        ROUTE_WORLD_MAP = b.comment("Draw the route on the full-screen World Map").define("world_map", true);
        ROUTE_MINIMAP = b.comment("Draw the route inside the minimap").define("minimap", true);
        ROUTE_LABEL = b.comment("Label at the target on the World Map: distance, travel time, time to deadline")
                .define("label", true);
        ROUTE_ERASE_PASSED = b.comment("Erase the part of the line behind the ship").define("erase_passed", true);
        ROUTE_WAYBILL_PREVIEW = b.comment("\"Show on map\" button on the waybill").define("waybill_preview", true);
        ROUTE_OTHER_ORDERS = b.comment("Also draw your other active orders as a thin dashed line")
                .define("other_orders", false);
        ROUTE_CORRIDOR = b.comment("Rebuild the line when the ship is farther than this from it, blocks")
                .defineInRange("corridor", 32, 16, 96);
        ROUTE_REROUTE_DELAY = b.comment("Seconds outside the corridor before the line is rebuilt")
                .defineInRange("reroute_delay", 2.0, 0.5, 10.0);
        ROUTE_THICKNESS = b.comment("THIN, NORMAL or THICK").defineEnum("thickness", RouteThickness.NORMAL);
        ROUTE_CONTRAST = b.comment("Colour-blind friendly colours: orange to A, white to B")
                .define("contrast_colors", false);
        ROUTE_ETA_MODE = b.comment(
                        "SHIP_SPEED - travel time from the ship's speed over the last 10 s;",
                        "FIXED - always fixed_speed")
                .defineEnum("eta_mode", RouteEtaMode.SHIP_SPEED);
        ROUTE_FIXED_SPEED = b.comment("Blocks per second for FIXED and while the ship is standing still")
                .defineInRange("fixed_speed", 8.0, 1.0, 64.0);
        ROUTE_BREADCRUMB_STEP = b.comment(
                        "Fallback when the line cannot be drawn in this Xaero version:",
                        "temporary Xaero waypoints every N blocks along the route")
                .defineInRange("breadcrumb_step", 96, 32, 512);
        b.pop();

        b.push("notifications");
        TOAST_CORNER = b.comment("Corner of the toast stack")
                .defineEnum("toast_corner", ToastCorner.TOP_RIGHT);
        TOAST_MAX_VISIBLE = b.comment("Toasts shown at once; the rest wait in the queue")
                .defineInRange("toast_max_visible", 3, 1, 5);
        TOAST_SECONDS = b.comment("How long a toast stays, seconds (urgent ones stay twice as long)")
                .defineInRange("toast_seconds", 5, 2, 30);
        DO_NOT_DISTURB = b.comment("No toasts and no sounds except urgent ones; the log in Mail is always kept")
                .define("do_not_disturb", false);
        b.push("category");
        for (NoticeChannel.Row row : NoticeChannel.Row.values()) {
            b.push(row.key());
            CATEGORY_TOAST.put(row, b.define("toast", row.defaultToast()));
            CATEGORY_SOUND.put(row, b.define("sound", row.defaultSound()));
            CATEGORY_CHAT.put(row, b.define("chat", row.defaultChat()));
            b.pop();
        }
        b.pop();
        b.pop();

        b.push("tasks");
        TASK_PANEL_MODE = b.comment("Task panel on the right: FULL / COMPACT / COLLAPSED / HIDDEN")
                .defineEnum("panel_mode", TaskPanelMode.FULL);
        b.pop();

        SPEC = b.build();
    }

    public enum RouteThickness {
        THIN,
        NORMAL,
        THICK
    }

    public enum RouteEtaMode {
        SHIP_SPEED,
        FIXED
    }

    /** Reads a client value, falling back to its default before the config file is loaded. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return value.getDefault();
        }
    }

    public enum ToastCorner {
        TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean top() {
            return this == TOP_RIGHT || this == TOP_LEFT;
        }
    }

    public enum TaskPanelMode {
        FULL, COMPACT, COLLAPSED, HIDDEN;

        public TaskPanelMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static ModConfigSpec.BooleanValue channel(NoticeChannel.Row row, NoticeChannel channel) {
        return switch (channel) {
            case TOAST -> CATEGORY_TOAST.get(row);
            case SOUND -> CATEGORY_SOUND.get(row);
            case CHAT -> CATEGORY_CHAT.get(row);
        };
    }

    public static boolean channelEnabled(NoticeChannel.Row row, NoticeChannel channel) {
        try {
            return channel(row, channel).get();
        } catch (IllegalStateException ignored) {
            return switch (channel) {
                case TOAST -> row.defaultToast();
                case SOUND -> row.defaultSound();
                case CHAT -> row.defaultChat();
            };
        }
    }

    public static void setChannel(NoticeChannel.Row row, NoticeChannel channel, boolean on) {
        try {
            channel(row, channel).set(on);
            SPEC.save();
        } catch (IllegalStateException ignored) {
        }
    }

    public static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    public static <T> void set(ModConfigSpec.ConfigValue<T> value, T next) {
        try {
            value.set(next);
            SPEC.save();
        } catch (IllegalStateException ignored) {
        }
    }

    /** Client-only; safe when config is not loaded yet (defaults to mod sprites). */
    public static boolean useVanillaSprites() {
        try {
            return USE_VANILLA_SPRITES.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static boolean tabletGridAnimation() {
        try {
            return TABLET_GRID_ANIMATION.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static double tabletGridSpeed() {
        try {
            return TABLET_GRID_SPEED.get();
        } catch (IllegalStateException ignored) {
            return 1.0;
        }
    }

    public static boolean uiAnimations() {
        try {
            return UI_ANIMATIONS.get();
        } catch (IllegalStateException ignored) {
            return true;
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

    public static boolean navBeamEnabled() {
        try {
            return NAV_BEAM_ENABLED.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    /** Xaero {@code WaypointColor.YELLOW} #FFFF55, xaerominimap 26.6.0. */
    private static final float[] XAERO_YELLOW = {1.0f, 1.0f, 85f / 255f};
    /** Xaero {@code WaypointColor.AQUA} #55FFFF, xaerominimap 26.6.0. */
    private static final float[] XAERO_AQUA = {85f / 255f, 1.0f, 1.0f};

    public static float[] navBeamColorRgb(boolean delivery) {
        float[] fallback = delivery ? XAERO_AQUA : XAERO_YELLOW;
        try {
            String raw = (delivery ? NAV_BEAM_DELIVERY_COLOR : NAV_BEAM_PICKUP_COLOR).get();
            if (raw == null || isLegacyBeamDefault(raw, delivery)) {
                return fallback;
            }
            String[] parts = raw.split(",");
            if (parts.length < 3) {
                return fallback;
            }
            return new float[] {
                    Mth.clamp(Float.parseFloat(parts[0].trim()), 0f, 1f),
                    Mth.clamp(Float.parseFloat(parts[1].trim()), 0f, 1f),
                    Mth.clamp(Float.parseFloat(parts[2].trim()), 0f, 1f)
            };
        } catch (IllegalStateException | NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Pre-0.1.12 defaults. They do not match Xaero YELLOW / AQUA, so they now resolve to those hexes. */
    private static boolean isLegacyBeamDefault(String raw, boolean delivery) {
        String normalized = raw.replace(" ", "");
        return delivery ? normalized.equals("0.2,0.72,1.0") : normalized.equals("1.0,0.82,0.15");
    }

    private CouriersClientConfig() {
    }
}
