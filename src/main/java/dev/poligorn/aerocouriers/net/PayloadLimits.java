package dev.poligorn.aerocouriers.net;

import java.util.List;

/** Wire and UI caps for network payload lists (independent of courier container rules in {@link dev.poligorn.aerocouriers.contract.ManifestLimits}). */
public final class PayloadLimits {
    /** Item stacks synced per board row / waybill preview (XL + headroom). */
    public static final int CARGO_STACK_SYNC = 54;
    /** Legacy name used across the mod. */
    public static final int CARGO_STACK_LIMIT = CARGO_STACK_SYNC;

    /** Generous cap for item-stack lists on the wire; server data is clamped before encode. */
    public static final int WIRE_ITEM_STACK_LIST = 512;

    public static final int BOARD_PAGE = 20;
    public static final int BOARD_MINE = 64;
    public static final int STATION_HINTS = 64;
    public static final int ADMIN_ACTIVE = 64;
    public static final int ONLINE_PLAYERS = 64;
    public static final int ADMIN_CONFIG_LINES = 32;
    public static final int STATION_ORDERS = 32;
    public static final int ZONE_SYNC = 64;

    private PayloadLimits() {
    }

    public static <T> List<T> cap(List<T> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        return List.copyOf(list.subList(0, max));
    }
}
