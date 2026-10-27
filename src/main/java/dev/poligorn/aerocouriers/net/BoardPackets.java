package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.CargoContainers;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.contract.NotificationLog;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.Weights;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseOwner;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.item.Waybills;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationReputation;
import com.mojang.authlib.GameProfile;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side board intents. The screen only sends these; every check stays in {@link ContractManager} and the
 * existing command helpers (GDD §14).
 */
public final class BoardPackets {
    private static final int OP = Commands.LEVEL_GAMEMASTERS;

    private BoardPackets() {
    }

    public static void open(ServerPlayer player) {
        open(player, 0, true, Optional.empty());
    }

    /** Pushes a fresh snapshot without opening the screen (profile XP after delivery, etc.). */
    public static void syncSnapshot(ServerPlayer player) {
        if (!canSendBoard(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(player, 0, false, Optional.empty()));
    }

    public static void open(ServerPlayer player, int page, boolean openScreen, Optional<Component> notice) {
        if (!canSendBoard(player)) {
            notice.ifPresent(player::sendSystemMessage);
            player.sendSystemMessage(Component.translatable("ui.aerocouriers.error.no_client"));
            return;
        }
        if (openScreen && !OrderTablets.canAccessBoard(player)) {
            denyTablet(player);
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(player, page, openScreen, notice));
        if (openScreen) {
            CouriersNotifications.sync(player);
        }
    }

    public static BoardSnapshotPayload snapshot(ServerPlayer player, int page, boolean openScreen,
                                               Optional<Component> notice) {
        MinecraftServer server = player.server;
        ContractManager manager = ContractManager.get(server);
        StationAtlas atlas = StationAtlas.get(server);
        Escrow escrow = Escrow.get(server);
        long now = ContractManager.now(server);
        UUID self = player.getUUID();
        boolean allStations = player.hasPermissions(OP);

        List<BoardSnapshotPayload.BoardEntry> open = manager.open()
                .filter(c -> visibleOnBoard(c, self, atlas, allStations))
                .sorted(Comparator.comparingLong(c -> c.timing().createdAt()))
                .map(c -> entry(c, self, atlas, escrow, now, server))
                .toList();
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * BoardSnapshotPayload.PAGE_SIZE, open.size());
        int to = Math.min(from + BoardSnapshotPayload.PAGE_SIZE, open.size());

        List<BoardSnapshotPayload.BoardEntry> mine = manager.all()
                .filter(c -> c.issuer().equals(self) || c.isCourier(self))
                .sorted(Comparator.comparingLong(c -> c.timing().createdAt()))
                .map(c -> entry(c, self, atlas, escrow, now, server))
                .limit(64)
                .toList();
        List<BoardSnapshotPayload.StationHint> stations = atlas.all().stream()
                .filter(s -> allStations || s.discoveredBy(self))
                .sorted(Comparator.comparing(StationAtlas.Station::name))
                .map(s -> new BoardSnapshotPayload.StationHint(s.name(), s.pos()))
                .limit(64)
                .toList();

        boolean requireTablet = CouriersServerConfig.SPEC.isLoaded() && CouriersServerConfig.REQUIRE_TABLET.get();
        return new BoardSnapshotPayload(
                openScreen,
                safePage,
                open.size(),
                Economies.get(server).balance(self),
                Mailbox.get(server).contents(self).size(),
                NotificationLog.get(server).unread(self),
                player.hasPermissions(OP),
                Licenses.isLicensedCourier(player),
                CouriersServerConfig.ALLOW_SELF_ACCEPT.get(),
                requireTablet,
                notice,
                List.copyOf(open.subList(from, to)),
                mine,
                stations,
                boardProfile(player));
    }

