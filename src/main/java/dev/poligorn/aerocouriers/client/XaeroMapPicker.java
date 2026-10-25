package dev.poligorn.aerocouriers.client;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Optional Xaero bridge for the stamp window. This class has no Xaero types in its API so it is
 * safe to call when the mods are absent. Access classes are only touched after {@link ModList#isLoaded}.
 */
public final class XaeroMapPicker {
    public static final String MINIMAP_ID = "xaerominimap";
    public static final String WORLDMAP_ID = "xaeroworldmap";

    private static @Nullable Pending pending;

    private XaeroMapPicker() {
    }

    public static boolean minimapPresent() {
        return ModList.get().isLoaded(MINIMAP_ID);
    }

    public static boolean worldMapPresent() {
        return ModList.get().isLoaded(WORLDMAP_ID);
    }

    public static boolean available() {
        if (!minimapPresent()) {
            return false;
        }
        try {
            return XaeroMinimapAccess.isUsable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean canOpenMap() {
        if (!available()) {
            return false;
        }
        if (worldMapPresent()) {
            try {
                if (XaeroWorldMapAccess.isUsable()) {
                    return true;
                }
            } catch (Throwable ignored) {
                // older World Map — still offer the minimap waypoint screen
            }
        }
        return true;
    }

    public static List<XaeroPickedWaypoint> listWaypoints() {
        if (!available()) {
            return List.of();
        }
        try {
            return XaeroMinimapAccess.listCurrentWorld();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /**
     * Opens World Map if present, otherwise the minimap waypoint screen. Snapshots current-world
     * waypoints; when the player leaves every Xaero screen the newest new waypoint is applied
     * and the same stamp instance is restored.
     */
    public static boolean openMapForPick(StampCreateScreen stamp) {
        if (!canOpenMap()) {
            return false;
        }
        List<XaeroPickedWaypoint> before;
        try {
            before = XaeroMinimapAccess.listCurrentWorld();
        } catch (Throwable ignored) {
            before = List.of();
        }
        boolean opened = false;
        if (worldMapPresent()) {
            try {
                opened = XaeroWorldMapAccess.openWorldMap();
            } catch (Throwable ignored) {
                opened = false;
            }
        }
        if (!opened) {
            try {
                opened = XaeroMinimapAccess.openWaypointMenu(null);
            } catch (Throwable ignored) {
                opened = false;
            }
        }
        if (!opened) {
            return false;
        }
        pending = new Pending(stamp, identities(before));
        return true;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        if (pending == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (isXaeroScreen(screen)) {
            return;
        }
        Pending done = pending;
        pending = null;
        newestSince(done.before).ifPresent(done.stamp::applyPicked);
        if (screen != done.stamp) {
            minecraft.setScreen(done.stamp);
        } else {
            done.stamp.refreshAfterPick();
        }
    }

    private static Optional<XaeroPickedWaypoint> newestSince(Set<Integer> before) {
        List<XaeroPickedWaypoint> after;
        try {
            after = XaeroMinimapAccess.listCurrentWorld();
        } catch (Throwable ignored) {
            return Optional.empty();
        }
        XaeroPickedWaypoint best = null;
        for (XaeroPickedWaypoint waypoint : after) {
            if (before.contains(waypoint.identity())) {
                continue;
            }
            if (best == null || waypoint.createdAt() >= best.createdAt()) {
                best = waypoint;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Set<Integer> identities(List<XaeroPickedWaypoint> waypoints) {
        Set<Integer> ids = new HashSet<>();
        for (XaeroPickedWaypoint waypoint : waypoints) {
            ids.add(waypoint.identity());
        }
        return ids;
    }

    private static boolean isXaeroScreen(@Nullable Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        return name.startsWith("xaero.");
    }

    private record Pending(StampCreateScreen stamp, Set<Integer> before) {
    }
}
