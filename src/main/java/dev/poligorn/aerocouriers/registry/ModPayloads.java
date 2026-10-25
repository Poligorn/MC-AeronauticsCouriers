package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.client.ClientZones;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
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
    }

    private ModPayloads() {
    }
}
