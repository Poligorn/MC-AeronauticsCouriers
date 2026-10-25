package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** Rank limits from GDD §4.2; {@code Long.MAX_VALUE} / {@code Double.POSITIVE_INFINITY} mean "no limit". */
    public record Limits(long maxCollateral, CargoSize maxSize, double maxDistance, int maxActive) {
    }

    private static final Map<LicenseRank, Limits> LIMITS = new EnumMap<>(Map.of(
            LicenseRank.RANK1, new Limits(200, CargoSize.S, 1_500, 1),
            LicenseRank.RANK2, new Limits(600, CargoSize.M, 4_000, 2),
            LicenseRank.RANK3, new Limits(2_000, CargoSize.L, 10_000, 2),
            LicenseRank.RANK4, new Limits(6_000, CargoSize.L, Double.POSITIVE_INFINITY, 3),
            LicenseRank.RANK5, new Limits(Long.MAX_VALUE, CargoSize.L, Double.POSITIVE_INFINITY, 3),
            LicenseRank.ADMIN, new Limits(Long.MAX_VALUE, CargoSize.L, Double.POSITIVE_INFINITY, 3)));

    public static Limits limits(LicenseRank rank) {
        return LIMITS.get(rank);
    }

    /** {@code requiredRank = max(by collateral, by container, by distance)} (GDD §5.3). */
    public static LicenseRank requiredRank(long collateral, CargoSize size, double distance) {
        for (int level = 1; level <= 5; level++) {
            LicenseRank rank = LicenseRank.byLevel(level);
            Limits limits = limits(rank);
            if (collateral <= limits.maxCollateral() && size.ordinal() <= limits.maxSize().ordinal()
                    && distance <= limits.maxDistance()) {
                return rank;
            }
        }
        return LicenseRank.RANK5;
    }

    /** Admin licenses satisfy every requirement. */
    public static boolean satisfies(LicenseRank have, LicenseRank required) {
        return have.isAdmin() || have.level() >= required.level();
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
