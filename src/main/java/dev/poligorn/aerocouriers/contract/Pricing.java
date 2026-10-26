package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** P2P money rules (GDD §7.1, §9.6) and NPC station formulas (GDD §6.3). Amounts are spurs. */
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

    /** {@code 16 + (D/60) × (1 + W/400)} × 0.8–1.2 (GDD §6.3). */
    public static long npcReward(double distance, double weight, RandomSource random) {
        double base = 16.0 + (distance / 60.0) * (1.0 + weight / 400.0);
        double factor = 0.8 + random.nextDouble() * 0.4;
        return Math.max(1, Math.round(base * factor));
    }

    /** NPC collateral from cargo value (GDD §8.3) or {@code ≈2×} weight when value is zero. */
    public static long npcCollateral(List<ItemStack> manifest, double weight) {
        long value = ItemValues.of(manifest);
        if (value > 0) {
            return Math.max(1, Math.round(value * ItemValues.collateralFactor()));
        }
        return Math.max(1, Math.round(weight * 2.0));
    }
}
