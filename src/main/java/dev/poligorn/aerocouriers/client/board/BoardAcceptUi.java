package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Client-side accept button state and rank lock styling (GDD §5.3, §7.2). */
public final class BoardAcceptUi {
    private BoardAcceptUi() {
    }

    public static int countActiveHauls(List<BoardSnapshotPayload.BoardEntry> mine) {
        int count = 0;
        for (BoardSnapshotPayload.BoardEntry entry : mine) {
            if ("courier".equals(entry.role()) && entry.state().isActive()) {
                count++;
            }
        }
        return count;
    }

    public static Optional<AcceptEligibility.Denial> denial(
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            int activeHauls
    ) {
        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        boolean crossDimension = entry.crossDimension();
        Optional<AcceptEligibility.Denial> base = AcceptEligibility.check(
                profile.rank(),
                entry.requiredRank(),
                entry.collateral(),
                size,
                entry.distance(),
                crossDimension,
                entry.villageCustomer(),
                profile.villageDelivered(),
                profile.traineeFreeContracts(),
                profile.traineeMaxDistance());
        if (base.isPresent()) {
            return base;
        }
        int maxActive = RankRules.limits(profile.rank()).maxActive();
        if (activeHauls >= maxActive) {
            return Optional.of(new AcceptEligibility.Denial("rank_limit_active", maxActive));
        }
        return Optional.empty();
    }

    public static boolean rowRankLocked(BoardSnapshotPayload.BoardEntry entry, LicenseRank courierRank) {
        if (entry.state() != ContractState.OPEN) {
            return false;
        }
        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        boolean crossDimension = entry.crossDimension();
        return AcceptEligibility.boardRowLocked(
                courierRank, entry.requiredRank(), entry.collateral(), size, entry.distance(), crossDimension);
    }

    public static Component denialMessage(Optional<AcceptEligibility.Denial> denial) {
        return denial.map(AcceptEligibility.Denial::message)
                .orElse(Component.empty());
    }
}
