package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.license.LicenseRank;
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

    /** Minimum reward {@code ceil(D / divisor + W × per_weight)} (anti-spam, GDD §7.1; XP floor §13). */
    public static long minReward(double distance, double weight) {
        return (long) Math.ceil(distance / rewardDistanceDivisor() + weight * rewardPerWeight());
    }

    private static double rewardDistanceDivisor() {
        return CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.PRICING_REWARD_DISTANCE_DIVISOR.get()
                : 100.0;
    }

    private static double rewardPerWeight() {
        return CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.PRICING_REWARD_PER_WEIGHT.get()
                : 0.04;
    }

    /** Burned on creation, on top of the reward the issuer pays into escrow. */
    public static long fee(long reward) {
        return Math.round(reward * CouriersServerConfig.P2P_FEE.get());
    }

    /**
     * Minimum P2P collateral ({@code aerocouriers-server.toml} {@code p2p_min_collateral_ratio}): at least the
     * reward, at least {@code reward × ratio}, and at least {@code cargo value × ratio} when value is known.
     */
    public static long minP2pCollateral(long reward, List<ItemStack> manifest) {
        double ratio = CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.P2P_MIN_COLLATERAL_RATIO.get()
                : 0.5;
        long min = Math.max(reward, Math.round(reward * ratio));
        if (manifest != null) {
            long value = ItemValues.of(manifest);
            if (value > 0) {
                min = Math.max(min, Math.round(value * ratio));
            }
        }
        return min;
    }

    public record NpcMoney(long reward, long collateral) {
    }

    /**
     * NPC generator: collateral is never below reward. If raising collateral to the reward would exceed the
     * collateral cap implied by the pre-clamp route rank, the reward is lowered instead.
     */
    public static NpcMoney balanceNpcMoney(
            long reward,
            long collateral,
            CargoSize size,
            Destination origin,
            Destination destination) {
        LicenseRank rankAtCollateral = ContractDistances.routeRequiredRank(collateral, size, origin, destination);
        long cap = RankRules.limits(rankAtCollateral).maxCollateral();
        if (collateral < reward) {
            if (reward > cap) {
                reward = Math.max(1L, cap);
            }
            collateral = Math.max(collateral, reward);
        }
        return new NpcMoney(reward, collateral);
    }

    /** Late delivery pays half; the other half burns (GDD §11.2). */
    public static long lateReward(long reward) {
        return reward / 2;
    }

    public static double lateWindow() {
        return CouriersServerConfig.LATE_WINDOW.get();
    }

    /** {@code 16 + (D/60) × (1 + W/400) + W × per_weight} × 0.8–1.2 (GDD §6.3, {@code [contracts.pricing]}). */
    public static long npcReward(double distance, double weight, RandomSource random) {
        double base = 16.0 + (distance / 60.0) * (1.0 + weight / 400.0) + weight * rewardPerWeight();
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
