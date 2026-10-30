package dev.poligorn.aerocouriers.compat;

import dev.poligorn.aerocouriers.cargo.CargoBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlockEntity;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.tutorial.Tutorials;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationReputation;
import dev.poligorn.aerocouriers.village.VillageRefreshTiming;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side Jade tooltip payloads. No Jade types: GameTests and the optional plugin both call this.
 * The block entity only stores {@code contractId}; order fields come from {@link Contract} / {@link StationAtlas}.
 */
public final class JadeTooltipData {
    public static final String CARGO_BOUND = "ac_bound";
    public static final String CARGO_SHORT_ID = "ac_short_id";
    public static final String CARGO_CUSTOMER = "ac_customer";
    public static final String CARGO_CUSTOMER_KIND = "ac_customer_kind";
    public static final String CARGO_WEIGHT = "ac_weight";
    public static final String CARGO_STATUS = "ac_status";

    public static final String STATION_NAME = "ac_station_name";
    public static final String STATION_VILLAGE = "ac_village";
    public static final String STATION_VILLAGE_NAME = "ac_village_name";
    public static final String STATION_REPUTATION = "ac_reputation";
    public static final String STATION_BONUS = "ac_bonus";
    public static final String STATION_OPEN = "ac_open";
    public static final String STATION_REFRESH = "ac_refresh";

    public enum DisplayStatus {
        PUBLISHED("published"),
        IN_TRANSIT("in_transit"),
        DELIVERED("delivered"),
        OVERDUE("overdue");

        private final String key;

        DisplayStatus(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static DisplayStatus byKey(String key) {
            for (DisplayStatus status : values()) {
                if (status.key.equals(key)) {
                    return status;
                }
            }
            return PUBLISHED;
        }
    }

    public enum CustomerKind {
        PLAYER("player"),
        VILLAGE("village"),
        TUTORIAL("tutorial");

        private final String key;

        CustomerKind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static CustomerKind byKey(String key) {
            for (CustomerKind kind : values()) {
                if (kind.key.equals(key)) {
                    return kind;
                }
            }
            return PLAYER;
        }
    }

    public record CargoSnapshot(
            boolean bound,
            String shortId,
            String customer,
            CustomerKind customerKind,
            double weight,
            DisplayStatus status
    ) {
        public static CargoSnapshot empty() {
            return new CargoSnapshot(false, "", "", CustomerKind.PLAYER, 0, DisplayStatus.PUBLISHED);
        }

        public void write(CompoundTag tag) {
            tag.putBoolean(CARGO_BOUND, bound);
            if (!bound) {
                return;
            }
            tag.putString(CARGO_SHORT_ID, shortId);
            tag.putString(CARGO_CUSTOMER, customer);
            tag.putString(CARGO_CUSTOMER_KIND, customerKind.key());
            tag.putDouble(CARGO_WEIGHT, weight);
            tag.putString(CARGO_STATUS, status.key());
        }

        public static CargoSnapshot read(CompoundTag tag) {
            if (!tag.getBoolean(CARGO_BOUND)) {
                return empty();
            }
            return new CargoSnapshot(
                    true,
                    tag.getString(CARGO_SHORT_ID),
                    tag.getString(CARGO_CUSTOMER),
                    CustomerKind.byKey(tag.getString(CARGO_CUSTOMER_KIND)),
                    tag.getDouble(CARGO_WEIGHT),
                    DisplayStatus.byKey(tag.getString(CARGO_STATUS)));
        }
    }

    public record StationSnapshot(
            String name,
            boolean village,
            String villageName,
            int reputation,
            int bonusPercent,
            int openOrders,
            int refreshTicks
    ) {
        public void write(CompoundTag tag) {
            tag.putString(STATION_NAME, name);
            tag.putBoolean(STATION_VILLAGE, village);
            tag.putString(STATION_VILLAGE_NAME, villageName);
            tag.putInt(STATION_REPUTATION, reputation);
            tag.putInt(STATION_BONUS, bonusPercent);
            tag.putInt(STATION_OPEN, openOrders);
            tag.putInt(STATION_REFRESH, refreshTicks);
        }

        public static Optional<StationSnapshot> read(CompoundTag tag) {
            if (!tag.contains(STATION_NAME)) {
                return Optional.empty();
            }
            return Optional.of(new StationSnapshot(
                    tag.getString(STATION_NAME),
                    tag.getBoolean(STATION_VILLAGE),
                    tag.getString(STATION_VILLAGE_NAME),
                    tag.getInt(STATION_REPUTATION),
                    tag.getInt(STATION_BONUS),
                    tag.getInt(STATION_OPEN),
                    tag.getInt(STATION_REFRESH)));
        }
    }

