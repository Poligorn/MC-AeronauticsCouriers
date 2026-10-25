package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client entry for the job board: send the open intent and apply snapshots onto the screen.
 * Referenced from the S2C handler, so this class must stay loadable only when that handler runs (client).
 */
public final class ClientBoard {
    private ClientBoard() {
    }

    public static void requestOpen(int page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof CourierBoardScreen)) {
            minecraft.setScreen(new CourierBoardScreen(null));
        }
        PacketDistributor.sendToServer(new OpenBoardPayload(page));
    }

    public static void apply(BoardSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CourierBoardScreen screen) {
            screen.apply(payload);
        } else if (payload.openScreen()) {
            minecraft.setScreen(new CourierBoardScreen(payload));
        }
    }
}
