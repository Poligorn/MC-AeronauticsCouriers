package dev.poligorn.aerocouriers.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/** Unread counter on the order tablet in hotbar / inventory slots; coral and pulsing while something urgent is unread. */
public final class TabletBadgeDecorator implements IItemDecorator {
    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        int unread = ClientNotifications.unreadCount();
        if (unread <= 0) {
            return false;
        }
        drawBadge(graphics, font, x + 17, y - 1, unread, ClientNotifications.unreadUrgent());
        return true;
    }

    /** Badge whose top-right corner is at ({@code right}, {@code top}). */
    public static void drawBadge(GuiGraphics graphics, Font font, int right, int top, int count, boolean urgent) {
        String text = count > 9 ? "9+" : Integer.toString(count);
        int w = Math.max(7, font.width(text) + 3);
        int left = right - w;
        int color = urgent ? ClientToasts.CORAL : ClientToasts.BLUE;
        if (urgent) {
            float pulse = 0.65F + 0.35F * Mth.sin((Util.getMillis() % 1200L) / 1200F * Mth.TWO_PI);
            int alpha = Mth.clamp(Math.round(pulse * 255), 0, 255);
            color = (alpha << 24) | (color & 0x00FFFFFF);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        graphics.fill(left, top, right, top + 9, color);
        graphics.drawString(font, text, left + (w - font.width(text) + 1) / 2, top + 1, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }
}
