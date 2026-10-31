package dev.poligorn.aerocouriers.config;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code aerocouriers-server.toml} (GDD §15). Keys mirror the GDD; only what the current iterations read is
 * consumed by code, the rest is declared so pack makers can already tune it.
 */
public final class CouriersServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue VILLAGE_GENERATION_INTERVAL_MIN;
    public static final ModConfigSpec.IntValue VILLAGE_BOARD_MAX;
    public static final ModConfigSpec.DoubleValue P2P_MIN_COLLATERAL_RATIO;
    public static final ModConfigSpec.DoubleValue P2P_FEE;
    public static final ModConfigSpec.IntValue MAX_OPEN_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue PAUSE_ON_LOGOUT_SINGLEPLAYER;
    public static final ModConfigSpec.DoubleValue LATE_WINDOW;
    public static final ModConfigSpec.IntValue PRICING_REWARD_DISTANCE_DIVISOR;
    public static final ModConfigSpec.DoubleValue PRICING_REWARD_PER_WEIGHT;
    public static final ModConfigSpec.DoubleValue PRICING_FEE_MULTIPLIER_S;
    public static final ModConfigSpec.DoubleValue PRICING_FEE_MULTIPLIER_M;
    public static final ModConfigSpec.DoubleValue PRICING_FEE_MULTIPLIER_L;
    public static final ModConfigSpec.DoubleValue PRICING_FEE_MULTIPLIER_XL;

    public static final ModConfigSpec.IntValue SPAWN_TRIGGER_RADIUS;
    public static final ModConfigSpec.DoubleValue DELIVERY_MAX_SPEED;
    public static final ModConfigSpec.IntValue DELIVERY_STILL_CHECKS;
    public static final ModConfigSpec.DoubleValue CARGO_MASS_SCALE;
    public static final ModConfigSpec.IntValue CARGO_DEBUG_LOG_INTERVAL;
    public static final ModConfigSpec.IntValue SPAWN_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue CARGO_SLOTS_S;
    public static final ModConfigSpec.IntValue CARGO_SLOTS_M;
    public static final ModConfigSpec.IntValue CARGO_SLOTS_L;
    public static final ModConfigSpec.IntValue CARGO_SLOTS_XL;
    public static final ModConfigSpec.IntValue CARGO_MAX_WEIGHT_S;
    public static final ModConfigSpec.IntValue CARGO_MAX_WEIGHT_M;
    public static final ModConfigSpec.IntValue CARGO_MAX_WEIGHT_L;
    public static final ModConfigSpec.IntValue CARGO_MAX_WEIGHT_XL;

    public static final ModConfigSpec.IntValue CARGO_RANK_REQUIRED_XL;

    public static final ModConfigSpec.BooleanValue PIRACY_ENABLED;
    public static final ModConfigSpec.DoubleValue PIRACY_BREAK_SECONDS;
    public static final ModConfigSpec.DoubleValue PIRACY_BLAST_RESISTANCE;
    public static final ModConfigSpec.DoubleValue PIRACY_DROP_RATIO;

    public static final ModConfigSpec.IntValue STATION_REGISTER_RADIUS;
    public static final ModConfigSpec.IntValue STATION_VILLAGE_DEDUP_RADIUS;
    public static final ModConfigSpec.BooleanValue STATIONS_IN_VILLAGES;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> REPUTATION_TIER_THRESHOLDS;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> REPUTATION_TIER_BONUS_PERCENT;
    public static final ModConfigSpec.DoubleValue VILLAGE_RARE_ROLL_CHANCE;
    public static final ModConfigSpec.IntValue VILLAGE_SIZE_WEIGHT_S;
    public static final ModConfigSpec.IntValue VILLAGE_SIZE_WEIGHT_M;
    public static final ModConfigSpec.IntValue VILLAGE_SIZE_WEIGHT_L;
    public static final ModConfigSpec.IntValue VILLAGE_SIZE_WEIGHT_XL;
    public static final ModConfigSpec.IntValue VILLAGE_S_STACKS_MAX;
    public static final ModConfigSpec.DoubleValue VILLAGE_VALUE_REWARD_FACTOR;
    public static final ModConfigSpec.DoubleValue VILLAGE_VALUABLE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue VILLAGE_SIZE_BONUS_S;
    public static final ModConfigSpec.DoubleValue VILLAGE_SIZE_BONUS_M;
    public static final ModConfigSpec.DoubleValue VILLAGE_SIZE_BONUS_L;
    public static final ModConfigSpec.DoubleValue VILLAGE_SIZE_BONUS_XL;

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RANK_XP;
    public static final ModConfigSpec.DoubleValue ACE_MIN_SUCCESS;
    public static final ModConfigSpec.IntValue TRAINEE_FREE_CONTRACTS;
    public static final ModConfigSpec.IntValue TRAINEE_BONUS;
    public static final ModConfigSpec.IntValue TRAINEE_MAX_DISTANCE;

    public static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION_ORDERS;
    public static final ModConfigSpec.DoubleValue CROSS_DIMENSION_ORDER_CHANCE;
    public static final ModConfigSpec.IntValue CROSS_DIMENSION_DISTANCE;

    public static final ModConfigSpec.IntValue EMERALD_VALUE_SPURS;
    public static final ModConfigSpec.ConfigValue<String> ECONOMY_BACKEND;
    public static final ModConfigSpec.ConfigValue<String> COLLATERAL_PROFILE;
    public static final ModConfigSpec.DoubleValue NPC_CUSTOM_RATIO;

    public static final ModConfigSpec.IntValue MIGRATION_XP_CADET;
    public static final ModConfigSpec.IntValue MIGRATION_XP_SECOND;
    public static final ModConfigSpec.IntValue MIGRATION_XP_FIRST;
    public static final ModConfigSpec.IntValue MIGRATION_XP_CAPTAIN;

    public static final ModConfigSpec.BooleanValue ALLOW_SELF_ACCEPT;

    public static final ModConfigSpec.BooleanValue REQUIRE_TABLET;

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> NOTIFY_WARN_MINUTES;
    public static final ModConfigSpec.IntValue NOTIFY_FAR_CONTAINER_BLOCKS;
    public static final ModConfigSpec.IntValue NOTIFY_FAR_COOLDOWN_S;

    public static final ModConfigSpec.BooleanValue TUTORIAL_ENABLED;
    public static final ModConfigSpec.IntValue TUTORIAL_STATION_RADIUS;
    public static final ModConfigSpec.IntValue TUTORIAL_XP;
    public static final ModConfigSpec.IntValue LICENSE_RESTORE_PRICE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("contracts");
        VILLAGE_GENERATION_INTERVAL_MIN = b.comment("Minutes between NPC contract generation passes")
                .defineInRange("village_generation_interval_min", 10, 1, 1440);
        VILLAGE_BOARD_MAX = b.comment("Maximum NPC contracts on the board at once")
                .defineInRange("village_board_max", 12, 0, 1000);
        P2P_MIN_COLLATERAL_RATIO = b.comment("Legacy: read once to migrate to economy.collateral_profile, then ignored")
                .defineInRange("p2p_min_collateral_ratio", 0.5, 0.0, 10.0);
        P2P_FEE = b.comment("Player contract fee as a share of the reward (burned)")
                .defineInRange("p2p_fee", 0.05, 0.0, 1.0);
        MAX_OPEN_PER_PLAYER = b.defineInRange("max_open_per_player", 10, 0, 1000);
        PAUSE_ON_LOGOUT_SINGLEPLAYER = b.comment("Pause contract timers while the courier is offline (singleplayer only)")
                .define("pause_on_logout_singleplayer", true);
        LATE_WINDOW = b.comment("Late delivery window as a share of the deadline (reward and XP x0.5)")
                .defineInRange("late_window", 0.25, 0.0, 10.0);
        b.push("pricing");
        PRICING_REWARD_DISTANCE_DIVISOR = b.comment("Distance term in P2P minimum reward: distance / this value (spurs)")
                .defineInRange("reward_distance_divisor", 100, 1, 1_000_000);
        PRICING_REWARD_PER_WEIGHT = b.comment("Extra spur reward per unit of cargo weight (NPC reward and P2P minimum)")
                .defineInRange("reward_per_weight", 0.04, 0.0, 1000.0);
        PRICING_FEE_MULTIPLIER_S = b.comment("P2P issuer commission multiplier for container size S (fee = reward × p2p_fee × multiplier)")
                .defineInRange("fee_multiplier_s", 1.0, 0.0, 100.0);
        PRICING_FEE_MULTIPLIER_M = b.comment("P2P issuer commission multiplier for container size M")
                .defineInRange("fee_multiplier_m", 1.5, 0.0, 100.0);
        PRICING_FEE_MULTIPLIER_L = b.comment("P2P issuer commission multiplier for container size L")
                .defineInRange("fee_multiplier_l", 2.5, 0.0, 100.0);
        PRICING_FEE_MULTIPLIER_XL = b.comment("P2P issuer commission multiplier for container size XL")
                .defineInRange("fee_multiplier_xl", 4.0, 0.0, 100.0);
        b.pop();
        ALLOW_CROSS_DIMENSION_ORDERS = b.comment("NPC generator may pair stations in different dimensions")
                .define("allow_cross_dimension_orders", true);
        CROSS_DIMENSION_ORDER_CHANCE = b.comment("Share of candidate NPC pairs that may span dimensions (when allowed and another dimension has stations)")
                .defineInRange("cross_dimension_order_chance", 0.15, 0.0, 1.0);
        CROSS_DIMENSION_DISTANCE = b.comment("Treated route length in blocks for cross-dimension reward, XP, timers, and rank-by-distance")
                .defineInRange("cross_dimension_distance", 3000, 100, 500_000);
        b.pop();

        b.push("cargo");
        SPAWN_TRIGGER_RADIUS = b.comment("Courier distance to the pickup point that spawns the container, blocks")
                .defineInRange("spawn_trigger_radius", 64, 8, 512);
        DELIVERY_MAX_SPEED = b.comment("Container must be slower than this to count as delivered, blocks/second")
                .defineInRange("delivery_max_speed", 0.5, 0.0, 100.0);
        DELIVERY_STILL_CHECKS = b.comment("Consecutive once-per-second checks required for delivery")
                .defineInRange("delivery_still_checks", 3, 1, 100);
        CARGO_MASS_SCALE = b.comment("Mass scale k used by the physics datapack generator (documentation only for now: ",
                        "the shipped physics_block_properties JSON is precomputed for k = 0.25)")
                .defineInRange("cargo_mass_scale", 0.25, 0.0, 10.0);
        CARGO_DEBUG_LOG_INTERVAL = b.comment("Prototype: log world position and velocity of debug containers every N ticks (0 = off)")
                .defineInRange("debug_log_interval_ticks", 20, 0, 72000);
        SPAWN_SEARCH_RADIUS = b.comment("How far from the pickup point to look for a free spot with a solid base, blocks")
                .defineInRange("spawn_search_radius", 8, 0, 32);
        CARGO_SLOTS_S = b.defineInRange("slots_s", 9, 1, 256);
        CARGO_SLOTS_M = b.defineInRange("slots_m", 18, 1, 256);
        CARGO_SLOTS_L = b.defineInRange("slots_l", 27, 1, 256);
        CARGO_SLOTS_XL = b.defineInRange("slots_xl", 45, 1, 256);
        CARGO_MAX_WEIGHT_S = b.defineInRange("max_weight_s", 256, 1, 1_000_000);
        CARGO_MAX_WEIGHT_M = b.defineInRange("max_weight_m", 768, 1, 1_000_000);
        CARGO_MAX_WEIGHT_L = b.defineInRange("max_weight_l", 2048, 1, 1_000_000);
        CARGO_MAX_WEIGHT_XL = b.defineInRange("max_weight_xl", 4096, 1, 1_000_000);
        CARGO_RANK_REQUIRED_XL = b.comment("Minimum courier license rank (1..5) to accept XL containers")
                .defineInRange("rank_required_xl", 4, 1, 5);
        b.pop();

        b.push("progression");
        RANK_XP = b.comment("XP thresholds for ranks I..V")
                .defineList("rank_xp", List.of(0, 300, 1500, 5000, 15000), () -> 0,
                        o -> o instanceof Integer i && i >= 0);
        ACE_MIN_SUCCESS = b.comment("Minimum success rate for rank V")
                .defineInRange("ace_min_success", 0.90, 0.0, 1.0);
        TRAINEE_FREE_CONTRACTS = b.defineInRange("trainee_free_contracts", 3, 0, 100);
        TRAINEE_BONUS = b.comment("One-time advance paid after the trainee contracts, spurs (divided by emerald_value_spurs when emeralds are the currency)")
                .defineInRange("trainee_bonus", 100, 0, 1_000_000);
        TRAINEE_MAX_DISTANCE = b.comment("While trainee_free_contracts remain, NPC orders longer than this cannot be accepted (0 = no limit)")
                .defineInRange("trainee_max_distance", 800, 0, 50_000);
        b.pop();

        b.push("economy");
        ECONOMY_BACKEND = b.comment(
                        "Money backend: auto (Numismatics bank account when the mod is loaded, else emeralds),",
                        "numismatics (force bank account; falls back to emeralds with a warning if the mod is missing),",
                        "internal (emeralds in the player inventory; 1 emerald block = 9)")
                .define("backend", "auto");
        EMERALD_VALUE_SPURS = b.comment(
                        "Without the Numismatics backend, divide spur prices by this to get emeralds. Numismatics keeps the spur numbers.")
                .defineInRange("emerald_value_spurs", 8, 1, 4096);
        COLLATERAL_PROFILE = b.comment(
                        "Collateral difficulty: peace (no collateral), easy (25% of cargo value), normal (50%), hard (75%),",
                        "custom (issuer picks 0..200% of value). Replaces contracts.p2p_min_collateral_ratio, which is read once",
                        "to migrate older worlds. NPC collateral = profile share × value × item_values collateral_factor.")
                .define("collateral_profile", "normal",
                        o -> o instanceof String s && java.util.Arrays.stream(
                                dev.poligorn.aerocouriers.contract.CollateralProfile.values())
                                .anyMatch(p -> p.key().equalsIgnoreCase(s.trim())));
        NPC_CUSTOM_RATIO = b.comment("NPC collateral share of cargo value when collateral_profile = custom")
                .defineInRange("npc_custom_ratio", 0.5, 0.0, 10.0);
        b.pop();

        b.push("piracy");
        b.comment("Cargo damage (GDD 9.7, formerly pvp_rules). A broken or blown-up container fails the contract as DAMAGED,",
                "returns collateral and reward to the issuer and drops part of the manifest into the world.");
        PIRACY_ENABLED = b.comment("false = containers are unbreakable and explosion-proof")
                .define("enabled", true);
        PIRACY_BREAK_SECONDS = b.comment("Unused: break time follows obsidian-like hardness (50) and pickaxe tags")
                .defineInRange("break_seconds", 10.0, 0.05, 3600.0);
        PIRACY_BLAST_RESISTANCE = b.comment("Explosion resistance when piracy is enabled (stone 6, obsidian 1200)")
                .defineInRange("blast_resistance", 6.0, 0.0, 3_600_000.0);
        PIRACY_DROP_RATIO = b.comment("Share of every manifest stack that drops when the container is damaged; the rest is destroyed")
                .defineInRange("drop_ratio", 0.5, 0.0, 1.0);
        b.pop();

        b.push("stations");
        STATION_REGISTER_RADIUS = b.comment("A licensed player this close to a post station adds it to the atlas, blocks")
                .defineInRange("register_radius", 32, 4, 256);
        STATION_VILLAGE_DEDUP_RADIUS = b.comment("Village-generated stations within this radius of an existing station become a cartography table")
                .defineInRange("village_dedup_radius", 96, 16, 256);
        STATIONS_IN_VILLAGES = b.comment("Add post stations to village structures (takes effect for newly generated villages)")
                .define("generate_in_villages", true);
        REPUTATION_TIER_THRESHOLDS = b.comment("Reputation at each tier (GDD §6.4); pairs with reputation_tier_bonus_percent")
                .defineList("reputation_tier_thresholds", List.of(10, 25, 50), () -> 0,
                        o -> o instanceof Integer i && i > 0);
        REPUTATION_TIER_BONUS_PERCENT = b.comment("NPC reward bonus percent at each tier (same order as thresholds)")
                .defineList("reputation_tier_bonus_percent", List.of(10, 20, 30), () -> 0,
                        o -> o instanceof Integer i && i >= 0);
        VILLAGE_RARE_ROLL_CHANCE = b.comment("Chance to replace one manifest stack with rare cargo when origin reputation meets a rare table row (0 = off)")
                .defineInRange("village_rare_roll_chance", 0.35, 0.0, 1.0);
        VILLAGE_SIZE_WEIGHT_S = b.comment("Relative chance to roll container S at a station that may use every unlocked size")
                .defineInRange("village_size_weight_s", 45, 0, 10_000);
        VILLAGE_SIZE_WEIGHT_M = b.comment("Relative chance to roll container M (ignored when the reputation tier or station policy forbids M)")
                .defineInRange("village_size_weight_m", 30, 0, 10_000);
        VILLAGE_SIZE_WEIGHT_L = b.comment("Relative chance to roll container L (ignored when the reputation tier or station policy forbids L)")
                .defineInRange("village_size_weight_l", 17, 0, 10_000);
        VILLAGE_SIZE_WEIGHT_XL = b.comment("Relative chance to roll container XL (ignored when the reputation tier or station policy forbids XL)")
                .defineInRange("village_size_weight_xl", 8, 0, 10_000);
        VILLAGE_S_STACKS_MAX = b.comment("S village orders stay light: 1..this many stacks (never more than the S slot cap)")
                .defineInRange("village_s_stacks_max", 4, 1, 64);
        VILLAGE_VALUE_REWARD_FACTOR = b.comment("NPC reward adds cargo value (spurs, after table value_multiplier) × this factor")
                .defineInRange("village_value_reward_factor", 0.35, 0.0, 100.0);
        VILLAGE_VALUABLE_MULTIPLIER = b.comment("Default value_multiplier for village_cargo rows with valuable=true and no explicit multiplier")
                .defineInRange("village_valuable_multiplier", 5.0, 1.0, 100.0);
        VILLAGE_SIZE_BONUS_S = b.comment("Extra NPC reward in spurs for a village order of this container size (before 0.8–1.2 jitter)")
                .defineInRange("village_size_bonus_s", 0.0, 0.0, 100_000.0);
        VILLAGE_SIZE_BONUS_M = b.comment("Extra NPC reward in spurs for village M orders")
                .defineInRange("village_size_bonus_m", 40.0, 0.0, 100_000.0);
        VILLAGE_SIZE_BONUS_L = b.comment("Extra NPC reward in spurs for village L orders")
                .defineInRange("village_size_bonus_l", 180.0, 0.0, 100_000.0);
        VILLAGE_SIZE_BONUS_XL = b.comment("Extra NPC reward in spurs for village XL orders")
                .defineInRange("village_size_bonus_xl", 360.0, 0.0, 100_000.0);
        b.pop();

        b.push("migration");
        b.comment("Starting XP granted when a legacy kubejs:pilot_license_<rank> is migrated (never lowers XP)");
        MIGRATION_XP_CADET = b.defineInRange("xp_cadet", 0, 0, Integer.MAX_VALUE);
        MIGRATION_XP_SECOND = b.defineInRange("xp_second", 300, 0, Integer.MAX_VALUE);
        MIGRATION_XP_FIRST = b.defineInRange("xp_first", 1500, 0, Integer.MAX_VALUE);
        MIGRATION_XP_CAPTAIN = b.defineInRange("xp_captain", 5000, 0, Integer.MAX_VALUE);
        b.pop();

        b.push("debug");
        ALLOW_SELF_ACCEPT = b.comment("Test-only: allow a player to accept their own contract (singleplayer loop)")
                .define("allow_self_accept", false);
        b.pop();

        b.push("access");
        REQUIRE_TABLET = b.comment("Job board (J, inventory button, /couriers ui, post station, tablet use) requires the order tablet")
                .define("require_tablet", true);
        b.pop();

        b.push("notifications");
        NOTIFY_WARN_MINUTES = b.comment("Warn the courier this many minutes before the deadline (orders shorter than 10 min are skipped)")
                .defineList("warn_minutes", List.of(5, 2, 1), () -> 1, o -> o instanceof Integer i && i > 0);
        NOTIFY_FAR_CONTAINER_BLOCKS = b.comment("Urgent notice when the courier is farther than this from a picked-up container, blocks")
                .defineInRange("far_container_blocks", 64, 8, 4096);
        NOTIFY_FAR_COOLDOWN_S = b.comment("Minimum seconds between two «container is far» notices for one order")
                .defineInRange("far_notice_cooldown_s", 30, 1, 3600);
        b.pop();

        b.push("tutorial");
        TUTORIAL_ENABLED = b.comment("Onboarding for new couriers: welcome window, training order, checklist.",
                        "Same value as the admin panel toggle and /couriers admin server tutorial on|off")
                .define("tutorial_enabled", true);
        TUTORIAL_STATION_RADIUS = b.comment("Pickup point A is the nearest post station within this radius; otherwise 6-10 blocks from the player")
                .defineInRange("station_search_radius", 512, 16, 4096);
        TUTORIAL_XP = b.comment("XP for finishing the training order (granted once)")
                .defineInRange("xp", 100, 0, 100_000);
        b.pop();

        b.push("license");
        LICENSE_RESTORE_PRICE = b.comment("Price to restore a lost license, spurs (divided by emerald_value_spurs with emeralds)")
                .defineInRange("restore_price", 25, 0, 1_000_000);
        b.pop();

        SPEC = b.build();
    }

    public static double feeMultiplier(CargoSize size) {
        if (!SPEC.isLoaded()) {
            return defaultFeeMultiplier(size);
        }
        return switch (size) {
            case S -> PRICING_FEE_MULTIPLIER_S.get();
            case M -> PRICING_FEE_MULTIPLIER_M.get();
            case L -> PRICING_FEE_MULTIPLIER_L.get();
            case XL -> PRICING_FEE_MULTIPLIER_XL.get();
        };
    }

    private static double defaultFeeMultiplier(CargoSize size) {
        return switch (size) {
            case S -> 1.0;
            case M -> 1.5;
            case L -> 2.5;
            case XL -> 4.0;
        };
    }

    public static int containerSlots(CargoSize size) {
        if (!SPEC.isLoaded()) {
            return defaultSlots(size);
        }
        return switch (size) {
            case S -> CARGO_SLOTS_S.get();
            case M -> CARGO_SLOTS_M.get();
            case L -> CARGO_SLOTS_L.get();
            case XL -> CARGO_SLOTS_XL.get();
        };
    }

    public static int containerMaxWeight(CargoSize size) {
        if (!SPEC.isLoaded()) {
            return defaultMaxWeight(size);
        }
        return switch (size) {
            case S -> CARGO_MAX_WEIGHT_S.get();
            case M -> CARGO_MAX_WEIGHT_M.get();
            case L -> CARGO_MAX_WEIGHT_L.get();
            case XL -> CARGO_MAX_WEIGHT_XL.get();
        };
    }

    public static int rankRequiredXl() {
        if (!SPEC.isLoaded()) {
            return 4;
        }
        return CARGO_RANK_REQUIRED_XL.get();
    }

    private static int defaultSlots(CargoSize size) {
        return switch (size) {
            case S -> 9;
            case M -> 18;
            case L -> 27;
            case XL -> 45;
        };
    }

    private static int defaultMaxWeight(CargoSize size) {
        return switch (size) {
            case S -> 256;
            case M -> 768;
            case L -> 2048;
            case XL -> 4096;
        };
    }

    public static int villageSizeWeight(CargoSize size) {
        if (!SPEC.isLoaded()) {
            return defaultVillageSizeWeight(size);
        }
        return switch (size) {
            case S -> VILLAGE_SIZE_WEIGHT_S.get();
            case M -> VILLAGE_SIZE_WEIGHT_M.get();
            case L -> VILLAGE_SIZE_WEIGHT_L.get();
            case XL -> VILLAGE_SIZE_WEIGHT_XL.get();
        };
    }

    private static int defaultVillageSizeWeight(CargoSize size) {
        return switch (size) {
            case S -> 45;
            case M -> 30;
            case L -> 17;
            case XL -> 8;
        };
    }

    public static int villageSStacksMax() {
        int cap = SPEC.isLoaded() ? VILLAGE_S_STACKS_MAX.get() : 4;
        return Math.max(1, Math.min(containerSlots(CargoSize.S), cap));
    }

    public static double villageValueRewardFactor() {
        return SPEC.isLoaded() ? VILLAGE_VALUE_REWARD_FACTOR.get() : 0.35;
    }

    public static double villageValuableMultiplier() {
        return SPEC.isLoaded() ? VILLAGE_VALUABLE_MULTIPLIER.get() : 5.0;
    }

    public static double villageSizeRewardBonus(CargoSize size) {
        if (!SPEC.isLoaded()) {
            return defaultVillageSizeBonus(size);
        }
        return switch (size) {
            case S -> VILLAGE_SIZE_BONUS_S.get();
            case M -> VILLAGE_SIZE_BONUS_M.get();
            case L -> VILLAGE_SIZE_BONUS_L.get();
            case XL -> VILLAGE_SIZE_BONUS_XL.get();
        };
    }

    private static double defaultVillageSizeBonus(CargoSize size) {
        return switch (size) {
            case S -> 0.0;
            case M -> 40.0;
            case L -> 180.0;
            case XL -> 360.0;
        };
    }

    private CouriersServerConfig() {
    }
}
