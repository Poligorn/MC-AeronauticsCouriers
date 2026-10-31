package dev.poligorn.aerocouriers.client.station;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Station UI must not cover the world. Chat / vanilla HUD / toasts are skipped while the cork
 * board or village admin is open (no full-screen mask, no dim).
 */
public final class CorkHudGuard {
    private CorkHudGuard() {
    }

    public static boolean stationUiOpen() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof CorkBoardScreen || screen instanceof VillageAdminScreen;
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
