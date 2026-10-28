package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;

/** Xaero &lt; 26.2 fallback: temporary DESTINATION waypoints in the player's current set. */
final class XaeroLegacyWaypoints {
    private XaeroLegacyWaypoints() {
    }

    static void placeFallback(
            UUID orderId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String label,
            XaeroWaypointSymbols.Kind symbolKind,
            boolean persistent,
            WaypointColor color
    ) {
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) {
                return;
            }
            var world = session.getWorldManager().getCurrentWorld();
            if (world == null) {
                return;
            }
            WaypointSet set = world.getCurrentWaypointSet();
            if (set == null) {
                return;
            }
            String symbol = XaeroWaypointSymbols.resolve(label, symbolKind);
            WaypointPurpose purpose = persistent ? WaypointPurpose.NORMAL : WaypointPurpose.DESTINATION;
            Waypoint waypoint = new Waypoint(
                    pos.getX(), pos.getY(), pos.getZ(),
                    label, symbol,
                    color, purpose, true);
            for (Waypoint existing : set.getWaypoints()) {
                if (label.equals(existing.getName()) && existing.isTemporary()) {
                    set.remove(existing);
                }
            }
            set.add(waypoint);
        } catch (Throwable t) {
            AeronauticsCouriers.LOGGER.debug("Xaero legacy waypoint fallback failed: {}", t.toString());
        }
    }
}
