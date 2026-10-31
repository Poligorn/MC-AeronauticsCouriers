package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.board.BoardAcceptUi;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
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
                entry.reward() >= VillageBoardPolicy.EXPENSIVE_REWARD,
                entry.villageRare(),
                size == CargoSize.L || size == CargoSize.XL,
                !inProgress && BoardCardRenderer.deadlineWarn(entry),
                locked,
                inProgress,
                player,
                courierName == null ? "" : courierName);
    }

    public int paper() {
        if (locked) {
            return CorkBoardGui.LOCK_PAPER;
        }
        if (expensive && rare) {
            return 0xFFF4D8B0;
        }
        if (expensive) {
            return CorkBoardGui.GOLD_PAPER;
        }
        if (rare) {
            return CorkBoardGui.PINK_PAPER;
        }
        return CorkBoardGui.CREAM;
    }

    public ResourceLocation paperSprite() {
        if (locked) {
            return AerocouriersGuiSprites.CORK_PAPER_LOCKED;
        }
        if (expensive && rare) {
            return AerocouriersGuiSprites.CORK_PAPER_BLEND;
        }
        if (expensive) {
            return AerocouriersGuiSprites.CORK_PAPER_GOLD;
        }
        if (rare) {
            return AerocouriersGuiSprites.CORK_PAPER_PINK;
        }
        return AerocouriersGuiSprites.CORK_PAPER;
    }

    public int border() {
        if (inProgress) {
            return CorkBoardGui.TEAL;
        }
        if (expiring) {
            return CorkBoardGui.RED;
        }
        if (locked) {
            return 0xFF8A8680;
        }
        if (expensive) {
            return 0xFFC4A020;
        }
        if (rare) {
            return 0xFFC070A0;
        }
        return 0xFFB0A070;
    }

    public boolean hideReward() {
        return locked;
    }
}
