package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.board.BoardAcceptUi;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record CorkNoteStyle(
        boolean expensive,
        boolean rare,
        boolean largeCargo,
        boolean expiring,
        boolean locked,
        boolean inProgress,
        LicenseRank playerRank,
        String courierName
) {
    public static CorkNoteStyle of(BoardSnapshotPayload.BoardEntry entry, int playerRankLevel, String courierName) {
        boolean inProgress = entry.state().isActive();
        LicenseRank player = playerRankLevel <= 0
                ? LicenseRank.ADMIN
                : LicenseRank.byLevel(Math.max(1, Math.min(5, playerRankLevel)));
        boolean locked = !inProgress && BoardAcceptUi.rowRankLocked(entry, player);
        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        return new CorkNoteStyle(
                entry.expensive(),
                entry.villageRare(),
                size == CargoSize.L || size == CargoSize.XL,
                !inProgress && BoardCardRenderer.deadlineWarn(entry),
                locked,
                inProgress,
                player,
                courierName == null ? "" : courierName);
    }

    public ResourceLocation paperSprite(boolean compact) {
        return CorkSprites.paper(this, compact);
    }

    public boolean hideReward() {
        return locked;
    }
}
