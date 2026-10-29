package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Whether a courier may accept a contract (GDD §4.2, §5.3, §6.5). Shared by server accept and client UI hints.
 */
public final class AcceptEligibility {
    private AcceptEligibility() {
    }

    public record Denial(String errorKey, Object... args) {
        public Component message() {
            return Component.translatable("contract.aerocouriers.error." + errorKey, args);
        }

        public ContractManager.Outcome toOutcome() {
            return new ContractManager.Outcome(false, message(), java.util.Optional.empty());
        }
    }

    public static Optional<Denial> check(
            LicenseRank courierRank,
            LicenseRank requiredRank,
            long collateral,
            CargoSize size,
            double distance,
            boolean crossDimension,
            boolean villageNpc,
            int villageDelivered,
            int traineeFreeContracts,
            int traineeMaxDistance
    ) {
        if (crossDimension && !courierRank.isAdmin() && courierRank.level() < LicenseRank.RANK4.level()) {
            return Optional.of(new Denial("rank_cross_dimension", LicenseRank.RANK4.displayName()));
        }
        RankRules.Limits limits = RankRules.limits(courierRank);
        if (!RankRules.satisfies(courierRank, requiredRank)) {
            return Optional.of(new Denial("rank_too_low", requiredRank.displayName()));
        }
        if (collateral > limits.maxCollateral()) {
            return Optional.of(new Denial("rank_limit_collateral",
                    MoneyFormat.component(limits.maxCollateral())));
        }
        if (size.ordinal() > limits.maxSize().ordinal()) {
            return Optional.of(new Denial("rank_limit_size", sizeLabel(limits.maxSize())));
        }
        if (!crossDimension && distance > limits.maxDistance()) {
            String dist = limits.maxDistance() == Double.POSITIVE_INFINITY
                    ? "∞"
                    : Integer.toString((int) limits.maxDistance());
            return Optional.of(new Denial("rank_limit_distance", dist));
        }
        if (villageNpc && !TraineeRules.villageDistanceAllowed(courierRank, distance, villageDelivered,
                traineeFreeContracts, traineeMaxDistance)) {
            return Optional.of(new Denial("trainee_too_far", traineeMaxDistance));
        }
        return Optional.empty();
    }

    /** True when the board row should look locked (rank or limits). */
    public static boolean boardRowLocked(
            LicenseRank courierRank,
            LicenseRank requiredRank,
            long collateral,
            CargoSize size,
            double distance,
            boolean crossDimension
    ) {
        if (crossDimension && !courierRank.isAdmin() && courierRank.level() < LicenseRank.RANK4.level()) {
            return true;
        }
        if (!RankRules.satisfies(courierRank, requiredRank)) {
            return true;
        }
        RankRules.Limits limits = RankRules.limits(courierRank);
        if (collateral > limits.maxCollateral()
                || size.ordinal() > limits.maxSize().ordinal()) {
            return true;
        }
        return !crossDimension && distance > limits.maxDistance();
    }

    public static String formatSize(CargoSize size) {
        return size.name();
    }

    public static Component sizeLabel(CargoSize size) {
        return Component.translatable("cargo.aerocouriers.size." + size.getSerializedName());
    }

    public static String formatDistance(double blocks) {
        if (blocks == Double.POSITIVE_INFINITY || blocks >= 1_000_000) {
            return "∞";
        }
        return Integer.toString((int) blocks);
    }
}
