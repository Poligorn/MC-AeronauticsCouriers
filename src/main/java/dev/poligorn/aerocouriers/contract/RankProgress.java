package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** XP progress within the current rank and promotion gates (GDD §4.2). */
public final class RankProgress {
    private RankProgress() {
    }

    public record State(int xpInRank, int span, int remainingToNext, float fraction, boolean maxRank) {
    }

    public static State state(int xp, LicenseRank rank, List<Integer> thresholds) {
        if (rank.isAdmin() || rank.level() >= 5) {
            return new State(0, 0, 0, 1.0F, true);
        }
        int floor = thresholdForRank(rank.level(), thresholds);
        int ceiling = thresholdForRank(rank.level() + 1, thresholds);
        int span = Math.max(1, ceiling - floor);
        int inRank = Math.max(0, Math.min(span, xp - floor));
        float fraction = inRank / (float) span;
        int remaining = Math.max(0, ceiling - xp);
        return new State(inRank, span, remaining, fraction, false);
    }

    public static LicenseRank nextRank(LicenseRank current) {
        if (current.isAdmin() || current.level() >= 5) {
            return current;
        }
        return LicenseRank.byLevel(current.level() + 1);
    }

    /** Non-XP requirement blocking the next rank (e.g. ace success rate). */
    public static Optional<Component> promotionRequirement(
            int xp,
            int delivered,
            int failed,
            LicenseRank rank,
            List<Integer> thresholds,
            int aceMinSuccessPercent
    ) {
        if (rank.isAdmin() || rank.level() >= 5) {
            return Optional.empty();
        }
        int aceXp = thresholdForRank(5, thresholds);
        if (rank.level() == 4 && xp >= aceXp) {
            double rate = successRate(delivered, failed);
            double required = aceMinSuccessPercent / 100.0;
            if (rate + 1e-9 < required) {
                String need = Integer.toString(aceMinSuccessPercent);
                String have = String.format(java.util.Locale.ROOT, "%.0f", rate * 100.0);
                return Optional.of(Component.translatable("ui.aerocouriers.profile.promotion.ace_success", need, have));
            }
        }
        return Optional.empty();
    }

    public static double successRate(int delivered, int failed) {
        int total = delivered + failed;
        if (total <= 0) {
            return 1.0;
        }
        return delivered / (double) total;
    }

    private static int thresholdForRank(int level, List<Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, Math.min(level - 1, thresholds.size() - 1));
        return thresholds.get(index);
    }
}
