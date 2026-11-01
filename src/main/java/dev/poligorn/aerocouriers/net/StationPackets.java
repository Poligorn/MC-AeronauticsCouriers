package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationReputation;
import dev.poligorn.aerocouriers.village.StationNamePending;
import dev.poligorn.aerocouriers.village.VillageAdmin;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import dev.poligorn.aerocouriers.village.VillageRefreshTiming;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class StationPackets {
    private StationPackets() {
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        open(player, level, pos, false);
    }

    public static void openAdmin(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!VillageAdmin.canManageVillage(player)) {
            player.sendSystemMessage(Component.translatable("ui.aerocouriers.error.not_operator"));
            open(player, level, pos, false);
            return;
        }
        open(player, level, pos, true);
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos, boolean adminPanel) {
        PostStationBlockEntity station = PostStationBlock.stationEntity(level, pos);
        if (station == null || station.stationId() == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station", pos.toShortString()));
            return;
        }
        BlockPos controller = station.getBlockPos().immutable();
        UUID stationId = station.stationId();
        StationAtlas atlas = StationAtlas.get(player.server);
        StationAtlas.Station atlasStation = atlas.byId(stationId).orElse(null);
        int reputation = atlas.reputation(stationId);
        int bonus = StationReputation.bonusPercent(reputation);
        int tier = StationReputation.tier(reputation);
        int nextThreshold = StationReputation.nextThreshold(reputation);
        boolean canAdmin = VillageAdmin.canManageVillage(player);
        List<BoardSnapshotPayload.BoardEntry> orders = new ArrayList<>();
        List<String> courierNames = new ArrayList<>();
        ContractManager.get(player.server).all()
                .filter(c -> c.type() == ContractType.VILLAGE)
                .filter(c -> c.issuer().equals(stationId))
                .filter(StationPackets::onBoard)
                .sorted(Comparator.comparingLong(c -> c.timing().createdAt()))
                .limit(StationSnapshotPayload.MAX_ORDERS)
                .forEach(c -> {
                    orders.add(BoardPackets.boardEntry(player, c));
                    courierNames.add(VillageAdmin.courierName(player.server, c));
                });
        VillageAdmin.Stats stats = atlasStation == null
                ? new VillageAdmin.Stats(0, 0, orders.size(), 0, 0, CouriersServerConfig.VILLAGE_BOARD_MAX.get())
                : VillageAdmin.stats(player.server, atlasStation);
        VillageBoardPolicy policy = atlasStation == null ? VillageBoardPolicy.DEFAULT : atlasStation.policy();
        long overworldGameTime = player.server.overworld().getGameTime();
        long nextVillageRefresh = atlasStation == null
                ? VillageRefreshTiming.nextRefreshGameTime(player.server).orElse(0L)
                : VillageRefreshTiming.nextRefreshGameTime(player.server, atlasStation).orElse(0L);
        LicenseRank rank = Licenses.effectiveRank(player);
        StationSnapshotPayload.View view = new StationSnapshotPayload.View(
                adminPanel && canAdmin,
                canAdmin,
                OrderTablets.canAccessBoard(player),
                CouriersServerConfig.REQUIRE_TABLET.get(),
                rank.isAdmin() ? 5 : rank.level(),
                policy.allowExpensive(),
                policy.allowRare(),
                policy.allowedSizesMask(),
                policy.refreshIntervalMin() > 0
                        ? policy.refreshIntervalMin()
                        : CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN.get(),
                stats.completed(),
                stats.active(),
                stats.onBoard(),
                stats.expensive(),
                stats.rare(),
                stats.boardMax(),
                courierNames,
                otherNames(atlas, stationId));
        CouriersNetwork.sendRegistryPayload(player, new StationSnapshotPayload(
                controller,
                station.name(),
                reputation,
                bonus,
                tier,
                nextThreshold,
                canAdmin,
                orders,
                nextVillageRefresh,
                overworldGameTime,
                view), StationSnapshotPayload.STREAM_CODEC);
    }

    private static List<String> otherNames(StationAtlas atlas, UUID self) {
        List<String> names = new ArrayList<>();
        for (StationAtlas.Station station : atlas.all()) {
            if (!station.id().equals(self)) {
                names.add(station.name());
            }
        }
        return names;
    }

    /** Notes stay while the order is being delivered and leave only when it is finished. */
    public static boolean onBoard(Contract contract) {
        ContractState state = contract.state();
        return state == ContractState.OPEN || state.isActive();
    }

    /** The station never accepts an order itself. */
    public static boolean stationAcceptsOrders() {
        return false;
    }

    public static void handleOpen(ServerPlayer player, OpenStationPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        open(player, level, payload.pos());
    }

    public static void handleAdmin(ServerPlayer player, VillageAdminPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!VillageAdmin.canManageVillage(player)) {
            player.sendSystemMessage(Component.translatable("ui.aerocouriers.error.not_operator"));
            open(player, level, payload.stationPos(), false);
            return;
        }
        PostStationBlockEntity be = PostStationBlock.stationEntity(level, payload.stationPos());
        if (be == null || be.stationId() == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.stationPos().toShortString()));
            return;
        }
        StationAtlas.Station station = StationAtlas.get(player.server).byId(be.stationId()).orElse(null);
        if (station == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.stationPos().toShortString()));
            return;
        }
        Component message = switch (payload.action()) {
            case OPEN -> null;
            case RENAME -> VillageAdmin.rename(player.server, station, payload.text());
            case REP_ADD -> VillageAdmin.addReputation(player.server, station, payload.amount());
            case REP_SET -> VillageAdmin.setReputation(player.server, station, payload.amount());
            case REFRESH -> VillageAdmin.refreshNow(player.server, station);
            case REGENERATE -> VillageAdmin.regenerate(player.server, station);
            case CLEAR -> VillageAdmin.clearBoard(player.server, station);
            case INTERVAL -> VillageAdmin.setInterval(player.server, station, payload.amount());
            case TOGGLE_EXPENSIVE -> VillageAdmin.setExpensive(player.server, station, payload.flag());
            case TOGGLE_RARE -> VillageAdmin.setRare(player.server, station, payload.flag());
            case TOGGLE_SIZE -> toggleSize(player, station, payload);
        };
        if (message != null) {
            player.sendSystemMessage(message);
        }
        open(player, level, payload.stationPos(), true);
    }

    private static Component toggleSize(ServerPlayer player, StationAtlas.Station station, VillageAdminPayload payload) {
        CargoSize[] sizes = CargoSize.values();
        int ord = payload.amount();
        if (ord < 0 || ord >= sizes.length) {
            return Component.translatable("admin.aerocouriers.village.sizes.unknown");
        }
        VillageBoardPolicy next = station.policy().withSize(sizes[ord], payload.flag());
        return VillageAdmin.setSizes(player.server, station, next.allowedSizesMask());
    }

    public static void openName(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PostStationBlockEntity station = PostStationBlock.stationEntity(level, pos);
        BlockPos controller = station != null ? station.getBlockPos().immutable() : pos.immutable();
        UUID self = station != null ? station.stationId() : null;
        CouriersNetwork.sendRegistryPayload(player, new OpenStationNamePayload(controller, otherNames(
                StationAtlas.get(player.server), self)), OpenStationNamePayload.STREAM_CODEC);
    }

    public static void handleName(ServerPlayer player, NameStationPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!StationNamePending.canName(player, payload.pos())) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_placer"));
            return;
        }
        PostStationBlockEntity station = PostStationBlock.stationEntity(level, payload.pos());
        if (station == null || station.stationId() == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.pos().toShortString()));
            return;
        }
        StationAtlas.Station atlas = StationAtlas.get(player.server).byId(station.stationId()).orElse(null);
        if (atlas == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.pos().toShortString()));
            return;
        }
        String want = payload.name().strip();
        Component message = VillageAdmin.rename(player.server, atlas, want);
        player.sendSystemMessage(message);
        StationAtlas.Station after = StationAtlas.get(player.server).byId(atlas.id()).orElse(atlas);
        if (after.name().equals(want)) {
            StationNamePending.forget(level, payload.pos());
        }
    }

    public static void handleRename(ServerPlayer player, RenameStationPayload payload) {
        if (!VillageAdmin.canManageVillage(player)) {
            player.sendSystemMessage(Component.translatable("ui.aerocouriers.error.not_operator"));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.pos().toShortString()));
            return;
        }
        PostStationBlockEntity station = PostStationBlock.stationEntity(level, payload.pos());
        if (station == null || station.stationId() == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.pos().toShortString()));
            return;
        }
        StationAtlas.Station atlas = StationAtlas.get(player.server).byId(station.stationId()).orElse(null);
        if (atlas == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.pos().toShortString()));
            return;
        }
        player.sendSystemMessage(VillageAdmin.rename(player.server, atlas, payload.name()));
        open(player, level, payload.pos());
    }
}
