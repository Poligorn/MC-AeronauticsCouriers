package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.CargoContainers;
import dev.poligorn.aerocouriers.admin.AdminActions;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import dev.poligorn.aerocouriers.contract.ManifestLimits;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.contract.CourierInbox;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.Weights;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseOwner;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.config.CouriersConfigEditor;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.registry.ModComponents;
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
        CouriersNetwork.sendRegistryPayload(player, snapshot(player, 0, false, Optional.empty()),
                BoardSnapshotPayload.STREAM_CODEC);
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
        CouriersNetwork.sendRegistryPayload(player, snapshot(player, page, openScreen, notice),
                BoardSnapshotPayload.STREAM_CODEC);
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
                .map(s -> new BoardSnapshotPayload.StationHint(s.name(), s.pos(), s.dimension()))
                .limit(64)
                .toList();

        boolean requireTablet = CouriersServerConfig.SPEC.isLoaded() && CouriersServerConfig.REQUIRE_TABLET.get();
        boolean operator = player.hasPermissions(OP);
        List<BoardSnapshotPayload.AdminActiveEntry> adminActive = operator
                ? manager.all()
                        .filter(c -> c.state() == ContractState.OPEN || c.state().isActive())
                        .sorted(Comparator.comparingLong(c -> c.timing().createdAt()))
                        .map(c -> adminEntry(c, atlas, server))
                        .limit(64)
                        .toList()
                : List.of();
        List<BoardSnapshotPayload.PlayerHint> online = operator
                ? server.getPlayerList().getPlayers().stream()
                        .map(p -> new BoardSnapshotPayload.PlayerHint(p.getUUID(), p.getGameProfile().getName()))
                        .limit(PayloadLimits.ONLINE_PLAYERS)
                        .toList()
                : List.of();
        List<String> configLines = operator
                ? CouriersConfigEditor.listCommon().stream()
                        .map(k -> k.path() + "=" + k.current())
                        .limit(PayloadLimits.ADMIN_CONFIG_LINES)
                        .toList()
                : List.of();
        return new BoardSnapshotPayload(
                openScreen,
                safePage,
                open.size(),
                Economies.get(server).balance(self),
                CourierInbox.get(server).unread(self),
                CourierInbox.get(server).unread(self),
                operator,
                Licenses.isLicensedCourier(player),
                CouriersServerConfig.ALLOW_SELF_ACCEPT.get(),
                requireTablet,
                notice,
                List.copyOf(open.subList(from, to)),
                mine,
                stations,
                boardProfile(player),
                adminActive,
                online,
                configLines);
    }

    private static BoardSnapshotPayload.AdminActiveEntry adminEntry(Contract contract, StationAtlas atlas,
                                                                   MinecraftServer server) {
        String courier = contract.courier()
                .flatMap(id -> server.getProfileCache().get(id).map(GameProfile::getName))
                .orElse("—");
        long now = ContractManager.now(server);
        long ticksLeft = contract.state().isActive()
                ? contract.timing().dueAt() - now
                : contract.state() == ContractState.OPEN ? contract.timing().expiresAt() - now : 0;
        return new BoardSnapshotPayload.AdminActiveEntry(
                contract.id(),
                contract.shortId(),
                contract.state(),
                label(atlas, contract.origin()),
                label(atlas, contract.destination()),
                contract.size().name(),
                courier,
                contract.type(),
                contract.reward(),
                (int) contract.distance(),
                Math.max(0, ticksLeft / Pricing.TICKS_PER_MINUTE),
                contract.collateral(),
                contract.timing().createdAt());
    }

    private static BoardSnapshotPayload.BoardProfile boardProfile(ServerPlayer player) {
        return ProfileSnapshots.forPlayer(player);
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
            sendUiSound(player, CouriersUiSoundPayload.Kind.ORDER_DENY);
        } else if (payload.kind() == ContractIntentPayload.Kind.ACCEPT) {
            sendUiSound(player, CouriersUiSoundPayload.Kind.ORDER_ACCEPT);
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
            sendUiSound(player, CouriersUiSoundPayload.Kind.ORDER_DENY);
            return;
        }
        List<ItemStack> allCargo = access.get().items();
        Optional<Component> cargoError = ManifestLimits.validateMessage(allCargo);
        if (cargoError.isPresent()) {
            Component message = cargoError.get();
            player.sendSystemMessage(message);
            player.displayClientMessage(message, true);
            sendUiSound(player, CouriersUiSoundPayload.Kind.ORDER_DENY);
            return;
        }
        List<ItemStack> cargo = PayloadLimits.cap(allCargo, BoardSnapshotPayload.CARGO_STACK_LIMIT);
        double weight = Weights.of(allCargo);
        StationAtlas atlas = StationAtlas.get(player.server);
        boolean allStations = player.hasPermissions(OP);
        List<BoardSnapshotPayload.StationHint> stations = atlas.all().stream()
                .filter(s -> allStations || s.discoveredBy(player.getUUID()))
                .sorted(Comparator.comparing(StationAtlas.Station::name))
                .map(s -> new BoardSnapshotPayload.StationHint(s.name(), s.pos(), s.dimension()))
                .limit(64)
                .toList();
        CouriersNetwork.sendRegistryPayload(player, new OpenWaybillCreatePayload(
                origin, cargo, weight, Economies.get(player.server).balance(player.getUUID()), stations),
                OpenWaybillCreatePayload.STREAM_CODEC);
    }

    public static void handleCreate(ServerPlayer player, CreateContractPayload payload) {
        Destination destination = new Destination(payload.destinationDimension(), payload.to(), payload.radius());
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
            sendUiSound(player, CouriersUiSoundPayload.Kind.ORDER_DENY);
        }
    }

    public static ContractManager.Outcome handleOperator(ServerPlayer player, OperatorBoardPayload payload) {
        if (!player.hasPermissions(OP)) {
            Component message = Component.translatable("ui.aerocouriers.error.not_operator");
            reply(player, message);
            return new ContractManager.Outcome(false, message, Optional.empty());
        }
        MinecraftServer server = player.server;
        ServerPlayer target = payload.targetPlayer()
                .flatMap(id -> Optional.ofNullable(server.getPlayerList().getPlayer(id)))
                .orElse(player);
        ContractManager.Outcome outcome = switch (payload.action()) {
            case GIVE_LICENSE -> AdminActions.grantLicense(player, LicenseRank.RANK1);
            case ADD_MONEY -> AdminActions.grantMoney(player, OperatorBoardPayload.TEST_MONEY);
            case GIVE_WAYBILL -> AdminActions.grantWaybill(player);
            case FORCE_SPAWN -> adminContract(player, payload, false,
                    (s, id) -> AdminActions.forceSpawn(s, id, player));
            case FORCE_DELIVER -> adminContract(player, payload, false,
                    (s, id) -> AdminActions.forceDeliver(s, id, player));
            case FORCE_FAIL -> adminContract(player, payload, true,
                    (s, id) -> AdminActions.forceFail(s, id, player));
            case ADMIN_CANCEL -> adminContract(player, payload, true,
                    (s, id) -> AdminActions.adminCancel(s, id, player));
            case EXTEND_DEADLINE -> adminContract(player, payload, false,
                    (s, id) -> AdminActions.extend(s, id, player));
            case TELEPORT_TO_CONTRACT -> adminContract(player, payload, false,
                    (s, id) -> AdminActions.teleportToContract(s, id, player));
            case PLAYER_RANK -> playerRank(target, payload);
            case PLAYER_XP_ADD -> AdminActions.setXp(target, (int) payload.longArg(), true);
            case PLAYER_XP_SET -> AdminActions.setXp(target, (int) payload.longArg(), false);
            case PLAYER_REP -> AdminActions.setReputation(server, payload.arg1().orElse(""), (int) payload.longArg());
            case PLAYER_LICENSE -> playerLicense(target, payload);
            case PLAYER_WAYBILL -> AdminActions.grantWaybill(target);
            case PLAYER_MONEY -> AdminActions.grantMoney(target, payload.longArg() > 0 ? payload.longArg() : OperatorBoardPayload.TEST_MONEY);
            case PLAYER_RESET_LICENSE -> playerResetLicense(player, payload, target);
            case ECONOMY_RELOAD -> AdminActions.reloadDatapacks(server);
            case ECONOMY_SHOW_VALUE -> AdminActions.handValue(player, false, 0);
            case ECONOMY_SET_VALUE -> AdminActions.handValue(player, true, payload.longArg());
            case ECONOMY_SHOW_WEIGHT -> AdminActions.handWeight(player, false, 0);
            case ECONOMY_SET_WEIGHT -> AdminActions.handWeight(player, true, Double.longBitsToDouble(payload.longArg()));
            case SERVER_REGEN -> AdminActions.regenerateVillageBoards(server);
            case CONFIG_SET -> AdminActions.setConfig(payload.arg1().orElse(""), payload.arg2().orElse(""));
            case DEBUG_TOGGLE_SELF_ACCEPT -> AdminActions.toggleAllowSelfAccept();
        };
        reply(player, outcome.message());
        return outcome;
    }

    private static ContractManager.Outcome playerRank(ServerPlayer target, OperatorBoardPayload payload) {
        String raw = payload.arg1().orElse(LicenseRank.RANK1.getSerializedName());
        return AdminActions.grantLicense(target, LicenseRank.bySerializedName(raw));
    }

    private static ContractManager.Outcome playerLicense(ServerPlayer target, OperatorBoardPayload payload) {
        String raw = payload.arg1().orElse(LicenseRank.RANK1.getSerializedName());
        return AdminActions.grantLicense(target, LicenseRank.bySerializedName(raw));
    }

    private static ContractManager.Outcome playerResetLicense(ServerPlayer actor, OperatorBoardPayload payload,
                                                              ServerPlayer target) {
        if (payload.targetPlayer().isEmpty()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("ui.aerocouriers.error.no_selection"), Optional.empty());
        }
        if (!payload.confirmed()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("admin.aerocouriers.error.confirm"), Optional.empty());
        }
        return AdminActions.resetLicense(target);
    }

    @FunctionalInterface
    private interface AdminContractFn {
        ContractManager.Outcome run(MinecraftServer server, UUID id);
    }

    private static ContractManager.Outcome adminContract(ServerPlayer player, OperatorBoardPayload payload,
                                                        boolean needsConfirm, AdminContractFn action) {
        if (needsConfirm && !payload.confirmed()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("admin.aerocouriers.error.confirm"), Optional.empty());
        }
        if (payload.contractId().isEmpty()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("ui.aerocouriers.error.no_selection"), Optional.empty());
        }
        return action.run(player.server, payload.contractId().get());
    }

    /** FakePlayers used by GameTests have a Connection whose Netty channel is null. */
    private static boolean canSendBoard(ServerPlayer player) {
        try {
            return player.connection != null && player.connection.hasChannel(BoardSnapshotPayload.TYPE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void denyTablet(ServerPlayer player) {
        Component message = Component.translatable("ui.aerocouriers.error.need_tablet");
        player.displayClientMessage(message, true);
        player.sendSystemMessage(message);
        sendUiSound(player, CouriersUiSoundPayload.Kind.ORDER_DENY);
    }

    private static void sendUiSound(ServerPlayer player, CouriersUiSoundPayload.Kind kind) {
        if (!canSendBoard(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new CouriersUiSoundPayload(kind));
    }

    private static void reply(ServerPlayer player, Component message) {
        CouriersNotifications.notifyOrder(player.server, player.getUUID(), message, Optional.empty());
        open(player, 0, false, Optional.empty());
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
        boolean originRevealed = contract.revealsPickupTo(viewer);
        boolean crossDimension = !contract.origin().dimension().equals(contract.destination().dimension());
        return new BoardSnapshotPayload.BoardEntry(
                contract.id(),
                contract.shortId(),
                contract.state(),
                role,
                originRevealed ? label(atlas, contract.origin()) : "",
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
                originRevealed ? contract.origin().dimension() : contract.destination().dimension(),
                originRevealed ? contract.origin().pos() : BlockPos.ZERO,
                contract.destination().dimension(),
                contract.destination().pos(),
                cargoItems,
                customerStationReputation,
                escrow.holding(contract.id()).villageRare(),
                customerTier,
                customerNext,
                customerBonus,
                originRevealed,
                crossDimension);
    }

    private static String label(StationAtlas atlas, Destination dest) {
        return atlas.all().stream()
                .filter(s -> s.dimension().equals(dest.dimension()) && s.pos().equals(dest.pos()))
                .map(StationAtlas.Station::name)
                .findFirst()
                .orElse(dest.pos().toShortString());
    }
}
