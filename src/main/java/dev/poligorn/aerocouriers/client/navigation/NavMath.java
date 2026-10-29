package dev.poligorn.aerocouriers.client.navigation;

/**
 * Horizontal distance for the nav beam. Bearing helpers stay so {@code tools/check_nav_math.py} can mirror them.
 */
public final class NavMath {
    private NavMath() {
    }

    public static double horizontalDistanceSq(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return dx * dx + dz * dz;
    }

    public static double horizontalDistance(double fromX, double fromZ, double toX, double toZ) {
        return Math.sqrt(horizontalDistanceSq(fromX, fromZ, toX, toZ));
    }

    /**
     * Bearing from {@code (fromX, fromZ)} to target in degrees, Minecraft yaw convention (0° = south/+Z,
     * increases clockwise).
     */
    public static float bearingDegrees(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** Smallest signed difference {@code target - current} in [-180, 180]. */
    public static float yawDelta(float currentYaw, float targetBearing) {
        float delta = targetBearing - currentYaw;
        while (delta > 180f) {
            delta -= 360f;
        }
        while (delta < -180f) {
            delta += 360f;
        }
        return delta;
    }
}
