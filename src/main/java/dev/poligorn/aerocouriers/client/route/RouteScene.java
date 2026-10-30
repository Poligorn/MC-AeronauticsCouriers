package dev.poligorn.aerocouriers.client.route;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** What the route looks like on a map, given a measured {@link RouteCanvas}. Shared by World Map and minimap. */
public final class RouteScene {
    private static final double ARROW_SPACING = 36;
    private static final double CONNECTOR_MIN_BLOCKS = 2;

    private RouteScene() {
    }

    /** Whether anything would be drawn for this map dimension; lets the layers skip probing entirely. */
    public static boolean hasContent(@Nullable ResourceKey<Level> mapDimension, boolean worldMap) {
        if (mapDimension == null) {
            return false;
        }
        if (worldMap) {
            RoutePreview.Preview preview = RoutePreview.active();
            if (preview != null) {
                return preview.dimension().equals(mapDimension)
                        && CouriersClientConfig.get(CouriersClientConfig.ROUTE_WAYBILL_PREVIEW);
            }
        }
        if (!CouriersClientConfig.get(CouriersClientConfig.ROUTE_ENABLED)
                || !CouriersClientConfig.get(worldMap ? CouriersClientConfig.ROUTE_WORLD_MAP
                : CouriersClientConfig.ROUTE_MINIMAP)) {
            return false;
        }
        RouteTracker.RouteState state = RouteTracker.state();
        return state != null && !state.otherDimension() && state.dimension().equals(mapDimension);
    }

    public static void draw(RouteCanvas c, @Nullable Font font, boolean worldMap, double playerX, double playerZ) {
        RoutePreview.Preview preview = worldMap ? RoutePreview.active() : null;
        if (preview != null) {
            drawPreview(c, font, preview);
            c.flush();
            return;
        }
        RouteTracker.RouteState s = RouteTracker.state();
        if (s == null || s.plan() == null) {
            return;
        }
        float u = c.unit();
        double width = RouteStyle.width() * u;
        double ppb = c.projection().pixelsPerBlock();
        int base = s.late() ? RouteStyle.LATE : RouteStyle.color(s.delivery());
        int color = s.outside() ? RouteStyle.alpha(base, 0.4f) : base;
        double tx = s.target().getX() + 0.5;
        double tz = s.target().getZ() + 0.5;
        double ringPx = Math.max(s.radius() * ppb, (worldMap ? 4 : 5) * u);

        ZoneSyncPayload.Next next = s.next();
        if (next != null) {
            int nextColor = RouteStyle.alpha(RouteStyle.color(true), 0.45f);
            double bx = next.pos().getX() + 0.5;
            double bz = next.pos().getZ() + 0.5;
            dashedWorld(c, tx, tz, bx, bz, width * 0.75, 6 * u, 4 * u, nextColor);
            c.ring(c.sx(bx, bz), c.sy(bx, bz), Math.max(next.radius() * ppb, 3 * u), width * 0.75, nextColor);
        }
        for (RouteTracker.OtherTarget other : RouteTracker.others()) {
            int otherColor = RouteStyle.alpha(RouteStyle.color(other.delivery()), 0.6f);
            dashedWorld(c, playerX, playerZ, other.x(), other.z(), Math.max(u, width * 0.5), 3 * u, 3 * u,
                    otherColor);
        }

        if (!s.inZone()) {
            List<double[]> points = routeScreenPoints(c, s);
            trimEnd(points, ringPx);
            polyline(c, points, width + 2 * u, RouteStyle.alpha(RouteStyle.OUTLINE, s.outside() ? 0.4f : 1f));
            polyline(c, points, width, color);
            arrows(c, points, ARROW_SPACING * u, 3.5 * u, Math.max(u, width * 0.5),
                    RouteStyle.alpha(base == 0xFFFFFFFF ? RouteStyle.OUTLINE : 0xFFFFFFFF, s.outside() ? 0.4f : 1f));
            if (!s.outside() && s.lateral() > CONNECTOR_MIN_BLOCKS) {
                double[] onLine = s.plan().pointAt(s.along());
                dashedWorld(c, playerX, playerZ, onLine[0], onLine[1], Math.max(u * 0.75, width * 0.4), 2 * u, 2 * u,
                        RouteStyle.alpha(0xFFFFFFFF, 0.7f));
            }
        }
        double cx = c.sx(tx, tz);
        double cy = c.sy(tx, tz);
        c.ring(cx, cy, ringPx, width * 0.75 + u, RouteStyle.alpha(RouteStyle.OUTLINE, 0.6f));
        c.ring(cx, cy, ringPx, width * 0.75, color);

        if (worldMap && font != null && CouriersClientConfig.get(CouriersClientConfig.ROUTE_LABEL)) {
            String letter = s.delivery() ? "B" : "A";
            String title = letter + " \u00B7 " + s.label();
            String detail;
            int detailColor = s.late() ? RouteStyle.LATE : 0xFFD0D8E0;
            if (s.inZone()) {
                detail = Component.translatable(s.delivery() ? "ui.aerocouriers.route.in_zone.delivery"
                        : "ui.aerocouriers.route.in_zone.pickup").getString();
                detailColor = 0xFFD0D8E0;
            } else {
                String deadline = s.ticksLeft() < 0
                        ? Component.translatable("ui.aerocouriers.route.overdue").getString()
                        : Component.translatable("ui.aerocouriers.route.deadline",
                        duration(s.ticksLeft() / 20.0)).getString();
                detail = Component.translatable("ui.aerocouriers.route.detail", letter, blocks(s.remaining()),
                        duration(s.etaSeconds()), deadline).getString();
            }
            label(c, font, cx + ringPx + 4 * u, cy - 22 * u, title, detail, color, detailColor);
        }
        c.flush();
    }

