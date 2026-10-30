package dev.poligorn.aerocouriers.client.route;

import java.util.List;

/**
 * A fixed polyline in world X/Z: the ship position when the route was built, optional via points, then the target.
 * Built once per route event and never moved, so lateral drift does not make it wobble.
 */
public final class RoutePlan {
    private final double[] xs;
    private final double[] zs;
    private final double[] along;

    private RoutePlan(double[] xs, double[] zs) {
        this.xs = xs;
        this.zs = zs;
        this.along = new double[xs.length];
        for (int i = 1; i < xs.length; i++) {
            along[i] = along[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
        }
    }

    /** @param via intermediate {@code {x, z}} points; empty until via-waypoints are designed (README decision 4) */
    public static RoutePlan of(double startX, double startZ, List<double[]> via, double targetX, double targetZ) {
        int n = via.size() + 2;
        double[] xs = new double[n];
        double[] zs = new double[n];
        xs[0] = startX;
        zs[0] = startZ;
        for (int i = 0; i < via.size(); i++) {
            xs[i + 1] = via.get(i)[0];
            zs[i + 1] = via.get(i)[1];
        }
        xs[n - 1] = targetX;
        zs[n - 1] = targetZ;
        return new RoutePlan(xs, zs);
    }

    public int size() {
        return xs.length;
    }

    public double x(int i) {
        return xs[i];
    }

    public double z(int i) {
        return zs[i];
    }

    public double length() {
        return along[along.length - 1];
    }

    /** Distance along the line where vertex {@code i} sits. */
    public double alongAt(int i) {
        return along[i];
    }

    public double targetX() {
        return xs[xs.length - 1];
    }

    public double targetZ() {
        return zs[zs.length - 1];
    }

    /** Point at {@code distance} along the line, clamped to the ends. */
    public double[] pointAt(double distance) {
        if (distance <= 0) {
            return new double[] {xs[0], zs[0]};
        }
        for (int i = 1; i < xs.length; i++) {
            if (distance <= along[i]) {
                double seg = along[i] - along[i - 1];
                double t = seg <= 1e-9 ? 0 : (distance - along[i - 1]) / seg;
                return new double[] {xs[i - 1] + (xs[i] - xs[i - 1]) * t, zs[i - 1] + (zs[i] - zs[i - 1]) * t};
            }
        }
        return new double[] {targetX(), targetZ()};
    }

    /**
     * Closest point of the line at or after {@code fromAlong}.
     *
     * @return {@code {along, lateralDistance}}
     */
    public double[] project(double px, double pz, double fromAlong) {
        double bestAlong = Math.min(Math.max(fromAlong, 0), length());
        double[] start = pointAt(bestAlong);
        double bestDist = Math.hypot(px - start[0], pz - start[1]);
        for (int i = 1; i < xs.length; i++) {
            if (along[i] < fromAlong) {
                continue;
            }
            double ax = xs[i - 1];
            double az = zs[i - 1];
            double dx = xs[i] - ax;
            double dz = zs[i] - az;
            double len2 = dx * dx + dz * dz;
            double t = len2 <= 1e-9 ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
            t = Math.max(0, Math.min(1, t));
            double a = along[i - 1] + (along[i] - along[i - 1]) * t;
            if (a < fromAlong) {
                continue;
            }
            double cx = ax + dx * t;
            double cz = az + dz * t;
            double d = Math.hypot(px - cx, pz - cz);
            if (d < bestDist) {
                bestDist = d;
                bestAlong = a;
            }
        }
        return new double[] {bestAlong, bestDist};
    }
}
