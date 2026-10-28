package dev.poligorn.aerocouriers.client.compat.numismatics;

import dev.poligorn.aerocouriers.economy.SpurDenomination;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Renders Numismatics coin items from the item registry (no copied textures). */
public final class NumismaticsCoinRender {
    private static final String MOD_ID = "numismatics";

    private NumismaticsCoinRender() {
    }

    public static void drawGenericSpur(GuiGraphics graphics, int x, int y, int size) {
        drawCoinId(graphics, SpurDenomination.SPUR.coinId(), x, y, size);
    }

    public static void drawDenomination(GuiGraphics graphics, SpurDenomination denomination, int x, int y, int size) {
        drawCoinId(graphics, denomination.coinId(), x, y, size);
    }

    private static void drawCoinId(GuiGraphics graphics, String coinId, int x, int y, int size) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, coinId);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x + (size - 16.0F * scale) / 2.0F, y + (size - 16.0F * scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }
}
