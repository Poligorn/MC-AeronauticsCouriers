package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.BoardRowMetrics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Compact coin icon + spur count for tablet GUI. */
public final class SpurAmountUi {
    public static final int ICON = BoardRowMetrics.LINE2_ICON;
    private static final int ICON_TEXT_GAP = BoardRowMetrics.ICON_TEXT_GAP;

    private SpurAmountUi() {
    }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public static int width(Font font, long amount, boolean lockPrefix) {
        int w = ICON + ICON_TEXT_GAP + MoneyText.width(font, dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount));
        if (lockPrefix) {
            w += ICON + ICON_TEXT_GAP;
        }
        return w;
    }

    public static Bounds draw(
            GuiGraphics graphics,
            Font font,
            long amount,
            int x,
            int y,
            boolean lockPrefix,
            int textColor
    ) {
        int cx = x;
        if (lockPrefix) {
            graphics.blitSprite(AerocouriersGuiSprites.ICON_LOCK, cx, y, ICON, ICON);
            cx += ICON + ICON_TEXT_GAP;
        }
        SpurCoinGraphics.drawGenericCoin(graphics, cx, y, ICON);
        int textX = cx + ICON + ICON_TEXT_GAP;
        int textY = y + Math.max(0, ICON - 8);
        String value = dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount);
        MoneyText.draw(graphics, font, value, textX, textY, textColor, MoneyText.Face.DEFAULT);
        int w = textX + MoneyText.width(font, value) - x;
        return new Bounds(x, y, w, Math.max(ICON, 9));
    }

    public static Bounds drawLabeled(
            GuiGraphics graphics,
            Font font,
            String label,
            long amount,
            int x,
            int y,
            int maxWidth,
            int labelColor,
            int amountColor
    ) {
        graphics.drawString(font, label, x, y, labelColor, false);
        int amountX = x + font.width(label);
        int amountMaxW = Math.max(8, maxWidth - font.width(label));
        if (width(font, amount, false) > amountMaxW) {
            String raw = dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount);
            String shown = MoneyText.trunc(font, raw, Math.max(4, amountMaxW - ICON - ICON_TEXT_GAP));
            SpurCoinGraphics.drawGenericCoin(graphics, amountX, y, ICON);
            int textX = amountX + ICON + ICON_TEXT_GAP;
            MoneyText.draw(graphics, font, shown, textX, y, amountColor, MoneyText.Face.DEFAULT);
            return new Bounds(x, y, Math.min(maxWidth, textX + MoneyText.width(font, shown) - x), Math.max(ICON, 9));
        }
        Bounds amountBounds = draw(graphics, font, amount, amountX, y, false, amountColor);
        return new Bounds(x, y, amountBounds.x() + amountBounds.width() - x, amountBounds.height());
    }
}
