package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.WaypointSymbolMode;

/** Resolves the Xaero waypoint marker string from client config (no GPL code — same idea as other mods: symbol field). */
public final class XaeroWaypointSymbols {
    /** Crate / gift marker for the pre-accept preview pin when {@link WaypointSymbolMode#ICON}. */
    public static final String ICON_CRATE = "\uD83D\uDCE6";
    /** Accepted haul: pickup, same letter as point A on the waybill. */
    public static final String LETTER_PICKUP = "A";
    /** Accepted haul: delivery, same letter as point B on the waybill. */
    public static final String LETTER_DELIVERY = "B";

    public enum Kind {
        DELIVERY,
        PICKUP
    }

    private XaeroWaypointSymbols() {
    }

    /** Persistent pickup/delivery pins. Ignores symbol mode so the letters stay A and B. */
    public static String persistentLetter(Kind kind) {
        return kind == Kind.PICKUP ? LETTER_PICKUP : LETTER_DELIVERY;
    }

    /** Preview pin only. Accepted hauls use {@link #persistentLetter}. */
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
