package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * Xaero-only third-party order waypoints ({@code aerocouriers:orders}, temp). Loaded via compileOnly jars.
 */
final class XaeroOrderWaypoints {
    static final ResourceLocation ORIGIN = AeronauticsCouriers.id("orders");

    private static final Set<String> PERSISTENT_IDS = new HashSet<>();
    private static @Nullable UUID previewOrderId;
    private static @Nullable MinimapSession cachedSession;
    private static @Nullable ThirdPartyWaypointManager cachedManager;

    private XaeroOrderWaypoints() {
    }

    static boolean minimapLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("xaerominimap");
    }

    static boolean worldMapLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("xaeroworldmap");
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

    static void clearPreview() {
        if (previewOrderId != null) {
            remove(previewOrderId, null);
            previewOrderId = null;
        }
    }

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
    }

    static void clearAll() {
        PERSISTENT_IDS.clear();
        previewOrderId = null;
        if (!minimapLoaded()) {
            return;
        }
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) {
                return;
            }
            ThirdPartyWaypointManager manager = manager(session);
            if (manager != null) {
                manager.clearOrigin(ORIGIN);
            }
        } catch (Throwable ignored) {
        }
    }

    static boolean openWorldMapAt(BlockPos pos) {
        if (!worldMapLoaded()) {
            return false;
        }
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getCameraEntity() == null) {
                return false;
            }
            GuiMap map = new GuiMap(null, null, session.getMapProcessor(), minecraft.getCameraEntity());
            boolean centered = centerGuiMap(map, pos.getX() + 0.5, pos.getZ() + 0.5);
            minecraft.setScreen(map);
            return minecraft.screen instanceof GuiMap && centered;
        } catch (Throwable ignored) {
            return false;
        }
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
        try {
            ThirdPartyWaypoints bucket = bucket(dimension);
            if (bucket == null) {
                XaeroLegacyWaypoints.placeFallback(orderId, dimension, pos, label, symbolKind, persistent);
                return;
            }
            bucket.remove(id);
            String symbol = XaeroWaypointSymbols.resolve(label, symbolKind);
            bucket.add(id, new Waypoint(
                    pos.getX(), pos.getY(), pos.getZ(),
                    label, symbol,
                    WaypointColor.GOLD, WaypointPurpose.NORMAL, true));
        } catch (Throwable ignored) {
            XaeroLegacyWaypoints.placeFallback(orderId, dimension, pos, label, symbolKind, persistent);
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
        try {
            ThirdPartyWaypoints bucket = dimension == null ? bucketAny() : bucket(dimension);
            if (bucket != null) {
                bucket.remove(id);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean centerGuiMap(GuiMap map, double blockX, double blockZ) {
        try {
            Field cameraX = GuiMap.class.getDeclaredField("cameraX");
            Field cameraZ = GuiMap.class.getDeclaredField("cameraZ");
            Field shouldReset = GuiMap.class.getDeclaredField("shouldResetCameraPos");
            cameraX.setAccessible(true);
            cameraZ.setAccessible(true);
            shouldReset.setAccessible(true);
            cameraX.setDouble(map, blockX);
            cameraZ.setDouble(map, blockZ);
            shouldReset.setBoolean(map, false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static @Nullable ThirdPartyWaypoints bucket(ResourceKey<Level> dimension) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            return null;
        }
        try {
            MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
            String node = session.getDimensionHelper().getDimensionDirectoryName(dimension);
            MinimapWorldContainer container = root.addSubContainer(root.getPath().resolve(node));
            return container.getThirdPartyWaypointManager().get(ORIGIN);
        } catch (Throwable ignored) {
            return bucketLegacy(session);
        }
    }

    private static @Nullable ThirdPartyWaypoints bucketAny() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            return null;
        }
        ThirdPartyWaypoints direct = bucketLegacy(session);
        if (direct != null) {
            return direct;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return bucket(minecraft.level.dimension());
        }
        return null;
    }

    private static @Nullable ThirdPartyWaypoints bucketLegacy(MinimapSession session) {
        ThirdPartyWaypointManager manager = manager(session);
        return manager == null ? null : manager.get(ORIGIN);
    }

    private static @Nullable ThirdPartyWaypointManager manager(MinimapSession session) {
        if (session == null) {
            return null;
        }
        if (cachedManager != null && cachedSession == session) {
            return cachedManager;
        }
        ThirdPartyWaypointManager resolved = findManagerField(session);
        if (resolved == null) {
            resolved = new ThirdPartyWaypointManager(session);
        }
        cachedSession = session;
        cachedManager = resolved;
        return resolved;
    }

    private static @Nullable ThirdPartyWaypointManager findManagerField(MinimapSession session) {
        Class<?> type = session.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!ThirdPartyWaypointManager.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(session);
                    if (value instanceof ThirdPartyWaypointManager manager) {
                        return manager;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
