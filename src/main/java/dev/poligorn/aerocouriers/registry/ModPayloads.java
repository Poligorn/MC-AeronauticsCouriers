package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.client.ClientZones;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ClaimMailPayload;
import dev.poligorn.aerocouriers.net.InboxClaimPayload;
import dev.poligorn.aerocouriers.net.InboxClearPlainPayload;
import dev.poligorn.aerocouriers.net.InboxDismissPayload;
import dev.poligorn.aerocouriers.net.InboxPackets;
import dev.poligorn.aerocouriers.net.ClaimTraineeLicensePayload;
import dev.poligorn.aerocouriers.net.PlayerProfilePayload;
import dev.poligorn.aerocouriers.net.ProfilePackets;
import dev.poligorn.aerocouriers.net.RequestPlayerProfilePayload;
import dev.poligorn.aerocouriers.net.CouriersUiSoundPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.MarkNotificationsReadPayload;
import dev.poligorn.aerocouriers.net.NotificationAlertPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OpenStationPayload;
import dev.poligorn.aerocouriers.net.OpenWaybillCreatePayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import dev.poligorn.aerocouriers.net.RenameStationPayload;
import dev.poligorn.aerocouriers.net.StationPackets;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import dev.poligorn.aerocouriers.net.TaskPrefPayload;
import dev.poligorn.aerocouriers.net.TaskSyncPayload;
import dev.poligorn.aerocouriers.net.TutorialIntentPayload;
import dev.poligorn.aerocouriers.task.TaskSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads (GDD §14.1 {@code net/}). C2S carry intents only; the server validates everything.
 */
public final class ModPayloads {
    public static final String PROTOCOL_VERSION = "3";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(ZoneSyncPayload.TYPE, ZoneSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientZones.set(payload.zones()));
        registrar.playToClient(BoardSnapshotPayload.TYPE, BoardSnapshotPayload.STREAM_CODEC,
                BoardSnapshotPayload::handle);
        registrar.playToClient(OpenWaybillCreatePayload.TYPE, OpenWaybillCreatePayload.STREAM_CODEC,
                OpenWaybillCreatePayload::handle);
        registrar.playToClient(NotificationSyncPayload.TYPE, NotificationSyncPayload.STREAM_CODEC,
                NotificationSyncPayload::handle);
        registrar.playToClient(NotificationAlertPayload.TYPE, NotificationAlertPayload.STREAM_CODEC,
                NotificationAlertPayload::handle);
        registrar.playToClient(CouriersUiSoundPayload.TYPE, CouriersUiSoundPayload.STREAM_CODEC,
                CouriersUiSoundPayload::handle);
        registrar.playToClient(StationSnapshotPayload.TYPE, StationSnapshotPayload.STREAM_CODEC,
                StationSnapshotPayload::handle);
        registrar.playToClient(PlayerProfilePayload.TYPE, PlayerProfilePayload.STREAM_CODEC,
                PlayerProfilePayload::handle);
        registrar.playToClient(TaskSyncPayload.TYPE, TaskSyncPayload.STREAM_CODEC, TaskSyncPayload::handle);
        registrar.playToServer(TaskPrefPayload.TYPE, TaskPrefPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TaskSync.handlePref(player, payload);
                    }
                }));

        registrar.playToServer(OpenBoardPayload.TYPE, OpenBoardPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BoardPackets.handleOpen(player, payload);
                    }
                }));
        registrar.playToServer(ClaimMailPayload.TYPE, ClaimMailPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BoardPackets.handleClaim(player);
                    }
                }));
        registrar.playToServer(MarkNotificationsReadPayload.TYPE, MarkNotificationsReadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MarkNotificationsReadPayload.handle(payload, player);
                    }
                }));
        registrar.playToServer(InboxDismissPayload.TYPE, InboxDismissPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        InboxPackets.handleDismiss(player, payload);
                    }
                }));
        registrar.playToServer(InboxClaimPayload.TYPE, InboxClaimPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        InboxPackets.handleClaim(player, payload);
                    }
                }));
        registrar.playToServer(InboxClearPlainPayload.TYPE, InboxClearPlainPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        InboxPackets.handleClearPlain(player);
                    }
                }));
        registrar.playToServer(ContractIntentPayload.TYPE, ContractIntentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BoardPackets.handleIntent(player, payload);
                    }
                }));
        registrar.playToServer(CreateContractPayload.TYPE, CreateContractPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BoardPackets.handleCreate(player, payload);
                    }
                }));
        registrar.playToServer(OperatorBoardPayload.TYPE, OperatorBoardPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BoardPackets.handleOperator(player, payload);
                    }
                }));
        registrar.playToServer(OpenStationPayload.TYPE, OpenStationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StationPackets.handleOpen(player, payload);
                    }
                }));
        registrar.playToServer(RenameStationPayload.TYPE, RenameStationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StationPackets.handleRename(player, payload);
                    }
                }));
        registrar.playToServer(RequestPlayerProfilePayload.TYPE, RequestPlayerProfilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ProfilePackets.handleRequest(player, payload);
                    }
                }));
        registrar.playToServer(ClaimTraineeLicensePayload.TYPE, ClaimTraineeLicensePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ProfilePackets.handleClaimTrainee(player);
                    }
                }));
        registrar.playToServer(TutorialIntentPayload.TYPE, TutorialIntentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ProfilePackets.handleTutorial(player, payload);
                    }
                }));
    }

    private ModPayloads() {
    }
}
