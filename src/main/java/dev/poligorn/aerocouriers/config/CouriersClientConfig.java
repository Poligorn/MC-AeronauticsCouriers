package dev.poligorn.aerocouriers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code aerocouriers-client.toml} (GDD §10.1, §10.3). {@code [button]} is read by the inventory button once the UI
 * exists (iteration 4); {@code [zones]} drives the zone outline particles.
 */
public final class CouriersClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<ButtonPosition> BUTTON_POSITION;
    public static final ModConfigSpec.IntValue BUTTON_CUSTOM_X;
    public static final ModConfigSpec.IntValue BUTTON_CUSTOM_Y;

    public static final ModConfigSpec.BooleanValue ZONES_ENABLED;
    public static final ModConfigSpec.DoubleValue ZONES_PARTICLE_DENSITY;
    public static final ModConfigSpec.IntValue ZONES_RENDER_DISTANCE;

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
        SPEC = b.build();
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
