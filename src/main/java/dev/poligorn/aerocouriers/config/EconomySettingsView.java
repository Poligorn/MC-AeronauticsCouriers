package dev.poligorn.aerocouriers.config;

import dev.poligorn.aerocouriers.contract.ItemValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only {@code key=value} lines for the operator «Экономика» list «Связанные настройки». */
public final class EconomySettingsView {
    public static final String RANK_CAPS = "rank_caps";
    public static final String P2P_RATIO = "contracts.p2p_min_collateral_ratio";
    public static final String COLLATERAL_FACTOR = "item_values.collateral_factor";
    public static final String NPC_CUSTOM_RATIO = "economy.npc_custom_ratio";
    public static final String TRAINEE_FREE = "progression.trainee_free_contracts";
    public static final String TRAINEE_BONUS = "progression.trainee_bonus";

    private EconomySettingsView() {
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        if (!CouriersServerConfig.SPEC.isLoaded()) {
            return out;
        }
        out.add(P2P_RATIO + "=" + num(CouriersServerConfig.P2P_MIN_COLLATERAL_RATIO.get()));
        out.add(COLLATERAL_FACTOR + "=" + num(ItemValues.collateralFactor()));
        out.add(NPC_CUSTOM_RATIO + "=" + num(CouriersServerConfig.NPC_CUSTOM_RATIO.get()));
        out.add(RANK_CAPS + "=200 / 600 / 2000 / 6000 / ∞");
        out.add("economy.emerald_value_spurs=" + CouriersServerConfig.EMERALD_VALUE_SPURS.get());
        out.add("economy.backend=" + CouriersServerConfig.ECONOMY_BACKEND.get());
        out.add("contracts.p2p_fee=" + num(CouriersServerConfig.P2P_FEE.get()));
        out.add("contracts.pricing.fee_multiplier=" + num(CouriersServerConfig.PRICING_FEE_MULTIPLIER_S.get()) + " / "
                + num(CouriersServerConfig.PRICING_FEE_MULTIPLIER_M.get()) + " / "
                + num(CouriersServerConfig.PRICING_FEE_MULTIPLIER_L.get()) + " / "
                + num(CouriersServerConfig.PRICING_FEE_MULTIPLIER_XL.get()));
        out.add("contracts.pricing.reward_distance_divisor=" + CouriersServerConfig.PRICING_REWARD_DISTANCE_DIVISOR.get());
        out.add("contracts.pricing.reward_per_weight=" + num(CouriersServerConfig.PRICING_REWARD_PER_WEIGHT.get()));
        out.add("contracts.late_window=" + num(CouriersServerConfig.LATE_WINDOW.get()));
        out.add(TRAINEE_FREE + "=" + CouriersServerConfig.TRAINEE_FREE_CONTRACTS.get());
        out.add(TRAINEE_BONUS + "=" + CouriersServerConfig.TRAINEE_BONUS.get());
        return out;
    }

    private static String num(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }
}
