package dev.poligorn.aerocouriers.client.route;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;

/** Route colours (same RGB as the pickup/delivery beams) and line widths in map icon pixels. */
public final class RouteStyle {
    public static final int LATE = 0xFFFF5555;
    public static final int PREVIEW = 0xFFFFFFFF;
    public static final int OUTLINE = 0xE0101418;
    public static final int LABEL_BG = 0xD0101820;

    private static final int CONTRAST_PICKUP = 0xFFFF9A1F;
    private static final int CONTRAST_DELIVERY = 0xFFFFFFFF;

    private RouteStyle() {
    }

    public static int color(boolean delivery) {
        if (CouriersClientConfig.get(CouriersClientConfig.ROUTE_CONTRAST)) {
            return delivery ? CONTRAST_DELIVERY : CONTRAST_PICKUP;
        }
        float[] rgb = CouriersClientConfig.navBeamColorRgb(delivery);
        return 0xFF000000 | channel(rgb[0]) << 16 | channel(rgb[1]) << 8 | channel(rgb[2]);
    }

    public static int alpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static float width() {
        return switch (CouriersClientConfig.get(CouriersClientConfig.ROUTE_THICKNESS)) {
            case THIN -> 1.0f;
            case NORMAL -> 2.0f;
            case THICK -> 3.0f;
        };
    }

    private static int channel(float value) {
        return Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
    }
}
