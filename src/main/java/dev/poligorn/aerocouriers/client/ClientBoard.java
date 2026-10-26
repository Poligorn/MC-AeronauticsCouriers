package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OpenWaybillCreatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client entry for the job board: send the open intent and apply snapshots onto the screen.
 * Referenced from the S2C handler, so this class must stay loadable only when that handler runs (client).
 */
public final class ClientBoard {
    private static boolean requireTablet = true;

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
            return;
        }
        if (!(minecraft.screen instanceof CourierBoardScreen)) {
            minecraft.setScreen(new CourierBoardScreen(null));
        }
        PacketDistributor.sendToServer(new OpenBoardPayload(page));
    }

    public static void apply(BoardSnapshotPayload payload) {
        requireTablet = payload.requireTablet();
        XaeroOrderBridge.onBoardSnapshot(payload);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CourierBoardScreen screen) {
            screen.apply(payload);
        } else if (minecraft.screen instanceof WaybillCreateScreen && !payload.openScreen()) {
            // stamp confirm replies with a board snapshot notice; close the stamp window
            if (payload.notice().isPresent()) {
                minecraft.setScreen(null);
            }
        } else if (payload.openScreen()) {
            minecraft.setScreen(new CourierBoardScreen(payload));
        }
    }

    public static void openWaybillCreate(OpenWaybillCreatePayload payload) {
        Minecraft.getInstance().setScreen(new WaybillCreateScreen(payload));
    }
}
