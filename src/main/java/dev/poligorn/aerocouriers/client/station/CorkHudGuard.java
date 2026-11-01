package dev.poligorn.aerocouriers.client.station;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Station UI must not cover the world. While the cork board or village admin is open the vanilla
 * {@code Gui} (chat, hotbar, boss bar, overlay, toasts-in-gui) is skipped — no full-screen mask
 * and no dim. The world stays visible in the frame's transparent margin.
 */
public final class CorkHudGuard {
    private CorkHudGuard() {
    }

    public static boolean stationUiOpen() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof CorkBoardScreen
                || screen instanceof VillageAdminScreen
                || screen instanceof StationNameScreen;
    }

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!stationUiOpen()) {
            return;
        }
        event.setCanceled(true);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getToasts().clear();
        }
    }
}
