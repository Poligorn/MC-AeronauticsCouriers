package dev.poligorn.aerocouriers.client.route;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side route model (README decisions 1–3): one fixed polyline for the tracked order, a corridor around it, and
 * a rebuild when the ship stays outside the corridor, the tracked order or its target changes, the cargo is picked up
 * (target A → B) or the order closes. Holds no Xaero types; the map layers only read {@link #state()}.
 */
public final class RouteTracker {
    private static final int SPEED_WINDOW_TICKS = 200;
    private static final double MIN_SHIP_SPEED = 1.0;

    private static List<ZoneSyncPayload.Zone> zones = List.of();
    private static final Map<UUID, Long> DUE_GAME_TIME = new HashMap<>();
    private static @Nullable UUID selected;

    private static @Nullable String routeKey;
    private static @Nullable RoutePlan plan;
    private static double progress;
    private static int outsideTicks;
    private static @Nullable ResourceKey<Level> lastPlayerDimension;
    private static @Nullable RouteState state;
    private static List<OtherTarget> others = List.of();

    private static final double[] SPEED_X = new double[SPEED_WINDOW_TICKS];
    private static final double[] SPEED_Z = new double[SPEED_WINDOW_TICKS];
    private static int speedCount;
    private static int speedHead;

    private RouteTracker() {
    }

    /** Snapshot for the map layers; {@code null} when nothing is tracked. */
    public static @Nullable RouteState state() {
        return state;
    }

    public static List<OtherTarget> others() {
        return others;
    }

    /** Selects which active order the line follows (task panel, "Show on map" on an active haul). */
    public static void track(UUID orderId) {
        selected = orderId;
    }

    public static @Nullable UUID tracked() {
        return state == null ? null : state.orderId();
    }

    public static void onZones(List<ZoneSyncPayload.Zone> received) {
        zones = List.copyOf(received);
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        DUE_GAME_TIME.clear();
        for (ZoneSyncPayload.Zone zone : zones) {
            DUE_GAME_TIME.put(zone.orderId(), now + zone.ticksLeft());
        }
    }

    public static void clear() {
        zones = List.of();
        DUE_GAME_TIME.clear();
        selected = null;
        reset();
        lastPlayerDimension = null;
        speedCount = 0;
    }

    private static void reset() {
        routeKey = null;
        plan = null;
        progress = 0;
        outsideTicks = 0;
        state = null;
        others = List.of();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity ship = minecraft.getCameraEntity();
        if (minecraft.level == null || ship == null) {
            reset();
            return;
        }
        if (!minecraft.isPaused()) {
            sampleSpeed(ship.getX(), ship.getZ());
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        boolean dimensionChanged = lastPlayerDimension != null && !lastPlayerDimension.equals(dimension);
        lastPlayerDimension = dimension;

        ZoneSyncPayload.Zone zone = pickTracked();
        if (zone == null || !CouriersClientConfig.get(CouriersClientConfig.ROUTE_ENABLED)) {
            reset();
            return;
        }
        others = collectOthers(zone, dimension);
        if (!zone.dimension().equals(dimension)) {
            routeKey = null;
            plan = null;
            state = RouteState.elsewhere(zone);
            return;
        }

        double px = ship.getX();
        double pz = ship.getZ();
        double tx = zone.pos().getX() + 0.5;
        double tz = zone.pos().getZ() + 0.5;
        String key = zone.orderId() + "|" + zone.delivery() + "|" + zone.pos().asLong();
        if (plan == null || dimensionChanged || !Objects.equals(key, routeKey)) {
            rebuild(key, px, pz, tx, tz);
        }

        double corridor = CouriersClientConfig.get(CouriersClientConfig.ROUTE_CORRIDOR);
        int delayTicks = (int) Math.round(CouriersClientConfig.get(CouriersClientConfig.ROUTE_REROUTE_DELAY) * 20);
        double[] projection = plan.project(px, pz, progress);
        boolean outside = projection[1] > corridor;
        if (outside) {
            outsideTicks++;
            if (outsideTicks >= delayTicks) {
                rebuild(key, px, pz, tx, tz);
                projection = plan.project(px, pz, 0);
                outside = false;
            }
        } else {
            outsideTicks = 0;
            progress = Math.max(progress, projection[0]);
        }

        double straight = Math.hypot(tx - px, tz - pz);
        double remaining = outside ? straight : Math.max(0, plan.length() - progress) + projection[1];
        boolean inZone = straight <= Math.max(1, zone.radius());
        double speed = etaSpeed();
        long dueAt = DUE_GAME_TIME.getOrDefault(zone.orderId(), minecraft.level.getGameTime());
        long ticksLeft = dueAt - minecraft.level.getGameTime();
        double etaSeconds = remaining / speed;
        boolean late = etaSeconds * 20 > ticksLeft;
        state = new RouteState(zone.orderId(), zone.contract(), zone.dimension(), zone.delivery(), zone.label(),
                zone.radius(), plan, progress, projection[0], projection[1], outside, zone.next().orElse(null),
                zone.pos(), remaining, etaSeconds, ticksLeft, late, inZone);
    }

    private static void rebuild(String key, double px, double pz, double tx, double tz) {
        routeKey = key;
        plan = RoutePlan.of(px, pz, List.of(), tx, tz);
        progress = 0;
        outsideTicks = 0;
    }

    private static @Nullable ZoneSyncPayload.Zone pickTracked() {
        if (zones.isEmpty()) {
            return null;
        }
        if (selected != null) {
            for (ZoneSyncPayload.Zone zone : zones) {
                if (zone.orderId().equals(selected)) {
                    return zone;
                }
            }
            selected = null;
        }
        ZoneSyncPayload.Zone best = null;
        long bestDue = Long.MAX_VALUE;
        for (ZoneSyncPayload.Zone zone : zones) {
            long due = DUE_GAME_TIME.getOrDefault(zone.orderId(), Long.MAX_VALUE);
            if (best == null || due < bestDue) {
                best = zone;
                bestDue = due;
            }
        }
        return best;
    }

    private static List<OtherTarget> collectOthers(ZoneSyncPayload.Zone tracked, ResourceKey<Level> dimension) {
        if (!CouriersClientConfig.get(CouriersClientConfig.ROUTE_OTHER_ORDERS)) {
            return List.of();
        }
        List<OtherTarget> list = new ArrayList<>();
        for (ZoneSyncPayload.Zone zone : zones) {
            if (zone.orderId().equals(tracked.orderId()) || !zone.dimension().equals(dimension)) {
                continue;
            }
            list.add(new OtherTarget(zone.pos().getX() + 0.5, zone.pos().getZ() + 0.5, zone.delivery()));
        }
        return List.copyOf(list);
    }

    private static void sampleSpeed(double x, double z) {
        SPEED_X[speedHead] = x;
        SPEED_Z[speedHead] = z;
        speedHead = (speedHead + 1) % SPEED_WINDOW_TICKS;
        speedCount = Math.min(speedCount + 1, SPEED_WINDOW_TICKS);
    }

    /** Ship speed over the last 10 s in blocks per second; the fixed speed when standing or in FIXED mode. */
    public static double etaSpeed() {
        double fixed = CouriersClientConfig.get(CouriersClientConfig.ROUTE_FIXED_SPEED);
        if (CouriersClientConfig.get(CouriersClientConfig.ROUTE_ETA_MODE) == CouriersClientConfig.RouteEtaMode.FIXED
                || speedCount < 20) {
            return fixed;
        }
        double path = 0;
        int start = (speedHead - speedCount + SPEED_WINDOW_TICKS) % SPEED_WINDOW_TICKS;
        for (int i = 1; i < speedCount; i++) {
            int a = (start + i - 1) % SPEED_WINDOW_TICKS;
            int b = (start + i) % SPEED_WINDOW_TICKS;
            double step = Math.hypot(SPEED_X[b] - SPEED_X[a], SPEED_Z[b] - SPEED_Z[a]);
            if (step < 64) {
                path += step;
            }
        }
        double perSecond = path / ((speedCount - 1) / 20.0);
        return perSecond < MIN_SHIP_SPEED ? fixed : perSecond;
    }

    /**
     * @param along       projection of the ship on the line
     * @param lateral     ship distance from the line
     * @param outside     ship is outside the corridor and the rebuild timer runs (line is dimmed)
     * @param next        A→B leg while heading to pickup, for the dimmed preview
     * @param remaining   blocks left along the route
     * @param ticksLeft   game ticks to the deadline, negative once late
     */
    public record RouteState(UUID orderId, String contract, ResourceKey<Level> dimension, boolean delivery,
                             String label, int radius, @Nullable RoutePlan plan, double progress, double along,
                             double lateral, boolean outside, @Nullable ZoneSyncPayload.Next next, BlockPos target,
                             double remaining, double etaSeconds, long ticksLeft, boolean late, boolean inZone) {
        static RouteState elsewhere(ZoneSyncPayload.Zone zone) {
            return new RouteState(zone.orderId(), zone.contract(), zone.dimension(), zone.delivery(), zone.label(),
                    zone.radius(), null, 0, 0, 0, false, null, zone.pos(), 0, 0, 0, false, false);
        }

        /** Tracked target is in another dimension: nothing to draw, only the map banner. */
        public boolean otherDimension() {
            return plan == null;
        }
    }

    public record OtherTarget(double x, double z, boolean delivery) {
    }
}
