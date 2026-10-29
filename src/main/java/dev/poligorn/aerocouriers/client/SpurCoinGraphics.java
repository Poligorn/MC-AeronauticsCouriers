package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.compat.numismatics.NumismaticsCoinRender;
import dev.poligorn.aerocouriers.economy.SpurDenomination;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Currency icons: Numismatics coin items when that mod is loaded, otherwise a vanilla emerald. */
public final class SpurCoinGraphics {
    private static final String NUMISMATICS_ID = "numismatics";
    private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);

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
        drawEmerald(graphics, x, y, size);
    }

    public static void drawDenomination(GuiGraphics graphics, SpurDenomination denomination, int x, int y, int size) {
        if (useNumismaticsItems()) {
            NumismaticsCoinRender.drawDenomination(graphics, denomination, x, y, size);
            return;
        }
        drawEmerald(graphics, x, y, size);
    }

    /** Vanilla emerald item, scaled to {@code size} pixels. No custom currency texture. */
    private static void drawEmerald(GuiGraphics graphics, int x, int y, int size) {
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(EMERALD, 0, 0);
        graphics.pose().popPose();
    }
}
