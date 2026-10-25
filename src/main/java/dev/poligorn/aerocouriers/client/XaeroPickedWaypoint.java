package dev.poligorn.aerocouriers.client;

/**
 * A player waypoint copied from Xaero into the stamp form. No Xaero types — safe to reference
 * when the minimap is not installed.
 */
public record XaeroPickedWaypoint(String name, int x, int y, int z, boolean yEstimated, long createdAt, int identity) {
    public String cycleKey() {
        return name + " @" + x + "," + y + "," + z + "#" + createdAt + ":" + identity;
    }

    public String label() {
        return name + "  " + x + " " + y + " " + z;
    }
}
