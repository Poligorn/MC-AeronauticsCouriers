package dev.poligorn.aerocouriers.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xaero.common.HudMod;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/**
 * All Minimap 26.x types live here. Loaded only after {@code ModList.isLoaded("xaerominimap")}.
 * Xaero uses Y=32767 and {@code yIncluded=false} when height is unknown (world-map click without
 * a heightmap).
 */
final class XaeroMinimapAccess {
    static final int UNKNOWN_Y = 32767;

    private XaeroMinimapAccess() {
    }

    static boolean isUsable() {
        MinimapSession session = session();
        return session != null && session.getWorldManager() != null
                && session.getWorldManager().getCurrentWorld() != null;
    }

    static List<XaeroPickedWaypoint> listCurrentWorld() {
        List<XaeroPickedWaypoint> out = new ArrayList<>();
        MinimapSession session = session();
        if (session == null) {
            return out;
        }
        MinimapWorld world = session.getWorldManager().getCurrentWorld();
        if (world == null) {
            return out;
        }
        for (WaypointSet set : world.getIterableWaypointSets()) {
            if (set == null) {
                continue;
            }
            for (Waypoint waypoint : set.getWaypoints()) {
                if (waypoint == null || waypoint.isThirdParty() || waypoint.isEffectivelyDeleted()) {
                    continue;
                }
                WaypointPurpose purpose = waypoint.getPurpose();
                if (purpose != null && purpose.isDeath()) {
                    continue;
                }
                out.add(from(waypoint));
            }
        }
        return out;
    }

    static boolean openWaypointMenu(Screen parent) {
        MinimapSession session = session();
        HudMod mod = HudMod.INSTANCE;
        if (session == null || mod == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new xaero.common.gui.GuiWaypoints(mod, session, parent, parent));
        return minecraft.screen != null && minecraft.screen.getClass().getName().startsWith("xaero.");
    }

    static int fallbackY() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.blockPosition().getY();
        }
        return 64;
    }

    private static MinimapSession session() {
        return BuiltInHudModules.MINIMAP.getCurrentSession();
    }

    private static XaeroPickedWaypoint from(Waypoint waypoint) {
        boolean known = waypoint.isYIncluded() && waypoint.getY() != UNKNOWN_Y;
        int y = known ? waypoint.getY() : fallbackY();
        String name = waypoint.getName();
        if (name == null || name.isBlank()) {
            name = "Waypoint";
        }
        return new XaeroPickedWaypoint(
                name, waypoint.getX(), y, waypoint.getZ(), !known,
                waypoint.getCreatedAt(), System.identityHashCode(waypoint));
    }
}
