package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.Locale;

/**
 * Server-wide collateral difficulty ({@code economy.collateral_profile}). Only operators see the profile name; players
 * only see its effect (no collateral field in {@link #PEACEFUL}, free field in {@link #CUSTOM}).
 */
public enum CollateralProfile {
    PEACEFUL("peace", 0.0),
    CUSTOM("custom", 0.0),
    EASY("easy", 0.25),
    NORMAL("normal", 0.5),
    HARD("hard", 0.75);

    /** Upper bound of the free collateral field in {@link #CUSTOM}, as a share of the cargo value. */
    public static final double CUSTOM_MAX_RATIO = 2.0;

    private final String key;
    private final double ratio;

    CollateralProfile(String key, double ratio) {
        this.key = key;
        this.ratio = ratio;
    }

    public String key() {
        return key;
    }

    /** Minimum P2P share of the cargo value; {@link #CUSTOM} has no floor. */
    public double ratio() {
        return ratio;
    }

    public boolean noCollateral() {
        return this == PEACEFUL;
    }

    public boolean freeCollateral() {
        return this == CUSTOM;
    }

    /** NPC share of the cargo value before {@code collateral_factor}. */
    public double npcRatio() {
        return switch (this) {
            case PEACEFUL -> 0.0;
            case CUSTOM -> CouriersServerConfig.SPEC.isLoaded() ? CouriersServerConfig.NPC_CUSTOM_RATIO.get() : 0.5;
            default -> ratio;
        };
    }

    public String langKey() {
        return "ui.aerocouriers.admin.economy.profile." + key;
    }

    public static CollateralProfile byKey(String raw) {
        if (raw != null) {
            String k = raw.trim().toLowerCase(Locale.ROOT);
            for (CollateralProfile p : values()) {
                if (p.key.equals(k) || p.name().toLowerCase(Locale.ROOT).equals(k)) {
                    return p;
                }
            }
        }
        return NORMAL;
    }

    public static CollateralProfile byOrdinal(int ordinal) {
        CollateralProfile[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NORMAL;
    }

    /** Active profile from the server config ({@link #NORMAL} before the config loads). */
    public static CollateralProfile active() {
        if (!CouriersServerConfig.SPEC.isLoaded()) {
            return NORMAL;
        }
        return byKey(CouriersServerConfig.COLLATERAL_PROFILE.get());
    }

    /** Legacy {@code p2p_min_collateral_ratio} to the nearest profile (one-time migration). */
    public static CollateralProfile fromLegacyRatio(double legacy) {
        if (legacy <= 0.0) {
            return PEACEFUL;
        }
        CollateralProfile best = NORMAL;
        double bestGap = Double.MAX_VALUE;
        for (CollateralProfile p : new CollateralProfile[] {EASY, NORMAL, HARD}) {
            double gap = Math.abs(p.ratio - legacy);
            if (gap < bestGap) {
                bestGap = gap;
                best = p;
            }
        }
        return best;
    }
}
