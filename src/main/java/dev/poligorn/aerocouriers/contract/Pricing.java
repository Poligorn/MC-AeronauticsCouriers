package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;

/** P2P money rules (GDD §7.1, §9.6). Amounts are spurs. */
public final class Pricing {
    public static final int MIN_DEADLINE_MINUTES = 30;
    public static final int MAX_DEADLINE_MINUTES = 7 * 24 * 60;
    public static final long TICKS_PER_MINUTE = 20L * 60;

    private Pricing() {
    }

    /** Minimum reward {@code D/100} (anti-spam, and the XP floor of GDD §13). */
    public static long minReward(double distance) {
        return (long) Math.ceil(distance / 100.0);
    }

    /** Burned on creation, on top of the reward the issuer pays into escrow. */
    public static long fee(long reward) {
        return Math.round(reward * CouriersServerConfig.P2P_FEE.get());
    }

    /** Late delivery pays half; the other half burns (GDD §11.2). */
    public static long lateReward(long reward) {
        return reward / 2;
    }

    public static double lateWindow() {
        return CouriersServerConfig.LATE_WINDOW.get();
    }
}
