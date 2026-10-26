package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class ClientStation {
    private ClientStation() {
    }

    public static void apply(StationSnapshotPayload payload) {
        Minecraft.getInstance().setScreen(new PostStationScreen(payload));
    }
}
