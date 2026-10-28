package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Pickup location display for board rows (hidden until the server reveals coordinates). */
public final class BoardEntryPickup {
    private BoardEntryPickup() {
    }

    public static Component routeOriginLabel(BoardSnapshotPayload.BoardEntry entry) {
        if (entry.originRevealed()) {
            return Component.literal(entry.originLabel());
        }
        return Component.translatable("ui.aerocouriers.row.pickup_hidden");
    }

    public static Component mapTooltipPickup(BoardSnapshotPayload.BoardEntry entry) {
        if (entry.originRevealed()) {
            return formatPos(entry.originPos(), entry.originDimension());
        }
        return Component.translatable("ui.aerocouriers.row.pickup_hidden");
    }

    private static Component formatPos(BlockPos pos, ResourceKey<Level> dimension) {
        return Component.translatable("ui.aerocouriers.row.coords",
                pos.getX(), pos.getY(), pos.getZ(), dimension.location().toString());
    }
}