    private static void drawPreview(RouteCanvas c, @Nullable Font font, RoutePreview.Preview p) {
        float u = c.unit();
        double width = RouteStyle.width() * u;
        double ppb = c.projection().pixelsPerBlock();
        double ax = p.from().getX() + 0.5;
        double az = p.from().getZ() + 0.5;
        double bx = p.to().getX() + 0.5;
        double bz = p.to().getZ() + 0.5;
        double ringPx = Math.max(p.radius() * ppb, 4 * u);
        List<double[]> points = new ArrayList<>();
        points.add(new double[] {c.sx(ax, az), c.sy(ax, az)});
        points.add(new double[] {c.sx(bx, bz), c.sy(bx, bz)});
        trimEnd(points, ringPx);
        double[] a = points.get(0);
        double[] b = points.get(1);
        c.dashed(a[0], a[1], b[0], b[1], width + 2 * u, 7 * u, 3 * u, 0, RouteStyle.OUTLINE);
        c.dashed(a[0], a[1], b[0], b[1], width, 7 * u, 3 * u, 0, RouteStyle.PREVIEW);
        arrows(c, points, ARROW_SPACING * u, 3.5 * u, Math.max(u, width * 0.5), RouteStyle.OUTLINE);
        int pickup = RouteStyle.color(false);
        int delivery = RouteStyle.color(true);
        marker(c, font, a[0], a[1], "A", pickup);
        double cx = c.sx(bx, bz);
        double cy = c.sy(bx, bz);
        c.ring(cx, cy, ringPx, width * 0.75, delivery);
        if (font != null) {
            String detail = Component.translatable("ui.aerocouriers.route.preview_detail", blocks(p.distance()),
                    duration(p.distance() / CouriersClientConfig.get(CouriersClientConfig.ROUTE_FIXED_SPEED)))
                    .getString();
            label(c, font, cx + ringPx + 4 * u, cy - 22 * u, "B \u00B7 " + p.label(), detail, delivery, 0xFFD0D8E0);
        }
    }

    private static List<double[]> routeScreenPoints(RouteCanvas c, RouteTracker.RouteState s) {
        RoutePlan plan = s.plan();
        boolean erase = CouriersClientConfig.get(CouriersClientConfig.ROUTE_ERASE_PASSED);
        double from = erase ? s.progress() : 0;
        List<double[]> points = new ArrayList<>();
        double[] start = plan.pointAt(from);
        points.add(new double[] {c.sx(start[0], start[1]), c.sy(start[0], start[1])});
        for (int i = 1; i < plan.size(); i++) {
            if (plan.alongAt(i) > from) {
                points.add(new double[] {c.sx(plan.x(i), plan.z(i)), c.sy(plan.x(i), plan.z(i))});
            }
        }
        return points;
    }

    /** Ends the line on the zone ring instead of the zone centre. */
    private static void trimEnd(List<double[]> points, double by) {
        while (points.size() >= 2) {
            double[] a = points.get(points.size() - 2);
            double[] b = points.get(points.size() - 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (len > by) {
                double t = (len - by) / len;
                points.set(points.size() - 1, new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
                return;
            }
            points.remove(points.size() - 1);
            by -= len;
        }
    }

    private static void polyline(RouteCanvas c, List<double[]> points, double width, int argb) {
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            c.segment(a[0], a[1], b[0], b[1], width, argb);
        }
    }

    private static void arrows(RouteCanvas c, List<double[]> points, double spacing, double size, double width,
                               int argb) {
        double carry = spacing / 2;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double t = carry;
            while (t < len - size) {
                double x = a[0] + (b[0] - a[0]) * t / len;
                double y = a[1] + (b[1] - a[1]) * t / len;
                c.chevron(x, y, b[0] - a[0], b[1] - a[1], size, width, argb);
                t += spacing;
            }
            carry = Math.max(0, t - len);
        }
    }

    private static void dashedWorld(RouteCanvas c, double x1, double z1, double x2, double z2, double width,
                                    double dash, double gap, int argb) {
        c.dashed(c.sx(x1, z1), c.sy(x1, z1), c.sx(x2, z2), c.sy(x2, z2), width, dash, gap, 0, argb);
    }

    private static void marker(RouteCanvas c, @Nullable Font font, double x, double y, String letter, int argb) {
        float u = c.unit();
        c.rect(x - 5 * u, y - 5 * u, x + 5 * u, y + 5 * u, RouteStyle.OUTLINE);
        c.rect(x - 4 * u, y - 4 * u, x + 4 * u, y + 4 * u, argb);
        if (font != null) {
            c.text(font, letter, x - font.width(letter) * u / 2.0 + 0.5 * u, y - 3.5 * u, 0xFF101418);
        }
    }

    private static void label(RouteCanvas c, Font font, double x, double y, String title, String detail,
                              int border, int detailColor) {
        float u = c.unit();
        double w = (Math.max(font.width(title), font.width(detail)) + 8) * u;
        double h = (font.lineHeight * 2 + 6) * u;
        c.rect(x - u, y - u, x + w + u, y + h + u, border);
        c.rect(x, y, x + w, y + h, RouteStyle.LABEL_BG);
        c.text(font, title, x + 4 * u, y + 3 * u, 0xFFFFFFFF);
        c.text(font, detail, x + 4 * u, y + (3 + font.lineHeight) * u, detailColor);
    }

    public static String blocks(double distance) {
        return String.format(Locale.ROOT, "%,d", Math.round(distance)).replace(',', ' ');
    }

    public static String duration(double seconds) {
        long total = Math.max(0, Math.round(seconds));
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }
}
