package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.client.board.AdminOrdersFilterState;
import dev.poligorn.aerocouriers.client.board.BoardTabFilterState;
import dev.poligorn.aerocouriers.client.board.MineListSection;
import dev.poligorn.aerocouriers.client.board.MineTabFilterState;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ClaimTraineeLicensePayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OpenWaybillCreatePayload;
import dev.poligorn.aerocouriers.net.PlayerProfilePayload;
import dev.poligorn.aerocouriers.net.RequestPlayerProfilePayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client entry for the job board: send the open intent and apply snapshots onto the screen.
 * Referenced from the S2C handler, so this class must stay loadable only when that handler runs (client).
 */
public final class ClientBoard {
    private static boolean requireTablet = true;
    private static boolean licensedCourier;
    private static boolean pilotLicenseHeld;
    private static BoardSnapshotPayload.Extras extras = BoardSnapshotPayload.Extras.EMPTY;
    private static @Nullable java.util.UUID pendingContractFocus;
    private static MineListSection mineSection = MineListSection.CURRENT;
    private static final BoardTabFilterState boardFilters = new BoardTabFilterState();
    private static final AdminOrdersFilterState adminOrderFilters = new AdminOrdersFilterState();
    private static final MineTabFilterState mineCurrentFilters = MineTabFilterState.defaults(MineListSection.CURRENT);
    private static final MineTabFilterState mineHistoryFilters = MineTabFilterState.defaults(MineListSection.HISTORY);
    private static @Nullable UUID viewedProfileId;
    private static @Nullable BoardSnapshotPayload.BoardProfile viewedProfile;

    private ClientBoard() {
    }

    public static boolean requiresTablet() {
        return requireTablet;
    }

    public static boolean canRequestOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return !requireTablet || OrderTablets.hasTablet(minecraft.player);
    }

    public static void requestOpen(int page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !canRequestOpen()) {
            minecraft.player.displayClientMessage(
                    Component.translatable("ui.aerocouriers.error.need_tablet"), true);
            ClientCouriersSounds.playOrderDeny();
            return;
        }
        if (!(minecraft.screen instanceof CourierBoardScreen)) {
            minecraft.setScreen(new CourierBoardScreen(null));
            ClientCouriersSounds.playTabletOpen();
        }
        PacketDistributor.sendToServer(new OpenBoardPayload(page));
    }

    public static void focusContract(UUID contractId) {
        pendingContractFocus = contractId;
        requestOpen(0);
    }

    public static @Nullable UUID takePendingContractFocus() {
        UUID id = pendingContractFocus;
        pendingContractFocus = null;
        return id;
    }

    public static MineListSection mineSection() {
        return mineSection;
    }

    public static void setMineSection(MineListSection section) {
        mineSection = section;
    }

    public static BoardTabFilterState boardFilters() {
        return boardFilters;
    }

    public static AdminOrdersFilterState adminOrderFilters() {
        return adminOrderFilters;
    }

    public static MineTabFilterState mineFilters(MineListSection section) {
        return section == MineListSection.HISTORY ? mineHistoryFilters : mineCurrentFilters;
    }

    public static BoardSnapshotPayload.Extras extras() {
        return extras;
    }

    public static dev.poligorn.aerocouriers.contract.CollateralProfile collateralProfile() {
        return extras.profile();
    }

    public static boolean knownLicensed() {
        return licensedCourier || pilotLicenseHeld;
    }

    public static void apply(BoardSnapshotPayload payload) {
        requireTablet = payload.requireTablet();
        extras = payload.extras();
        licensedCourier = payload.profile().licensedCourier();
        pilotLicenseHeld = payload.profile().pilotLicenseHeld();
        dev.poligorn.aerocouriers.economy.MoneyScale.reportBackend(payload.numismaticsEconomy());
        if (VillageRefreshUi.isEnabled(payload.nextVillageRefreshGameTime())) {
            VillageRefreshUi.applySnapshot(payload.nextVillageRefreshGameTime(), payload.snapshotOverworldGameTime());
        } else {
            VillageRefreshUi.clear();
        }
        XaeroOrderBridge.onBoardSnapshot(payload);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WaybillCreateScreen waybill && payload.openScreen()) {
            waybill.beginSeal(payload);
            return;
        }
        if (minecraft.screen instanceof CourierBoardScreen screen) {
            screen.apply(payload);
        } else if (payload.openScreen()) {
            minecraft.setScreen(new CourierBoardScreen(payload));
            ClientCouriersSounds.playTabletOpen();
        }
    }

    public static void onOrderDenied() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WaybillCreateScreen waybill) {
            waybill.onPublishDenied();
        }
    }

    public static void requestPlayerProfile(UUID playerId) {
        PacketDistributor.sendToServer(new RequestPlayerProfilePayload(playerId));
    }

    public static void applyPlayerProfile(PlayerProfilePayload payload) {
        viewedProfileId = payload.targetId();
        viewedProfile = payload.profile();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CourierBoardScreen screen) {
            screen.openViewedProfile(payload);
        }
    }

    public static void clearViewedProfile() {
        viewedProfileId = null;
        viewedProfile = null;
    }

    public static boolean viewingOtherProfile() {
        return viewedProfileId != null && viewedProfile != null;
    }

    public static @Nullable UUID viewedProfileId() {
        return viewedProfileId;
    }

    public static BoardSnapshotPayload.BoardProfile activeProfile(BoardSnapshotPayload.BoardProfile self) {
        return viewingOtherProfile() ? viewedProfile : self;
    }

    public static void claimTraineeLicense() {
        PacketDistributor.sendToServer(ClaimTraineeLicensePayload.INSTANCE);
    }

    private static final java.util.Set<UUID> seenBoardOrders = new java.util.HashSet<>();
    private static boolean boardSeenInitialized;

    /** Dot on «Orders»: an open order appeared that the player has not had on screen yet. */
    public static boolean hasUnseenBoardOrders(BoardSnapshotPayload payload) {
        if (!boardSeenInitialized) {
            markBoardSeen(payload);
            return false;
        }
        for (BoardSnapshotPayload.BoardEntry entry : payload.board()) {
            if (!"issuer".equals(entry.role()) && !seenBoardOrders.contains(entry.id())) {
                return true;
            }
        }
        return false;
    }

    public static void markBoardSeen(BoardSnapshotPayload payload) {
        boardSeenInitialized = true;
        for (BoardSnapshotPayload.BoardEntry entry : payload.board()) {
            seenBoardOrders.add(entry.id());
        }
    }

    public static void tutorial(dev.poligorn.aerocouriers.net.TutorialIntentPayload.Op op, int arg) {
        PacketDistributor.sendToServer(new dev.poligorn.aerocouriers.net.TutorialIntentPayload(op, arg));
    }

    public static void openWaybillCreate(OpenWaybillCreatePayload payload) {
        dev.poligorn.aerocouriers.economy.MoneyScale.reportBackend(payload.numismaticsEconomy());
        Minecraft.getInstance().setScreen(new WaybillCreateScreen(payload));
    }
}
