package dev.poligorn.aerocouriers.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Runtime read/write of common {@link CouriersServerConfig} keys (admin panel / commands). */
public final class CouriersConfigEditor {
    public record KeyInfo(String path, String current) {
    }

    private CouriersConfigEditor() {
    }

    public static List<KeyInfo> listCommon() {
        List<KeyInfo> out = new ArrayList<>();
        add(out, "contracts.village_generation_interval_min", CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN);
        add(out, "contracts.village_board_max", CouriersServerConfig.VILLAGE_BOARD_MAX);
        add(out, "contracts.max_open_per_player", CouriersServerConfig.MAX_OPEN_PER_PLAYER);
        add(out, "cargo.spawn_trigger_radius", CouriersServerConfig.SPAWN_TRIGGER_RADIUS);
        add(out, "cargo.delivery_max_speed", CouriersServerConfig.DELIVERY_MAX_SPEED);
        add(out, "progression.trainee_free_contracts", CouriersServerConfig.TRAINEE_FREE_CONTRACTS);
        add(out, "progression.trainee_max_distance", CouriersServerConfig.TRAINEE_MAX_DISTANCE);
        add(out, "stations.register_radius", CouriersServerConfig.STATION_REGISTER_RADIUS);
        add(out, "piracy.enabled", CouriersServerConfig.PIRACY_ENABLED);
        add(out, "debug.allow_self_accept", CouriersServerConfig.ALLOW_SELF_ACCEPT);
        add(out, "access.require_tablet", CouriersServerConfig.REQUIRE_TABLET);
        return out;
    }

    private static void add(List<KeyInfo> out, String path, ModConfigSpec.ConfigValue<?> value) {
        out.add(new KeyInfo(path, String.valueOf(value.get())));
    }

    public static Optional<String> get(String path) {
        return find(path).map(v -> String.valueOf(v.get()));
    }

    public static Optional<Component> set(String path, String raw) {
        if (find(path).isEmpty()) {
            return Optional.of(Component.translatable("admin.aerocouriers.config.unknown", path));
        }
        try {
            switch (path.toLowerCase(Locale.ROOT)) {
                case "debug.allow_self_accept" -> CouriersServerConfig.ALLOW_SELF_ACCEPT.set(Boolean.parseBoolean(raw));
                case "piracy.enabled" -> CouriersServerConfig.PIRACY_ENABLED.set(Boolean.parseBoolean(raw));
                case "access.require_tablet" -> CouriersServerConfig.REQUIRE_TABLET.set(Boolean.parseBoolean(raw));
                case "contracts.village_generation_interval_min" ->
                        CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN.set(Integer.parseInt(raw));
                case "contracts.village_board_max" -> CouriersServerConfig.VILLAGE_BOARD_MAX.set(Integer.parseInt(raw));
                case "contracts.max_open_per_player" ->
                        CouriersServerConfig.MAX_OPEN_PER_PLAYER.set(Integer.parseInt(raw));
                case "cargo.spawn_trigger_radius" ->
                        CouriersServerConfig.SPAWN_TRIGGER_RADIUS.set(Integer.parseInt(raw));
                case "cargo.delivery_max_speed" ->
                        CouriersServerConfig.DELIVERY_MAX_SPEED.set(Double.parseDouble(raw));
                case "progression.trainee_free_contracts" ->
                        CouriersServerConfig.TRAINEE_FREE_CONTRACTS.set(Integer.parseInt(raw));
                case "progression.trainee_max_distance" ->
                        CouriersServerConfig.TRAINEE_MAX_DISTANCE.set(Integer.parseInt(raw));
                case "stations.register_radius" ->
                        CouriersServerConfig.STATION_REGISTER_RADIUS.set(Integer.parseInt(raw));
                default -> {
                    return Optional.of(Component.translatable("admin.aerocouriers.config.unsupported", path));
                }
            }
        } catch (NumberFormatException e) {
            return Optional.of(Component.translatable("admin.aerocouriers.config.bad_value", raw));
        }
        saveServerConfig();
        return Optional.empty();
    }

    public static void setCollateralProfile(dev.poligorn.aerocouriers.contract.CollateralProfile profile) {
        CouriersServerConfig.COLLATERAL_PROFILE.set(profile.key());
        saveServerConfig();
    }

    public static void setTutorialEnabled(boolean on) {
        CouriersServerConfig.TUTORIAL_ENABLED.set(on);
        saveServerConfig();
    }

    public static void toggleDebugAllowSelfAccept() {
        boolean next = !CouriersServerConfig.ALLOW_SELF_ACCEPT.get();
        CouriersServerConfig.ALLOW_SELF_ACCEPT.set(next);
        saveServerConfig();
    }

    private static Optional<ModConfigSpec.ConfigValue<?>> find(String path) {
        String key = path.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "contracts.village_generation_interval_min" -> Optional.of(CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN);
            case "contracts.village_board_max" -> Optional.of(CouriersServerConfig.VILLAGE_BOARD_MAX);
            case "contracts.max_open_per_player" -> Optional.of(CouriersServerConfig.MAX_OPEN_PER_PLAYER);
            case "cargo.spawn_trigger_radius" -> Optional.of(CouriersServerConfig.SPAWN_TRIGGER_RADIUS);
            case "cargo.delivery_max_speed" -> Optional.of(CouriersServerConfig.DELIVERY_MAX_SPEED);
            case "progression.trainee_free_contracts" -> Optional.of(CouriersServerConfig.TRAINEE_FREE_CONTRACTS);
            case "progression.trainee_max_distance" -> Optional.of(CouriersServerConfig.TRAINEE_MAX_DISTANCE);
            case "stations.register_radius" -> Optional.of(CouriersServerConfig.STATION_REGISTER_RADIUS);
            case "piracy.enabled" -> Optional.of(CouriersServerConfig.PIRACY_ENABLED);
            case "debug.allow_self_accept" -> Optional.of(CouriersServerConfig.ALLOW_SELF_ACCEPT);
            case "access.require_tablet" -> Optional.of(CouriersServerConfig.REQUIRE_TABLET);
            default -> Optional.empty();
        };
    }

    private static void saveServerConfig() {
        if (CouriersServerConfig.SPEC.isLoaded()) {
            CouriersServerConfig.SPEC.save();
        }
    }
}
