package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Default key is J (M is taken by Create: Marketplace). Unbind in Controls if another pack already uses it. */
final class BoardKeyMappings {
    static final KeyMapping OPEN_BOARD = new KeyMapping(
            "key.aerocouriers.open_board",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.aerocouriers");

    /** Unbound by default: packs already use most letters (K included). */
    static final KeyMapping TASK_PANEL = new KeyMapping(
            "key.aerocouriers.task_panel",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.aerocouriers");

    private BoardKeyMappings() {
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOARD);
        event.register(TASK_PANEL);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (TASK_PANEL.consumeClick()) {
            if (minecraft.screen == null) {
                TaskPanelHud.cycleMode();
            }
        }
        while (OPEN_BOARD.consumeClick()) {
            if (minecraft.screen instanceof CourierBoardScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.player != null
                    && (minecraft.screen == null || minecraft.screen instanceof InventoryScreen)) {
                minecraft.setScreen(null);
                ClientBoard.requestOpen(0);
            }
        }
    }
}
