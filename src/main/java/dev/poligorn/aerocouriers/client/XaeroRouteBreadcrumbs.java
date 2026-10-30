package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.route.RoutePlan;
import dev.poligorn.aerocouriers.client.route.RouteTracker;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;

/**
 * Fallback when the line cannot be drawn (mockup 08ж): temporary third-party waypoints every N blocks along the
 * route, passed ones removed. Uses only Xaero's third-party waypoint API (Minimap ≥ 26.2.0), no render hooks.
 */
final class XaeroRouteBreadcrumbs {
    static final ResourceLocation ORIGIN = AeronauticsCouriers.id("route");
    private static final int MAX_CRUMBS = 32;

    private static final Map<String, ResourceKey<Level>> PLACED = new HashMap<>();
    private static @Nullable RoutePlan placedFor;
    private static boolean placedDelivery;

    private XaeroRouteBreadcrumbs() {
    }

    static void sync(boolean active) {
        RouteTracker.RouteState s = RouteTracker.state();
        RoutePlan plan = s == null ? null : s.plan();
        if (!active || plan == null || s.inZone()) {
            clear();
            return;
        }
        if (plan != placedFor || s.delivery() != placedDelivery) {
            clear();
            placedFor = plan;
            placedDelivery = s.delivery();
        }
        int step = CouriersClientConfig.get(CouriersClientConfig.ROUTE_BREADCRUMB_STEP);
        double end = plan.length() - Math.max(s.radius(), step / 2.0);
        Set<String> wanted = new HashSet<>();
        int first = (int) Math.ceil((s.progress() + step / 2.0) / step);
        for (int i = Math.max(1, first); i * (double) step < end && wanted.size() < MAX_CRUMBS; i++) {
            String id = "route-" + i;
            wanted.add(id);
            if (!PLACED.containsKey(id)) {
                double[] p = plan.pointAt(i * (double) step);
                place(id, s.dimension(), p[0], p[1], s.delivery());
            }
        }
        for (String id : Set.copyOf(PLACED.keySet())) {
            if (!wanted.contains(id)) {
                remove(id);
            }
        }
    }

    static void clear() {
        for (String id : Set.copyOf(PLACED.keySet())) {
            remove(id);
        }
        placedFor = null;
    }

    private static void place(String id, ResourceKey<Level> dimension, double x, double z, boolean delivery) {
        ThirdPartyWaypoints bucket = XaeroOrderWaypoints.bucket(dimension, ORIGIN);
        if (bucket == null) {
            return;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        int y = camera == null ? 64 : camera.getBlockY();
        String letter = delivery ? "B" : "A";
        bucket.remove(id);
        bucket.add(id, new Waypoint((int) Math.floor(x), y, (int) Math.floor(z), "\u2192 " + letter, "\u2022",
                delivery ? WaypointColor.AQUA : WaypointColor.YELLOW, WaypointPurpose.NORMAL, true));
        PLACED.put(id, dimension);
    }

    private static void remove(String id) {
        ResourceKey<Level> dimension = PLACED.remove(id);
        if (dimension == null) {
            return;
        }
        ThirdPartyWaypoints bucket = XaeroOrderWaypoints.bucket(dimension, ORIGIN);
        if (bucket != null) {
            bucket.remove(id);
        }
    }
}
