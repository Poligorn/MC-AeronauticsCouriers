package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.license.LicenseRank;

/** Route length for pricing, rank requirements, and UI (GDD §4.1 {@code D}). */
public final class ContractDistances {
    private static final double DEFAULT_CROSS_DIMENSION_BLOCKS = 3000.0;

    private ContractDistances() {
    }

    public static boolean isCrossDimension(Destination origin, Destination destination) {
        return !origin.dimension().equals(destination.dimension());
    }

    /** Blocks used for reward, XP, timers, and rank-by-distance when pickup and delivery differ in dimension. */
    public static double crossDimensionBlocks() {
        if (!CouriersServerConfig.SPEC.isLoaded()) {
            return DEFAULT_CROSS_DIMENSION_BLOCKS;
        }
        return CouriersServerConfig.CROSS_DIMENSION_DISTANCE.get();
    }

    public static double pricingBlocks(Destination origin, Destination destination) {
        if (isCrossDimension(origin, destination)) {
            return crossDimensionBlocks();
        }
        return origin.distanceTo(destination);
    }

    public static LicenseRank routeRequiredRank(long collateral, CargoSize size, Destination origin,
                                                Destination destination) {
        double distance = pricingBlocks(origin, destination);
        LicenseRank rank = RankRules.requiredRank(collateral, size, distance);
        if (isCrossDimension(origin, destination) && rank.level() < LicenseRank.RANK4.level()) {
            return LicenseRank.RANK4;
        }
        return rank;
    }
}
