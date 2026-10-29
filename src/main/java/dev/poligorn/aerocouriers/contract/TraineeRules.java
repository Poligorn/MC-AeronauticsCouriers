package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.license.LicenseRank;

/**
 * GDD §6.5: while the courier is still on free village contracts, only short NPC routes are allowed.
 * The limit applies only at license rank I. Rank II and above, and an admin license, are not trainees.
 */
public final class TraineeRules {
    private TraineeRules() {
    }

    public static boolean applies(LicenseRank rank, int villageDelivered, int traineeFreeContracts) {
        if (rank == null || rank.isAdmin() || rank.level() > 1) {
            return false;
        }
        return traineeFreeContracts > 0 && villageDelivered < traineeFreeContracts;
    }

    public static boolean villageDistanceAllowed(LicenseRank rank, double distanceBlocks, int villageDelivered,
                                                 int traineeFreeContracts, int traineeMaxDistance) {
        if (!applies(rank, villageDelivered, traineeFreeContracts)) {
            return true;
        }
        if (traineeMaxDistance <= 0) {
            return true;
        }
        return distanceBlocks <= traineeMaxDistance;
    }

    /** Collateral withdrawn on accept; mirrors {@code ContractManager#accept}. */
    public static long collateralDueAtAccept(LicenseRank rank, long contractCollateral, boolean villageNpc,
                                             int villageDelivered, int traineeFreeContracts) {
        if (villageNpc && applies(rank, villageDelivered, traineeFreeContracts)) {
            return 0L;
        }
        return contractCollateral;
    }
}
