package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Cross-dimension badge on board rows (destination dimension when pickup differs). */
public final class BoardDimensionMarker {
    private static final int MARKER_MAX_WIDTH = 72;

    private BoardDimensionMarker() {
    }

    public static boolean isCrossDimension(BoardSnapshotPayload.BoardEntry entry) {
        return !entry.originDimension().equals(entry.destinationDimension());
    }

    public static Component label(BoardSnapshotPayload.BoardEntry entry) {
        return Component.translatable(
                "ui.aerocouriers.row.cross_dimension",
                shortDimensionName(entry.destinationDimension()));
    }

    public static int maxClipWidth() {
        return MARKER_MAX_WIDTH;
    }

    public static Component shortDimensionName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) {
            return Component.translatable("ui.aerocouriers.dimension.short.overworld");
        }
        if (dimension.equals(Level.NETHER)) {
            return Component.translatable("ui.aerocouriers.dimension.short.nether");
        }
        if (dimension.equals(Level.END)) {
            return Component.translatable("ui.aerocouriers.dimension.short.end");
        }
        ResourceLocation id = dimension.location();
        String fallback = humanizePath(id.getPath());
        return Component.translatableWithFallback(
                "ui.aerocouriers.dimension.short." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                fallback);
    }

    private static String humanizePath(String path) {
        String leaf = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (leaf.isEmpty()) {
            return path;
        }
        return leaf;
    }
}
