package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Optional Xaero bridge for order-board map pins (no Xaero types in public API). */
public final class XaeroOrderBridge {
    private XaeroOrderBridge() {
    }

    public static boolean xaeroMinimapPresent() {
        return XaeroOrderWaypoints.minimapLoaded();
    }

    public static boolean xaeroWorldMapPresent() {
        return XaeroOrderWaypoints.worldMapLoaded();
    }

    public static void onHoverDestination(UUID orderId, ResourceKey<Level> dimension, BlockPos pos, String label) {
        XaeroOrderWaypoints.showPeek(orderId, dimension, pos, label);
    }

    public static void onLeaveDestination(UUID orderId) {
        XaeroOrderWaypoints.hidePeek(orderId);
    }

    /** @return whether the world map opened and camera centering succeeded */
    public static boolean openWorldMapAt(BlockPos pos) {
        return XaeroOrderWaypoints.openWorldMapAt(pos);
    }

    public static void syncActiveCourierZones(List<ZoneSyncPayload.Zone> zones) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (ZoneSyncPayload.Zone zone : zones) {
            if (!zone.delivery()) {
                continue;
            }
            seen.add(zone.orderId());
            String label = zone.contract();
            XaeroOrderWaypoints.markPersistent(zone.orderId(), zone.dimension(), zone.pos(), label);
        }
        // Drop stale persistent ids when contracts finish (zones list shrinks).
        // Peek ids are cleared separately on hover leave.
    }

    public static void onLogout() {
        XaeroOrderWaypoints.clearAll();
    }

    public static int distanceBlocks(BlockPos target, ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.level.dimension().equals(dimension)) {
            return -1;
        }
        BlockPos player = minecraft.player.blockPosition();
        return (int) Math.sqrt(player.distSqr(target));
    }
}
