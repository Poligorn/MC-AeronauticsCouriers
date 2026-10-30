package dev.poligorn.aerocouriers.client.route;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Measures how a Xaero map places world X/Z on screen without relying on Xaero internals.
 *
 * <p>Xaero has no public "world to screen" API. Its map element pipeline does place every element by its world
 * coordinates, so each frame we feed it four invisible probe elements (anchor, anchor+X, anchor+Z, anchor+X+Z) and
 * read the pose it hands to our renderer. The three first probes give the affine map (scale, minimap rotation,
 * offset); the fourth must close the parallelogram and decides whether the {@code partialX/partialY} arguments are
 * a sub-pixel remainder still to apply. Anything inconsistent (sheared, degenerate, clamped to the minimap edge)
 * yields no projection, so a changed Xaero build makes the line disappear instead of drawing it wrong.
 */
public final class RouteProbes {
    public static final int ANCHOR = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;
    public static final int AXIS_XZ = 3;
    public static final int COUNT = 4;

    private static final float TARGET_UNITS = 48f;

    private final Vector3f[] raw = new Vector3f[COUNT];
    private final Vector3f[] adjusted = new Vector3f[COUNT];
    private final boolean[] seen = new boolean[COUNT];
    private double anchorX;
    private double anchorZ;
    private double step = 16;
    private float unit = 1f;
    private boolean clamped;
    private double rawError;
    private double partialError;
    private int failedFrames;

    public void begin(double anchorX, double anchorZ) {
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        java.util.Arrays.fill(seen, false);
        clamped = false;
    }

    public double probeX(int kind) {
        return anchorX + (kind == AXIS_X || kind == AXIS_XZ ? step : 0);
    }

    public double probeZ(int kind) {
        return anchorZ + (kind == AXIS_Z || kind == AXIS_XZ ? step : 0);
    }

    /** @return {@code true} once all probes of this frame are in */
    public boolean record(int kind, Matrix4f pose, double partialX, double partialY, boolean outOfBounds) {
        raw[kind] = pose.transformPosition(0, 0, 0, new Vector3f());
        boolean partialSane = Math.abs(partialX) <= 1.0 && Math.abs(partialY) <= 1.0;
        adjusted[kind] = partialSane
                ? pose.transformPosition((float) partialX, (float) partialY, 0, new Vector3f())
                : new Vector3f(raw[kind]);
        seen[kind] = true;
        if (kind == ANCHOR) {
            unit = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01() + pose.m02() * pose.m02());
        }
        clamped |= outOfBounds;
        for (boolean s : seen) {
            if (!s) {
                return false;
            }
        }
        return true;
    }

    /** Pose scale of the anchor element: map units per "icon pixel", used for line width and text. */
    public float unit() {
        return unit > 1e-4f && Float.isFinite(unit) ? unit : 1f;
    }

    public int failedFrames() {
        return failedFrames;
    }

    public @Nullable Projection projection() {
        if (clamped) {
            step = Math.max(0.25, step / 2);
            return null;
        }
        rawError = rawError * 0.9 + residual(raw);
        partialError = partialError * 0.9 + residual(adjusted);
        Vector3f[] points = partialError < rawError ? adjusted : raw;
        Projection projection = Projection.of(anchorX, anchorZ, step, points[ANCHOR], points[AXIS_X], points[AXIS_Z]);
        if (projection == null || residual(points) > Math.max(2.0, projection.pixelsPerBlock() * step * 0.1)) {
            failedFrames++;
            return null;
        }
        failedFrames = 0;
        double wanted = TARGET_UNITS * unit() / projection.pixelsPerBlock();
        step = Math.max(0.25, Math.min(8192, wanted));
        return projection;
    }

    private static double residual(Vector3f[] p) {
        float ex = p[AXIS_XZ].x - (p[AXIS_X].x + p[AXIS_Z].x - p[ANCHOR].x);
        float ey = p[AXIS_XZ].y - (p[AXIS_X].y + p[AXIS_Z].y - p[ANCHOR].y);
        return Math.hypot(ex, ey);
    }

    /** World X/Z to the render space the probes were measured in. */
    public record Projection(double anchorX, double anchorZ, double ox, double oy, double depth,
                             double xx, double xy, double zx, double zy) {
        static @Nullable Projection of(double anchorX, double anchorZ, double step, Vector3f o, Vector3f px,
                                       Vector3f pz) {
            double xx = (px.x - o.x) / step;
            double xy = (px.y - o.y) / step;
            double zx = (pz.x - o.x) / step;
            double zy = (pz.y - o.y) / step;
            double lx = Math.hypot(xx, xy);
            double lz = Math.hypot(zx, zy);
            if (!Double.isFinite(lx) || !Double.isFinite(lz) || lx < 1e-6 || lz < 1e-6) {
                return null;
            }
            double ratio = lx / lz;
            double cos = (xx * zx + xy * zy) / (lx * lz);
            if (ratio < 0.8 || ratio > 1.25 || Math.abs(cos) > 0.2) {
                return null;
            }
            return new Projection(anchorX, anchorZ, o.x, o.y, o.z, xx, xy, zx, zy);
        }

        public double screenX(double worldX, double worldZ) {
            return ox + (worldX - anchorX) * xx + (worldZ - anchorZ) * zx;
        }

        public double screenY(double worldX, double worldZ) {
            return oy + (worldX - anchorX) * xy + (worldZ - anchorZ) * zy;
        }

        public double pixelsPerBlock() {
            return (Math.hypot(xx, xy) + Math.hypot(zx, zy)) / 2;
        }
    }
}
