package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.client.board.AdminOrdersFilterState;
import dev.poligorn.aerocouriers.client.board.BoardTabFilterState;
import dev.poligorn.aerocouriers.client.board.MineListSection;
import dev.poligorn.aerocouriers.client.board.MineTabFilterState;
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

    public static void apply(BoardSnapshotPayload payload) {
        requireTablet = payload.requireTablet();
        XaeroOrderBridge.onBoardSnapshot(payload);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CourierBoardScreen screen) {
            screen.apply(payload);
        } else if (payload.openScreen()) {
            minecraft.setScreen(new CourierBoardScreen(payload));
            ClientCouriersSounds.playTabletOpen();
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

    public static BoardSnapshotPayload.BoardProfile activeProfile(BoardSnapshotPayload.BoardProfile self) {
        return viewingOtherProfile() ? viewedProfile : self;
    }

    public static void claimTraineeLicense() {
        PacketDistributor.sendToServer(ClaimTraineeLicensePayload.INSTANCE);
    }

    public static void openWaybillCreate(OpenWaybillCreatePayload payload) {
        Minecraft.getInstance().setScreen(new WaybillCreateScreen(payload));
    }
}
