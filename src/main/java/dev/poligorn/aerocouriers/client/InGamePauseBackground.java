package dev.poligorn.aerocouriers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * In-world pause screens ({@link CourierBoardScreen}, {@link StampCreateScreen}) must not call
 * {@link Screen#renderBackground(GuiGraphics, int, int, float)} with blur: NeoForge 21.1 runs
 * {@code processBlurEffect} there, which breaks sprite atlas draws for the rest of the frame
 * (tabs/frame vanish while {@link TabletGui#drawScreenBackground} fills still show).
 */
final class InGamePauseBackground {
    private InGamePauseBackground() {
    }

    static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft != null && minecraft.level != null) {
            screen.renderTransparentBackground(graphics);
        } else {
            screen.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }
}
