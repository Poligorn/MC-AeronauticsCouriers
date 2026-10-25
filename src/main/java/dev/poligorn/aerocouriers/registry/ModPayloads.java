package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.client.ClientZones;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ClaimMailPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OpenStampPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads (GDD §14.1 {@code net/}). C2S carry intents only; the server validates everything.
 */
public final class ModPayloads {
    public static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(ZoneSyncPayload.TYPE, ZoneSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientZones.set(payload.zones()));
        registrar.playToClient(BoardSnapshotPayload.TYPE, BoardSnapshotPayload.STREAM_CODEC,
                BoardSnapshotPayload::handle);
        registrar.playToClient(OpenStampPayload.TYPE, OpenStampPayload.STREAM_CODEC,
                OpenStampPayload::handle);

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
    }

    private ModPayloads() {
    }
}
