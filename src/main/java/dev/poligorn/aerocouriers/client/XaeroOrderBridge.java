package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Optional Xaero bridge for order-board map pins (no Xaero types in public API).
 * {@link XaeroOrderWaypoints} links Xaero classes, so it is touched only after {@code ModList.isLoaded}.
 */
public final class XaeroOrderBridge {
    private static boolean sawXaeroWhilePreview;
    private static @Nullable ResourceKey<Level> trackedDimension;

    private XaeroOrderBridge() {
    }

    public static boolean xaeroMinimapPresent() {
        return XaeroMapPicker.minimapPresent();
    }

    public static boolean xaeroWorldMapPresent() {
        return XaeroMapPicker.worldMapPresent();
    }

    public static void onHoverDestination(UUID orderId, ResourceKey<Level> dimension, BlockPos pos, String label) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        try {
            XaeroOrderWaypoints.showPeek(orderId, dimension, pos, label);
        } catch (Throwable ignored) {
        }
    }

    public static void onLeaveDestination(UUID orderId) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        try {
            XaeroOrderWaypoints.hidePeek(orderId);
        } catch (Throwable ignored) {
        }
    }

    /** Map-pin click: temporary preview waypoint (at most one) plus optional world map. */
    public static void onPreviewDestination(UUID orderId, ResourceKey<Level> dimension, BlockPos pos, String label) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        try {
            XaeroOrderWaypoints.setPreview(orderId, dimension, pos, label);
        } catch (Throwable ignored) {
        }
    }

    /** @return whether the world map opened and camera centering succeeded */
    public static boolean openWorldMapAt(BlockPos pos) {
        if (!xaeroWorldMapPresent()) {
            return false;
        }
        try {
            return XaeroOrderWaypoints.openWorldMapAt(pos);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void onBoardSnapshot(BoardSnapshotPayload payload) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        Set<UUID> relevant = new HashSet<>();
        for (BoardSnapshotPayload.BoardEntry entry : payload.board()) {
            if (!entry.state().isFinal()) {
                relevant.add(entry.id());
            }
        }
        for (BoardSnapshotPayload.BoardEntry entry : payload.mine()) {
            if (!entry.state().isFinal()) {
                relevant.add(entry.id());
            }
        }
        try {
            XaeroOrderWaypoints.prunePreview(relevant);
        } catch (Throwable ignored) {
        }
    }

    public static void syncActiveCourierZones(List<ZoneSyncPayload.Zone> zones) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        try {
            Set<UUID> seen = new HashSet<>();
            for (ZoneSyncPayload.Zone zone : zones) {
                seen.add(zone.orderId());
                XaeroWaypointSymbols.Kind kind = zone.delivery()
                        ? XaeroWaypointSymbols.Kind.DELIVERY
                        : XaeroWaypointSymbols.Kind.PICKUP;
                String label = net.minecraft.network.chat.Component.translatable(zone.delivery()
                        ? "ui.aerocouriers.waypoint.delivery" : "ui.aerocouriers.waypoint.pickup", zone.contract())
                        .getString();
                XaeroOrderWaypoints.markPersistent(zone.orderId(), zone.dimension(), zone.pos(), label, kind);
            }
            XaeroOrderWaypoints.syncPersistent(seen);
        } catch (Throwable ignored) {
        }
    }

    public static void onLogout() {
        sawXaeroWhilePreview = false;
        trackedDimension = null;
        if (!xaeroMinimapPresent()) {
            return;
        }
        try {
            XaeroOrderWaypoints.clearPreview();
            XaeroOrderWaypoints.clearAll();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Drops the board map-pin preview waypoint after the player leaves Xaero's map or minimap UI, and on
     * dimension changes as a safety net.
     */
    static void onClientTick(ClientTickEvent.Post event) {
        if (!xaeroMinimapPresent()) {
            return;
        }
        try {
            tickPreview();
        } catch (Throwable ignored) {
        }
    }

    private static void tickPreview() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            ResourceKey<Level> dimension = minecraft.level.dimension();
            if (trackedDimension != null && !trackedDimension.equals(dimension)) {
                XaeroOrderWaypoints.clearPreview();
                sawXaeroWhilePreview = false;
            }
            trackedDimension = dimension;
        } else {
            trackedDimension = null;
        }

        if (!XaeroOrderWaypoints.hasActivePreview()) {
            sawXaeroWhilePreview = false;
            return;
        }
        if (isXaeroScreen(minecraft.screen)) {
            sawXaeroWhilePreview = true;
            return;
        }
        if (sawXaeroWhilePreview) {
            XaeroOrderWaypoints.clearPreview();
            sawXaeroWhilePreview = false;
        }
    }

    private static boolean isXaeroScreen(@Nullable Screen screen) {
        if (screen == null) {
            return false;
        }
        return screen.getClass().getName().startsWith("xaero.");
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
