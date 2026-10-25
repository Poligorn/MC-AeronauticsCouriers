package dev.poligorn.aerocouriers.config;

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

    public static final ModConfigSpec.IntValue SPAWN_TRIGGER_RADIUS;
    public static final ModConfigSpec.DoubleValue DELIVERY_MAX_SPEED;
    public static final ModConfigSpec.IntValue DELIVERY_STILL_CHECKS;
    public static final ModConfigSpec.DoubleValue CARGO_MASS_SCALE;
    public static final ModConfigSpec.IntValue CARGO_DEBUG_LOG_INTERVAL;
    public static final ModConfigSpec.IntValue SPAWN_SEARCH_RADIUS;

    public static final ModConfigSpec.BooleanValue PIRACY_ENABLED;
    public static final ModConfigSpec.DoubleValue PIRACY_BREAK_SECONDS;
    public static final ModConfigSpec.DoubleValue PIRACY_BLAST_RESISTANCE;
    public static final ModConfigSpec.DoubleValue PIRACY_DROP_RATIO;

    public static final ModConfigSpec.IntValue STATION_REGISTER_RADIUS;
    public static final ModConfigSpec.BooleanValue STATIONS_IN_VILLAGES;

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RANK_XP;
    public static final ModConfigSpec.DoubleValue ACE_MIN_SUCCESS;
    public static final ModConfigSpec.IntValue TRAINEE_FREE_CONTRACTS;
    public static final ModConfigSpec.IntValue TRAINEE_BONUS;

    public static final ModConfigSpec.IntValue EMERALD_VALUE_SPURS;

    public static final ModConfigSpec.IntValue MIGRATION_XP_CADET;
    public static final ModConfigSpec.IntValue MIGRATION_XP_SECOND;
    public static final ModConfigSpec.IntValue MIGRATION_XP_FIRST;
    public static final ModConfigSpec.IntValue MIGRATION_XP_CAPTAIN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("contracts");
        VILLAGE_GENERATION_INTERVAL_MIN = b.comment("Minutes between NPC contract generation passes")
                .defineInRange("village_generation_interval_min", 10, 1, 1440);
        VILLAGE_BOARD_MAX = b.comment("Maximum NPC contracts on the board at once")
                .defineInRange("village_board_max", 12, 0, 1000);
        P2P_MIN_COLLATERAL_RATIO = b.comment("Minimum collateral as a share of the estimated cargo value")
                .defineInRange("p2p_min_collateral_ratio", 0.5, 0.0, 10.0);
        P2P_FEE = b.comment("Player contract fee as a share of the reward (burned)")
                .defineInRange("p2p_fee", 0.05, 0.0, 1.0);
        MAX_OPEN_PER_PLAYER = b.defineInRange("max_open_per_player", 10, 0, 1000);
        PAUSE_ON_LOGOUT_SINGLEPLAYER = b.comment("Pause contract timers while the courier is offline (singleplayer only)")
                .define("pause_on_logout_singleplayer", true);
        LATE_WINDOW = b.comment("Late delivery window as a share of the deadline (reward and XP x0.5)")
                .defineInRange("late_window", 0.25, 0.0, 10.0);
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
        SPAWN_SEARCH_RADIUS = b.comment("How far from the pickup point to look for a free 3x3x3 spot with a solid base, blocks")
                .defineInRange("spawn_search_radius", 8, 0, 32);
        b.pop();

        b.push("progression");
        RANK_XP = b.comment("XP thresholds for ranks I..V")
                .defineList("rank_xp", List.of(0, 300, 1500, 5000, 15000), () -> 0,
                        o -> o instanceof Integer i && i >= 0);
        ACE_MIN_SUCCESS = b.comment("Minimum success rate for rank V")
                .defineInRange("ace_min_success", 0.90, 0.0, 1.0);
        TRAINEE_FREE_CONTRACTS = b.defineInRange("trainee_free_contracts", 3, 0, 100);
        TRAINEE_BONUS = b.comment("One-time advance paid after the trainee contracts, spurs")
                .defineInRange("trainee_bonus", 100, 0, 1_000_000);
        b.pop();

        b.push("economy");
        EMERALD_VALUE_SPURS = b.defineInRange("emerald_value_spurs", 8, 1, 4096);
        b.pop();

        b.push("piracy");
        b.comment("Cargo damage (GDD 9.7, formerly pvp_rules). A broken or blown-up container fails the contract as DAMAGED,",
                "returns collateral and reward to the issuer and drops part of the manifest into the world.");
        PIRACY_ENABLED = b.comment("false = containers are unbreakable and explosion-proof")
                .define("enabled", true);
        PIRACY_BREAK_SECONDS = b.comment("Time to break a container with any tool, seconds")
                .defineInRange("break_seconds", 10.0, 0.05, 3600.0);
        PIRACY_BLAST_RESISTANCE = b.comment("Explosion resistance of a container (stone 6, obsidian 1200)")
                .defineInRange("blast_resistance", 9.0, 0.0, 3_600_000.0);
        PIRACY_DROP_RATIO = b.comment("Share of every manifest stack that drops when the container is damaged; the rest is destroyed")
                .defineInRange("drop_ratio", 0.5, 0.0, 1.0);
        b.pop();

        b.push("stations");
        STATION_REGISTER_RADIUS = b.comment("A licensed player this close to a post station adds it to the atlas, blocks")
                .defineInRange("register_radius", 32, 4, 256);
        STATIONS_IN_VILLAGES = b.comment("Add post stations to village structures (takes effect for newly generated villages)")
                .define("generate_in_villages", true);
        b.pop();

        b.push("migration");
        b.comment("Starting XP granted when a legacy kubejs:pilot_license_<rank> is migrated (never lowers XP)");
        MIGRATION_XP_CADET = b.defineInRange("xp_cadet", 0, 0, Integer.MAX_VALUE);
        MIGRATION_XP_SECOND = b.defineInRange("xp_second", 300, 0, Integer.MAX_VALUE);
        MIGRATION_XP_FIRST = b.defineInRange("xp_first", 1500, 0, Integer.MAX_VALUE);
        MIGRATION_XP_CAPTAIN = b.defineInRange("xp_captain", 5000, 0, Integer.MAX_VALUE);
        b.pop();

        SPEC = b.build();
    }

    private CouriersServerConfig() {
    }
}
