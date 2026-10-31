package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** P2P money rules (GDD §7.1, §9.6) and NPC station formulas (GDD §6.3). Results are in the active currency. */
public final class Pricing {
    public static final int MIN_DEADLINE_MINUTES = 30;
    public static final int MAX_DEADLINE_MINUTES = 7 * 24 * 60;
    public static final long TICKS_PER_MINUTE = 20L * 60;

    private Pricing() {
    }

    /** Minimum reward {@code ceil(D / divisor + W × per_weight)} (anti-spam, GDD §7.1; XP floor §13). */
    public static long minReward(double distance, double weight) {
        return MoneyScale.fromSpurs(Math.ceil(distance / rewardDistanceDivisor() + weight * rewardPerWeight()));
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

    /** Burned on creation: {@code round(reward × p2p_fee × fee_multiplier[size])}. */
    public static long fee(long reward, CargoSize size) {
        double rate = CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.P2P_FEE.get()
                : 0.05;
        return Math.round(reward * rate * CouriersServerConfig.feeMultiplier(size));
    }

    public static long minP2pCollateral(long reward, List<ItemStack> manifest) {
        return minP2pCollateral(CollateralProfile.active(), reward, manifest);
    }

    /**
     * Minimum P2P collateral for {@code profile}: none for peace and custom; otherwise at least the reward and at
     * least {@code cargo value × profile share}.
     */
    public static long minP2pCollateral(CollateralProfile profile, long reward, List<ItemStack> manifest) {
        if (profile.noCollateral() || profile.freeCollateral()) {
            return 0L;
        }
        long min = reward;
        long value = manifest == null ? 0L : ItemValues.of(manifest);
        if (value > 0) {
            min = Math.max(min, Math.round(value * profile.ratio()));
        }
        return min;
    }

    /** Highest accepted P2P collateral: {@code 200 %} of value in custom, {@code 0} in peace, unbounded otherwise. */
    public static long maxP2pCollateral(CollateralProfile profile, long reward, List<ItemStack> manifest) {
        if (profile.noCollateral()) {
            return 0L;
        }
        if (!profile.freeCollateral()) {
            return Long.MAX_VALUE;
        }
        long value = manifest == null ? 0L : ItemValues.of(manifest);
        long base = value > 0 ? value : reward;
        return Math.max(0L, Math.round(base * CollateralProfile.CUSTOM_MAX_RATIO));
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
        return npcReward(distance, weight, 0L, CargoSize.S, random);
    }

    /**
     * Village NPC reward: base formula plus {@code valueSpurs × village_value_reward_factor} and the size bonus,
     * then ×0.8–1.2. {@code EXPENSIVE_REWARD} stays 1000 in the active currency.
     */
    public static long npcReward(double distance, double weight, long valueSpurs, CargoSize size, RandomSource random) {
        return npcReward(distance, weight, valueSpurs, 0L, size, random);
    }

    /**
     * {@code valuableSpurs} is the table-scaled value of {@code valuable} rows (uncapped). The rest of
     * {@code valueSpurs} is ordinary cargo and is capped by {@code village_value_reward_cap}.
     */
    public static long npcReward(double distance, double weight, long valueSpurs, long valuableSpurs,
                                 CargoSize size, RandomSource random) {
        double base = 16.0 + (distance / 60.0) * (1.0 + weight / 400.0) + weight * rewardPerWeight();
        double factor = CouriersServerConfig.villageValueRewardFactor();
        long valuable = Math.max(0L, valuableSpurs);
        long bulk = Math.max(0L, Math.max(0L, valueSpurs) - valuable);
        double bulkTerm = bulk * factor;
        double cap = CouriersServerConfig.villageValueRewardCap();
        if (cap > 0.0) {
            bulkTerm = Math.min(bulkTerm, cap);
        }
        base += bulkTerm + valuable * factor;
        base += CouriersServerConfig.villageSizeRewardBonus(size == null ? CargoSize.S : size);
        double jitter = 0.8 + random.nextDouble() * 0.4;
        return MoneyScale.fromSpurs(Math.max(1, Math.round(base * jitter)));
    }

    public static long npcCollateral(List<ItemStack> manifest, double weight) {
        return npcCollateral(CollateralProfile.active(), manifest, weight);
    }

    /**
     * NPC collateral: {@code profile share × cargo value × collateral_factor} (GDD §8.3), or the same share of
     * {@code ≈2×} weight when value is zero; {@code 0} in peace.
     */
    public static long npcCollateral(CollateralProfile profile, List<ItemStack> manifest, double weight) {
        return npcCollateral(profile, manifest, weight, 0L);
    }

    /**
     * Same as {@link #npcCollateral(CollateralProfile, List, double)} but uses a pre-scaled cargo value (table
     * {@code value_multiplier} / {@code valuable}) when {@code valueSpurs} is positive.
     */
    public static long npcCollateral(CollateralProfile profile, List<ItemStack> manifest, double weight,
                                     long valueSpurs) {
        double share = profile.npcRatio();
        if (share <= 0.0) {
            return 0L;
        }
        long value = valueSpurs > 0L ? MoneyScale.fromSpurs(valueSpurs) : ItemValues.of(manifest);
        if (value > 0) {
            return Math.max(1, Math.round(value * share * ItemValues.collateralFactor()));
        }
        return Math.max(1, Math.round(MoneyScale.fromSpurs(Math.max(1, Math.round(weight * 2.0))) * share));
    }
}
