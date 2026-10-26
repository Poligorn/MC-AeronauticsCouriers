package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.WaypointSymbolMode;

/** Resolves the Xaero waypoint marker string from client config (no GPL code — same idea as other mods: symbol field). */
public final class XaeroWaypointSymbols {
    /** Crate marker when {@link WaypointSymbolMode#ICON} (all order waypoints). */
    public static final String ICON_CRATE = "\uD83D\uDCE6";

    public enum Kind {
        DELIVERY,
        PICKUP
    }

    private XaeroWaypointSymbols() {
    }

    public static String resolve(String label, Kind kind) {
        WaypointSymbolMode mode = CouriersClientConfig.waypointSymbolMode();
        if (mode == WaypointSymbolMode.FIRST_LETTER) {
            if (label != null && !label.isEmpty()) {
                return label.substring(0, 1).toUpperCase();
            }
            return "?";
        }
        if (mode == WaypointSymbolMode.CUSTOM) {
            return CouriersClientConfig.customWaypointSymbol();
        }
        return ICON_CRATE;
    }
}
