package dev.poligorn.aerocouriers.client.route;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Waybill "Show on map" preview (README decision 10): a dashed A→B line without erasing or rebuilding, alive only
 * while the Xaero map opened for it stays open.
 */
public final class RoutePreview {
    private static @Nullable Preview active;

    private RoutePreview() {
    }

    public static @Nullable Preview active() {
        return active;
    }

    public static void show(Preview preview) {
        active = preview;
    }

    public static void clear() {
        active = null;
    }

    public record Preview(ResourceKey<Level> dimension, BlockPos from, BlockPos to, int radius, String label) {
        public double distance() {
            return Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        }
    }
}
