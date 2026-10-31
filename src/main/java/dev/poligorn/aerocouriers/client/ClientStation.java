package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.station.CorkBoardScreen;
import dev.poligorn.aerocouriers.client.station.VillageAdminScreen;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class ClientStation {
    private ClientStation() {
    }

    public static void apply(StationSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.view().openAdmin()) {
            if (minecraft.screen instanceof VillageAdminScreen admin) {
                admin.apply(payload);
            } else {
                minecraft.setScreen(new VillageAdminScreen(payload));
            }
            return;
        }
        if (minecraft.screen instanceof CorkBoardScreen board) {
            board.apply(payload);
        } else {
            minecraft.setScreen(new CorkBoardScreen(payload));
        }
    }
}
