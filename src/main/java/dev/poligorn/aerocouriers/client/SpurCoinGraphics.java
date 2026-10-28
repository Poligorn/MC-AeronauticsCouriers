package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.economy.SpurDenomination;
import net.minecraft.client.gui.GuiGraphics;

/** Binds spur denomination PNGs under {@code textures/gui/coins/}. */
public final class SpurCoinGraphics {
    private static final int TEX = 9;

    private SpurCoinGraphics() {
    }

    public static void drawGenericCoin(GuiGraphics graphics, int x, int y, int size) {
        graphics.blitSprite(AerocouriersGuiSprites.ICON_COIN, x, y, size, size);
    }

    public static void drawDenomination(GuiGraphics graphics, SpurDenomination denomination, int x, int y, int size) {
        graphics.blit(denomination.texture(), x, y, 0, 0, size, size, TEX, TEX);
    }
}
