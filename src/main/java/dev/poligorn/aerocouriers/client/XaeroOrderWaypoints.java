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
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * Xaero-only third-party order waypoints ({@code aerocouriers:orders}, temp). Loaded via compileOnly jars.
 */
final class XaeroOrderWaypoints {
    static final ResourceLocation ORIGIN = AeronauticsCouriers.id("orders");

    private static final Set<String> PERSISTENT_IDS = new HashSet<>();
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
        if (!minimapLoaded()) {
            return;
        }
        try {
            ThirdPartyWaypoints bucket = bucket();
            if (bucket == null) {
                return;
            }
            String id = waypointId(orderId);
            if (PERSISTENT_IDS.contains(id)) {
                return;
            }
            bucket.remove(id);
            bucket.add(id, new Waypoint(
                    pos.getX(), pos.getY(), pos.getZ(),
                    label, "✉",
                    WaypointColor.GOLD, WaypointPurpose.NORMAL, true));
        } catch (Throwable ignored) {
            // Xaero API is not stable across versions
        }
    }

    static void hidePeek(UUID orderId) {
        if (!minimapLoaded()) {
            return;
        }
        try {
            String id = waypointId(orderId);
            if (PERSISTENT_IDS.contains(id)) {
                return;
            }
            ThirdPartyWaypoints bucket = bucket();
            if (bucket != null) {
                bucket.remove(id);
            }
        } catch (Throwable ignored) {
        }
    }

    static void markPersistent(UUID orderId, ResourceKey<Level> dimension, BlockPos pos, String label) {
        if (!minimapLoaded()) {
            return;
        }
        try {
            String id = waypointId(orderId);
            PERSISTENT_IDS.add(id);
            ThirdPartyWaypoints bucket = bucket();
            if (bucket == null) {
                return;
            }
            bucket.remove(id);
            bucket.add(id, new Waypoint(
                    pos.getX(), pos.getY(), pos.getZ(),
                    label, "✉",
                    WaypointColor.GOLD, WaypointPurpose.NORMAL, true));
        } catch (Throwable ignored) {
        }
    }

    static void clearPersistent(UUID orderId) {
        PERSISTENT_IDS.remove(waypointId(orderId));
        hidePeek(orderId);
    }

    static void clearAll() {
        PERSISTENT_IDS.clear();
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

    /**
     * Opens the world map and tries to center the camera on block coordinates via package-private fields.
     *
     * @return {@code true} if the map opened and camera centering was applied
     */
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

    private static @Nullable ThirdPartyWaypoints bucket() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            return null;
        }
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
