package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Xaero's Minimap only: third-party order waypoints ({@code aerocouriers:orders}, temp). Must not reference World Map
 * types — the minimap is often installed without it.
 */
final class XaeroOrderWaypoints {
    static final ResourceLocation ORIGIN = AeronauticsCouriers.id("orders");

    private static final Set<String> PERSISTENT_IDS = new HashSet<>();
    /**
     * Dimension bucket of every waypoint we placed. Xaero keeps third-party waypoints per dimension container (the
     * session has no shared manager), so removal must go to the same bucket.
     */
    private static final Map<String, ResourceKey<Level>> PLACED = new HashMap<>();
    private static @Nullable UUID previewOrderId;

    private XaeroOrderWaypoints() {
    }

    static boolean minimapLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("xaerominimap");
    }

    static String waypointId(UUID orderId) {
        return "order-" + orderId;
    }

    static void showPeek(UUID orderId, ResourceKey<Level> dimension, BlockPos pos, String label) {
        place(orderId, dimension, pos, label, XaeroWaypointSymbols.Kind.DELIVERY, false);
    }

    static void hidePeek(UUID orderId) {
        if (previewOrderId != null && previewOrderId.equals(orderId)) {
            return;
        }
        remove(orderId, null);
    }

    /** Map-pin click: one preview waypoint per player (replaces any previous preview). */
    static void setPreview(UUID orderId, ResourceKey<Level> dimension, BlockPos pos, String label) {
        if (previewOrderId != null && !previewOrderId.equals(orderId)) {
            remove(previewOrderId, null);
        }
        previewOrderId = orderId;
        place(orderId, dimension, pos, label, XaeroWaypointSymbols.Kind.DELIVERY, false);
    }

    static boolean hasActivePreview() {
        return previewOrderId != null;
    }

    static void clearPreview() {
        if (previewOrderId != null) {
            remove(previewOrderId, null);
            previewOrderId = null;
        }
    }

    /** Active haul: yellow pickup or blue delivery waypoint, replaced in place when the haul changes stage. */
    static void markPersistent(
            UUID orderId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String label,
            XaeroWaypointSymbols.Kind symbolKind
    ) {
        String id = waypointId(orderId);
        PERSISTENT_IDS.add(id);
        if (previewOrderId != null && previewOrderId.equals(orderId)) {
            previewOrderId = null;
        }
        place(orderId, dimension, pos, label, symbolKind, true);
    }

    static void clearPersistent(UUID orderId) {
        PERSISTENT_IDS.remove(waypointId(orderId));
        if (previewOrderId != null && previewOrderId.equals(orderId)) {
            previewOrderId = null;
        }
        remove(orderId, null);
    }

    /** Drop preview when the order is finished or no longer on the board snapshot. */
    static void prunePreview(Set<UUID> stillRelevant) {
        if (previewOrderId == null) {
            return;
        }
        if (!stillRelevant.contains(previewOrderId)) {
            clearPreview();
        }
    }

    static void syncPersistent(Set<UUID> activeDeliveryOrders) {
        Set<String> stale = new HashSet<>(PERSISTENT_IDS);
        for (UUID id : activeDeliveryOrders) {
            stale.remove(waypointId(id));
        }
        for (String sid : stale) {
            PERSISTENT_IDS.remove(sid);
            removeByStringId(sid, null);
        }
        purgeExcept(new HashSet<>(PERSISTENT_IDS));
    }

    static void clearAll() {
        PERSISTENT_IDS.clear();
        previewOrderId = null;
        if (minimapLoaded()) {
            for (String id : Set.copyOf(PLACED.keySet())) {
                removeByStringId(id, null);
            }
            wipeOrigin();
        }
        PLACED.clear();
    }

    private static void place(
            UUID orderId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String label,
            XaeroWaypointSymbols.Kind symbolKind,
            boolean persistent
    ) {
        if (!minimapLoaded()) {
            return;
        }
        String id = waypointId(orderId);
        if (!persistent && PERSISTENT_IDS.contains(id)) {
            return;
        }
        WaypointColor color = !persistent ? WaypointColor.GOLD
                : symbolKind == XaeroWaypointSymbols.Kind.PICKUP ? WaypointColor.YELLOW : WaypointColor.AQUA;
        try {
            ResourceKey<Level> previous = PLACED.get(id);
            if (previous != null && !previous.equals(dimension)) {
                removeByStringId(id, previous);
            }
            ThirdPartyWaypoints bucket = bucket(dimension);
            String symbol = persistent
                    ? XaeroWaypointSymbols.persistentLetter(symbolKind)
                    : XaeroWaypointSymbols.resolve(label, symbolKind);
            if (bucket == null) {
                XaeroLegacyWaypoints.placeFallback(orderId, dimension, pos, label, symbol, persistent, color);
                return;
            }
            bucket.remove(id);
            bucket.add(id, new Waypoint(
                    pos.getX(), pos.getY(), pos.getZ(),
                    label, symbol,
                    color, WaypointPurpose.NORMAL, true));
            PLACED.put(id, dimension);
        } catch (Throwable ignored) {
            String symbol = persistent
                    ? XaeroWaypointSymbols.persistentLetter(symbolKind)
                    : XaeroWaypointSymbols.resolve(label, symbolKind);
            XaeroLegacyWaypoints.placeFallback(orderId, dimension, pos, label, symbol, persistent, color);
        }
    }

    private static void remove(UUID orderId, @Nullable ResourceKey<Level> dimension) {
        removeByStringId(waypointId(orderId), dimension);
    }

    private static void removeByStringId(String id, @Nullable ResourceKey<Level> dimension) {
        if (!minimapLoaded()) {
            return;
        }
        if (PERSISTENT_IDS.contains(id)) {
            return;
        }
        ResourceKey<Level> placedIn = dimension != null ? dimension : PLACED.get(id);
        PLACED.remove(id);
        if (placedIn == null) {
            return;
        }
        try {
            ThirdPartyWaypoints bucket = bucket(placedIn);
            if (bucket != null) {
                bucket.remove(id);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Drops our waypoints that are no longer an active haul, including copies left in another dimension container
     * after a rejoin (the in-memory {@link #PLACED} map does not survive a new session).
     */
    private static void purgeExcept(Set<String> keep) {
        visitOrigins((container, bucket) -> {
            List<String> drop = new ArrayList<>();
            for (String id : bucket.getIds()) {
                if (!keep.contains(id)) {
                    drop.add(id);
                    continue;
                }
                ResourceKey<Level> placed = PLACED.get(id);
                ResourceKey<Level> here = container.getEquivalentDimKey();
                if (placed != null && here != null && !placed.equals(here)) {
                    drop.add(id);
                }
            }
            for (String id : drop) {
                bucket.remove(id);
                if (!keep.contains(id)) {
                    PLACED.remove(id);
                }
            }
        });
    }

    /** Third-party waypoints are session memory; wipe every container so a rejoin cannot stack a second copy. */
    private static void wipeOrigin() {
        visitOrigins((container, bucket) -> {
            List<String> ids = new ArrayList<>();
            bucket.getIds().forEach(ids::add);
            for (String id : ids) {
                bucket.remove(id);
            }
        });
    }

    private interface OriginVisitor {
        void accept(MinimapWorldContainer container, ThirdPartyWaypoints bucket);
    }

    private static void visitOrigins(OriginVisitor visitor) {
        if (!minimapLoaded()) {
            return;
        }
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) {
                return;
            }
            MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
            visitOne(root, visitor);
            for (MinimapWorldContainer sub : root.getSubContainers()) {
                visitOne(sub, visitor);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void visitOne(MinimapWorldContainer container, OriginVisitor visitor) {
        try {
            ThirdPartyWaypoints bucket = container.getThirdPartyWaypointManager().get(ORIGIN);
            if (bucket != null && !bucket.isEmpty()) {
                visitor.accept(container, bucket);
            }
        } catch (Throwable ignored) {
        }
    }

    private static @Nullable ThirdPartyWaypoints bucket(ResourceKey<Level> dimension) {
        return bucket(dimension, ORIGIN);
    }

    static @Nullable ThirdPartyWaypoints bucket(ResourceKey<Level> dimension, ResourceLocation origin) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            return null;
        }
        try {
            MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
            String node = session.getDimensionHelper().getDimensionDirectoryName(dimension);
            MinimapWorldContainer container = root.addSubContainer(root.getPath().resolve(node));
            return container.getThirdPartyWaypointManager().get(origin);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
