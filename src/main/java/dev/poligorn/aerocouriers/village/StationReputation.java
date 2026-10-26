package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.List;

/** GDD §6.4: reputation tiers and reward bonus for post stations. */
public final class StationReputation {
    public static final int DELIVERY_DELTA = 1;
    public static final int FAILURE_DELTA = -3;

    private StationReputation() {
    }

    private static int[] thresholds() {
        List<? extends Integer> list = CouriersServerConfig.REPUTATION_TIER_THRESHOLDS.get();
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double[] bonuses() {
        List<? extends Integer> list = CouriersServerConfig.REPUTATION_TIER_BONUS_PERCENT.get();
        return list.stream().mapToInt(Integer::intValue).mapToDouble(p -> p / 100.0).toArray();
    }

    public static int tier(int reputation) {
        int[] thresholds = thresholds();
        double[] bonus = bonuses();
        int tiers = Math.min(thresholds.length, bonus.length);
        int result = 0;
        for (int i = 0; i < tiers; i++) {
            if (reputation >= thresholds[i]) {
                result = i + 1;
            }
        }
        return result;
    }

    /** Reputation points still needed for the next tier, or {@code -1} at max tier. */
    public static int nextThreshold(int reputation) {
        int[] thresholds = thresholds();
        for (int th : thresholds) {
            if (reputation < th) {
                return th;
            }
        }
        return -1;
    }

    /** Multiplier applied to NPC reward on delivery (1.0 … max tier bonus). Uses the higher tier of origin and destination. */
    public static double rewardMultiplier(int originReputation, int destinationReputation) {
        int t = Math.max(tier(originReputation), tier(destinationReputation));
        if (t <= 0) {
            return 1.0;
        }
        double[] bonus = bonuses();
        int index = Math.min(t - 1, bonus.length - 1);
        return 1.0 + bonus[index];
    }

    public static int bonusPercent(int reputation) {
        return (int) Math.round((rewardMultiplier(reputation, reputation) - 1.0) * 100);
    }
}