    private static BoardSnapshotPayload.BoardProfile boardProfile(ServerPlayer player) {
        CourierProfile stats = Licenses.profile(player);
        LicenseRank rank = RankRules.rankFor(stats);
        for (ItemStack stack : Licenses.findLicense(player).stream().toList()) {
            LicenseOwner owner = stack.get(ModComponents.LICENSE_OWNER.get());
            if (owner != null && owner.isOwnedBy(player.getUUID())) {
                LicenseRank onLicense = stack.get(ModComponents.LICENSE_RANK.get());
                if (onLicense != null && onLicense.isAdmin()) {
                    rank = onLicense;
                }
                break;
            }
        }
        List<Integer> rankDefaults = List.of(0, 300, 1500, 5000, 15000);
        List<? extends Integer> configured = CouriersServerConfig.RANK_XP.get();
        int[] thresholds = new int[5];
        for (int i = 0; i < 5; i++) {
            thresholds[i] = i < configured.size() ? configured.get(i) : rankDefaults.get(i);
        }
        int acePercent = (int) Math.round(CouriersServerConfig.ACE_MIN_SUCCESS.get() * 100.0);
        return new BoardSnapshotPayload.BoardProfile(
                player.getGameProfile().getName(),
                rank,
                stats.delivered(),
                stats.failed(),
                stats.distance(),
                stats.rewardSum(),
                stats.villageDelivered(),
                CouriersServerConfig.TRAINEE_FREE_CONTRACTS.get(),
                CouriersServerConfig.TRAINEE_MAX_DISTANCE.get(),
                stats.xp(),
                thresholds[0],
                thresholds[1],
                thresholds[2],
                thresholds[3],
                thresholds[4],
                acePercent);
    }

    public static void handleOpen(ServerPlayer player, OpenBoardPayload payload) {
        open(player, payload.page(), true, Optional.empty());
    }

    public static void handleClaim(ServerPlayer player) {
        if (!OrderTablets.canAccessBoard(player)) {
            denyTablet(player);
            return;
        }
        Mailbox mailbox = Mailbox.get(player.server);
        int moved = mailbox.claim(player.getUUID(), player.getInventory());
        int left = mailbox.contents(player.getUUID()).size();
        reply(player, Component.translatable("contract.aerocouriers.mail.claimed", moved, left));
    }

    public static void handleIntent(ServerPlayer player, ContractIntentPayload payload) {
        if (!OrderTablets.canAccessBoard(player)) {
            denyTablet(player);
            return;
        }
        ContractManager manager = ContractManager.get(player.server);
        ContractManager.Outcome outcome = switch (payload.kind()) {
            case ACCEPT -> manager.accept(player, payload.id());
            case ABANDON -> manager.abandon(player, payload.id());
            case CANCEL -> manager.cancel(player, payload.id());
        };
        if (!outcome.success()) {
            player.displayClientMessage(outcome.message(), true);
        }
        reply(player, outcome.message());
    }

