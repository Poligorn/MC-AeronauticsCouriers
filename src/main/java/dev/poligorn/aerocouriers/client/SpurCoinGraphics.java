package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.compat.numismatics.NumismaticsCoinRender;
import dev.poligorn.aerocouriers.economy.SpurDenomination;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.ModList;

/** Spur coin icons: Numismatics item renders when the mod is loaded, else fallback PNGs. */
public final class SpurCoinGraphics {
    private static final int TEX = 9;
    private static final String NUMISMATICS_ID = "numismatics";

    private SpurCoinGraphics() {
    }

    public static boolean useNumismaticsItems() {
        return ModList.get().isLoaded(NUMISMATICS_ID);
    }

    public static void drawGenericCoin(GuiGraphics graphics, int x, int y, int size) {
        if (useNumismaticsItems()) {
            NumismaticsCoinRender.drawGenericSpur(graphics, x, y, size);
            return;
        }
        graphics.blitSprite(AerocouriersGuiSprites.ICON_COIN, x, y, size, size);
    }

    public static void drawDenomination(GuiGraphics graphics, SpurDenomination denomination, int x, int y, int size) {
        if (useNumismaticsItems()) {
            NumismaticsCoinRender.drawDenomination(graphics, denomination, x, y, size);
            return;
        }
        graphics.blit(denomination.texture(), x, y, 0, 0, size, size, TEX, TEX);
    }
}
