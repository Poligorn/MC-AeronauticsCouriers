package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.List;

/**
 * The zones the server last sent this client (GDD §10.3). Holds no client-only types, so the payload handler can
 * reference it from common code.
 */
public final class ClientZones {
    private static volatile List<ZoneSyncPayload.Zone> zones = List.of();

    public static void set(List<ZoneSyncPayload.Zone> received) {
        zones = List.copyOf(received);
        XaeroOrderBridge.syncActiveCourierZones(zones);
    }

    public static List<ZoneSyncPayload.Zone> get() {
        return zones;
    }

    public static void clear() {
        zones = List.of();
        XaeroOrderBridge.onLogout();
    }

    private ClientZones() {
    }
}
