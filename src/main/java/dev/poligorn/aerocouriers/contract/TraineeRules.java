package dev.poligorn.aerocouriers.contract;

/**
 * GDD §6.5: while the courier is still on free village contracts, only short NPC routes are allowed.
 */
public final class TraineeRules {
    private TraineeRules() {
    }

    public static boolean onTraineeVillageContracts(int villageDelivered, int traineeFreeContracts) {
        return traineeFreeContracts > 0 && villageDelivered < traineeFreeContracts;
    }

    public static boolean villageDistanceAllowed(double distanceBlocks, int villageDelivered, int traineeFreeContracts,
                                                 int traineeMaxDistance) {
        if (!onTraineeVillageContracts(villageDelivered, traineeFreeContracts)) {
            return true;
        }
        if (traineeMaxDistance <= 0) {
            return true;
        }
        return distanceBlocks <= traineeMaxDistance;
    }

    /** Collateral withdrawn on accept; mirrors {@code ContractManager#accept}. */
    public static long collateralDueAtAccept(long contractCollateral, boolean villageNpc, int villageDelivered,
                                             int traineeFreeContracts) {
        if (villageNpc && onTraineeVillageContracts(villageDelivered, traineeFreeContracts)) {
            return 0L;
        }
        return contractCollateral;
    }
}
