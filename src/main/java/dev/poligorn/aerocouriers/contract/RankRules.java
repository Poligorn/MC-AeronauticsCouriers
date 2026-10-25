package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.List;
import java.util.OptionalInt;

/**
 * Rank thresholds and limits (GDD §4.2). Thresholds come from the server config, with GDD defaults as fallback
 * when the config is not loaded (e.g. before the server starts).
 */
public final class RankRules {
    private static final List<Integer> DEFAULT_RANK_XP = List.of(0, 300, 1500, 5000, 15000);
    private static final double DEFAULT_ACE_MIN_SUCCESS = 0.90;

    private RankRules() {
    }

    public static LicenseRank rankFor(CourierProfile profile) {
        List<Integer> thresholds = thresholds();
        int level = 1;
        for (int i = 1; i < thresholds.size() && i < 5; i++) {
            if (profile.xp() >= thresholds.get(i)) {
                level = i + 1;
            }
        }
        if (level == 5 && profile.successRate() < aceMinSuccess()) {
            level = 4;
        }
        return LicenseRank.byLevel(level);
    }

    /** XP needed for the next rank, empty for rank V. */
    public static OptionalInt nextThreshold(LicenseRank current) {
        List<Integer> thresholds = thresholds();
        int nextIndex = current.isAdmin() ? -1 : current.level();
        if (nextIndex < 0 || nextIndex >= thresholds.size()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(thresholds.get(nextIndex));
    }

    private static List<Integer> thresholds() {
        if (!CouriersServerConfig.SPEC.isLoaded()) {
            return DEFAULT_RANK_XP;
        }
        List<? extends Integer> configured = CouriersServerConfig.RANK_XP.get();
        return configured.size() >= 5 ? List.copyOf(configured) : DEFAULT_RANK_XP;
    }

    private static double aceMinSuccess() {
        return CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.ACE_MIN_SUCCESS.get()
                : DEFAULT_ACE_MIN_SUCCESS;
    }
}
