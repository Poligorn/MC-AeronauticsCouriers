package dev.poligorn.aerocouriers.registry;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads (GDD §14.1 {@code net/}). C2S carry intents only; the server validates everything.
 * Nothing is registered until the UI iteration (4).
 */
public final class ModPayloads {
    public static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
    }

    private ModPayloads() {
    }
}