    private JadeTooltipData() {
    }

    /** Controller BE of a multi-block crate, or {@code null} if this is not a cargo block. */
    public static @Nullable CargoContainerBlockEntity controllerEntity(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CargoBlock cargo) {
            BlockPos controller = cargo.controllerPos(pos, state);
            if (level.getBlockEntity(controller) instanceof CargoContainerBlockEntity be) {
                return be;
            }
        }
        return level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be ? be : null;
    }

    public static Optional<CargoSnapshot> cargoAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CargoBlock)) {
            return Optional.empty();
        }
        CargoContainerBlockEntity be = controllerEntity(level, pos, state);
        if (be == null || be.contractId() == null) {
            return Optional.of(CargoSnapshot.empty());
        }
        MinecraftServer server = level.getServer();
        Contract contract = ContractManager.get(server).byId(be.contractId()).orElse(null);
        if (contract == null) {
            return Optional.of(CargoSnapshot.empty());
        }
        return Optional.of(fromContract(contract, server, ContractManager.now(server)));
    }

    public static CargoSnapshot fromContract(Contract contract, MinecraftServer server, long now) {
        return new CargoSnapshot(
                true,
                contract.shortId(),
                customerName(contract, server),
                customerKind(contract),
                contract.weight(),
                displayStatus(contract, now));
    }

    public static DisplayStatus displayStatus(Contract contract, long now) {
        ContractState state = contract.state();
        if (state == ContractState.DELIVERED) {
            return DisplayStatus.DELIVERED;
        }
        if (state == ContractState.EXPIRED || state == ContractState.FAILED) {
            return DisplayStatus.OVERDUE;
        }
        if (state.isActive() && contract.timing().acceptedAt() >= 0 && now >= contract.timing().dueAt()) {
            return DisplayStatus.OVERDUE;
        }
        if (state == ContractState.OPEN) {
            return now >= contract.timing().expiresAt() ? DisplayStatus.OVERDUE : DisplayStatus.PUBLISHED;
        }
        return DisplayStatus.IN_TRANSIT;
    }

    public static CustomerKind customerKind(Contract contract) {
        if (contract.type().isTutorial()) {
            return CustomerKind.TUTORIAL;
        }
        return contract.type().isNpc() ? CustomerKind.VILLAGE : CustomerKind.PLAYER;
    }

    public static String customerName(Contract contract, MinecraftServer server) {
        if (contract.type().isTutorial() || Tutorials.SERVICE.equals(contract.issuer())) {
            return "";
        }
        if (contract.type() == ContractType.VILLAGE) {
            return StationAtlas.get(server).byId(contract.issuer()).map(StationAtlas.Station::name)
                    .orElse(contract.issuer().toString().substring(0, 8));
        }
        ServerPlayer online = ContractManager.findPlayer(server, contract.issuer());
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return BoardPackets.playerName(server, contract.issuer())
                .orElse(contract.issuer().toString().substring(0, 8));
    }

    public static Optional<StationSnapshot> stationAt(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PostStationBlockEntity station)) {
            return Optional.empty();
        }
        MinecraftServer server = level.getServer();
        StationAtlas atlas = StationAtlas.get(server);
        UUID stationId = station.stationId();
        String name = !station.name().isEmpty()
                ? station.name()
                : stationId == null ? "" : atlas.byId(stationId).map(StationAtlas.Station::name).orElse("");
        int reputation = stationId == null ? 0 : atlas.reputation(stationId);
        int open = stationId == null ? 0 : (int) ContractManager.get(server).open()
                .filter(c -> c.type() == ContractType.VILLAGE)
                .filter(c -> c.issuer().equals(stationId) || c.recipient().equals(stationId))
                .count();
        var nextRefresh = VillageRefreshTiming.nextRefreshGameTime(server);
        int refresh = nextRefresh.isEmpty()
                ? -1
                : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, nextRefresh.getAsLong() - server.overworld().getGameTime()));
        boolean village = station.village();
        return Optional.of(new StationSnapshot(
                name,
                village,
                village ? name : "",
                reputation,
                StationReputation.bonusPercent(reputation),
                open,
                refresh));
    }

    public static String formatWeight(double weight) {
        long rounded = Math.round(weight);
        if (Math.abs(weight - rounded) < 0.05) {
            return Long.toString(rounded);
        }
        return String.format(Locale.ROOT, "%.1f", weight);
    }

    public static String formatDuration(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + two(minutes) + ":" + two(seconds);
        }
        return minutes + ":" + two(seconds);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
