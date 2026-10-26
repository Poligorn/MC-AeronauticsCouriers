package dev.poligorn.aerocouriers.village;

/** GDD §6.4: reputation tiers and reward bonus for post stations. */
public final class StationReputation {
    public static final int DELIVERY_DELTA = 1;
    public static final int FAILURE_DELTA = -3;
    private static final int[] TIER_THRESHOLDS = {10, 25, 50};
    private static final double[] TIER_BONUS = {0.10, 0.20, 0.30};

    private StationReputation() {
    }

    public static int tier(int reputation) {
        if (reputation >= TIER_THRESHOLDS[2]) {
            return 3;
        }
        if (reputation >= TIER_THRESHOLDS[1]) {
            return 2;
        }
        if (reputation >= TIER_THRESHOLDS[0]) {
            return 1;
        }
        return 0;
    }

    /** Multiplier applied to NPC reward on delivery (1.0 … 1.3). Uses the higher tier of origin and destination. */
    public static double rewardMultiplier(int originReputation, int destinationReputation) {
        int t = Math.max(tier(originReputation), tier(destinationReputation));
        if (t <= 0) {
            return 1.0;
        }
        return 1.0 + TIER_BONUS[t - 1];
    }

    public static int bonusPercent(int reputation) {
        return (int) Math.round((rewardMultiplier(reputation, reputation) - 1.0) * 100);
    }
}