    public static void openWaybillCreate(ServerPlayer player, BlockPos origin) {
        if (!canSendBoard(player)) {
            player.sendSystemMessage(Component.translatable("ui.aerocouriers.error.no_client"));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        var access = CargoContainers.find(level, origin);
        if (access.isEmpty()) {
            Component message = Component.translatable("contract.aerocouriers.error.no_container",
                    origin.toShortString());
            player.sendSystemMessage(message);
            player.displayClientMessage(message, true);
            return;
        }
        List<ItemStack> cargo = access.get().items();
        double weight = Weights.of(cargo);
        StationAtlas atlas = StationAtlas.get(player.server);
        boolean allStations = player.hasPermissions(OP);
        List<BoardSnapshotPayload.StationHint> stations = atlas.all().stream()
                .filter(s -> allStations || s.discoveredBy(player.getUUID()))
                .sorted(Comparator.comparing(StationAtlas.Station::name))
                .map(s -> new BoardSnapshotPayload.StationHint(s.name(), s.pos()))
                .limit(64)
                .toList();
        PacketDistributor.sendToPlayer(player, new OpenWaybillCreatePayload(
                origin, cargo, weight, Economies.get(player.server).balance(player.getUUID()), stations));
    }

    public static void handleCreate(ServerPlayer player, CreateContractPayload payload) {
        Destination destination = new Destination(player.level().dimension(), payload.to(), payload.radius());
        ContractManager.Outcome outcome = ContractManager.get(player.server)
                .createFromWaybill(player, payload.from(), destination,
                        payload.reward(), payload.collateral(), payload.minutes());
        if (outcome.success()) {
            CouriersNotifications.notifyOrder(player.server, player.getUUID(), outcome.message(),
                    outcome.contract().map(Contract::id));
            open(player, 0, true, Optional.empty());
        } else {
            Component message = outcome.message();
            player.sendSystemMessage(message);
            player.displayClientMessage(message, true);
        }
    }

    public static ContractManager.Outcome handleOperator(ServerPlayer player, OperatorBoardPayload payload) {
        if (!player.hasPermissions(OP)) {
            Component message = Component.translatable("ui.aerocouriers.error.not_operator");
            reply(player, message);
            return new ContractManager.Outcome(false, message, Optional.empty());
        }
        ContractManager.Outcome outcome = switch (payload.action()) {
            case GIVE_LICENSE -> giveLicense(player);
            case ADD_MONEY -> addMoney(player);
            case GIVE_WAYBILL -> giveWaybill(player);
            case FORCE_SPAWN -> operatorContract(player, payload.contractId(), ContractManager::spawnContainer);
            case FORCE_DELIVER -> operatorContract(player, payload.contractId(), ContractManager::forceDeliver);
        };
        reply(player, outcome.message());
        return outcome;
    }

    /** FakePlayers used by GameTests have a Connection whose Netty channel is null. */
    private static boolean canSendBoard(ServerPlayer player) {
        try {
            return player.connection != null && player.connection.hasChannel(BoardSnapshotPayload.TYPE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void denyTablet(ServerPlayer player) {
        Component message = Component.translatable("ui.aerocouriers.error.need_tablet");
        player.displayClientMessage(message, true);
        player.sendSystemMessage(message);
    }

    private static void reply(ServerPlayer player, Component message) {
        CouriersNotifications.notifyOrder(player.server, player.getUUID(), message, Optional.empty());
        open(player, 0, false, Optional.empty());
    }

    private static ContractManager.Outcome giveLicense(ServerPlayer player) {
        ItemStack license = Licenses.issueLicense(player, LicenseRank.RANK1, false);
        if (!player.getInventory().add(license)) {
            player.drop(license, false);
        }
        return new ContractManager.Outcome(true, Component.translatable("commands.aerocouriers.license.given",
                player.getDisplayName(), LicenseRank.RANK1.displayName()), Optional.empty());
    }

    private static ContractManager.Outcome giveWaybill(ServerPlayer player) {
        ItemStack waybill = Waybills.stack(4);
        if (!player.getInventory().add(waybill)) {
            player.drop(waybill, false);
        }
        return new ContractManager.Outcome(true, Component.translatable("commands.aerocouriers.waybill.given",
                player.getDisplayName()), Optional.empty());
    }

    private static ContractManager.Outcome addMoney(ServerPlayer player) {
        Economies.get(player.server).deposit(player.getUUID(), OperatorBoardPayload.TEST_MONEY, "admin");
        long balance = Economies.get(player.server).balance(player.getUUID());
        return new ContractManager.Outcome(true, Component.translatable("contract.aerocouriers.balance",
                player.getDisplayName(), balance), Optional.empty());
    }

    @FunctionalInterface
    private interface OperatorContractAction {
        ContractManager.Outcome apply(ContractManager manager, MinecraftServer server, UUID id);
    }

    private static ContractManager.Outcome operatorContract(ServerPlayer player, Optional<UUID> id,
                                                           OperatorContractAction action) {
        if (id.isEmpty()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("ui.aerocouriers.error.no_selection"), Optional.empty());
        }
        return action.apply(ContractManager.get(player.server), player.server, id.get());
    }

    /** NPC orders only appear between stations this player has discovered (GDD §6.1). */
    private static boolean visibleOnBoard(Contract contract, UUID viewer, StationAtlas atlas, boolean allStations) {
        if (!contract.type().isNpc() || allStations) {
            return true;
        }
        return atlas.byPos(contract.origin().dimension(), contract.origin().pos())
                .map(s -> s.discoveredBy(viewer))
                .orElse(false)
                && atlas.byPos(contract.destination().dimension(), contract.destination().pos())
                .map(s -> s.discoveredBy(viewer))
                .orElse(false);
    }

    public static BoardSnapshotPayload.BoardEntry boardEntry(ServerPlayer viewer, Contract contract) {
        MinecraftServer server = viewer.server;
        return entry(contract, viewer.getUUID(), StationAtlas.get(server), Escrow.get(server),
                ContractManager.now(server), server);
    }

    private static BoardSnapshotPayload.BoardEntry entry(Contract contract, UUID viewer, StationAtlas atlas,
                                                        Escrow escrow, long now, MinecraftServer server) {
        String role = contract.roleFor(viewer);
        long ticksLeft = contract.state().isActive()
                ? contract.timing().dueAt() - now
                : contract.state() == ContractState.OPEN ? contract.timing().expiresAt() - now : 0;
        List<ItemStack> cargoItems = escrow.holding(contract.id()).items().stream()
                .filter(stack -> !stack.isEmpty())
                .limit(BoardSnapshotPayload.CARGO_STACK_LIMIT)
                .map(ItemStack::copy)
                .toList();
        List<BoardSnapshotPayload.CargoLine> manifest = cargoItems.stream()
                .map(BoardSnapshotPayload.CargoLine::of)
                .toList();
        boolean villageCustomer = contract.type().isNpc();
        Optional<UUID> customerId = villageCustomer ? Optional.empty() : Optional.of(contract.issuer());
        String customerName = villageCustomer
                ? label(atlas, contract.origin())
                : server.getProfileCache()
                        .get(contract.issuer())
                        .map(GameProfile::getName)
                        .orElse(contract.issuer().toString().substring(0, 8));
        int customerStationReputation = villageCustomer ? atlas.reputation(contract.issuer()) : 0;
        int customerTier = villageCustomer ? StationReputation.tier(customerStationReputation) : 0;
        int customerNext = villageCustomer ? StationReputation.nextThreshold(customerStationReputation) : -1;
        int customerBonus = villageCustomer ? StationReputation.bonusPercent(customerStationReputation) : 0;
        return new BoardSnapshotPayload.BoardEntry(
                contract.id(),
                contract.shortId(),
                contract.state(),
                role,
                label(atlas, contract.origin()),
                label(atlas, contract.destination()),
                (int) contract.distance(),
                contract.size().name(),
                String.format(Locale.ROOT, "%.1f / %s", contract.weight(), contract.size().maxWeight()),
                contract.reward(),
                contract.collateral(),
                contract.requiredRank(),
                contract.timing().deadline() / Pricing.TICKS_PER_MINUTE,
                Math.max(0, ticksLeft / Pricing.TICKS_PER_MINUTE),
                manifest,
                customerId,
                customerName,
                villageCustomer,
                contract.origin().dimension(),
                contract.origin().pos(),
                contract.destination().dimension(),
                contract.destination().pos(),
                cargoItems,
                customerStationReputation,
                escrow.holding(contract.id()).villageRare(),
                customerTier,
                customerNext,
                customerBonus);
    }

    private static String label(StationAtlas atlas, Destination dest) {
        return atlas.all().stream()
                .filter(s -> s.dimension().equals(dest.dimension()) && s.pos().equals(dest.pos()))
                .map(StationAtlas.Station::name)
                .findFirst()
                .orElse(dest.pos().toShortString());
    }
}
