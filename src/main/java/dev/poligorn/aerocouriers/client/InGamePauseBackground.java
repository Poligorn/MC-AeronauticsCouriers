package dev.poligorn.aerocouriers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * In-world pause screens ({@link CourierBoardScreen}, {@link WaybillCreateScreen}) must not call
 * {@link Screen#renderBackground(GuiGraphics, int, int, float)} with blur: NeoForge 21.1 runs
 * {@code processBlurEffect} there, which breaks sprite atlas draws for the rest of the frame
 * (tabs/frame vanish while {@link TabletGui#drawScreenBackground} fills still show).
 */
public final class InGamePauseBackground {
    private InGamePauseBackground() {
    }

    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(screen, graphics, mouseX, mouseY, partialTick, 1.0F);
    }

    static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float dimAlpha) {
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft != null && minecraft.level != null) {
            if (dimAlpha > 0.001F) {
                TabletAnim.withAlpha(graphics, dimAlpha, () -> screen.renderTransparentBackground(graphics));
            }
        } else {
            screen.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }
}
