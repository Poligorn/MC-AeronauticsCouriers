package dev.poligorn.aerocouriers.contract;

/**
 * {@code XP = (10 + D/200 + W/50) × M_deadline × M_repeat} (GDD §4.1).
 */
public final class XpCalculator {
    /** Same issuer → same courier deliveries are counted over one real day (GDD §13 "за сутки"). */
    public static final long REPEAT_WINDOW_TICKS = 24L * 60 * 60 * 20;

    private XpCalculator() {
    }

    public static int xp(double distance, double weight, boolean late, int previousFromSameIssuer) {
        double base = 10 + distance / 200.0 + weight / 50.0;
        double deadline = late ? 0.5 : 1.0;
        return (int) Math.floor(base * deadline * repeatMultiplier(previousFromSameIssuer));
    }

    /** 1.0 for the first contract of the day, then −0.1 per repeat down to 0.1. */
    public static double repeatMultiplier(int previousFromSameIssuer) {
        return Math.max(0.1, 1.0 - 0.1 * Math.max(0, previousFromSameIssuer));
    }
}
