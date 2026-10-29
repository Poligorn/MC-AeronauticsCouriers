package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.BoardRowMetrics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Compact coin icon + spur count for tablet GUI. */
public final class SpurAmountUi {
    public static final int ICON = BoardRowMetrics.LINE2_ICON;
    private static final int ICON_TEXT_GAP = BoardRowMetrics.ICON_TEXT_GAP;
    /** Bitmap height {@link TabletTabButton} uses to centre a tab label in the row. */
    private static final int TAB_FONT_H = 8;

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

    /**
     * Header balance: the coin and the amount share the vertical centre of the tab row,
     * the same centre as the tab labels. Width includes currency-word tracking.
     */
    public static Bounds drawCenteredInRow(
            GuiGraphics graphics,
            Font font,
            long amount,
            int x,
            int rowY,
            int rowH,
            int textColor
    ) {
        int iconY = rowY + Math.max(0, (rowH - ICON) / 2);
        int textY = rowY + Math.max(0, (rowH - TAB_FONT_H) / 2);
        SpurCoinGraphics.drawGenericCoin(graphics, x, iconY, ICON);
        int textX = x + ICON + ICON_TEXT_GAP;
        String value = dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount);
        MoneyText.draw(graphics, font, value, textX, textY, textColor, MoneyText.Face.DEFAULT);
        int w = textX + MoneyText.width(font, value) - x;
        int top = Math.min(iconY, textY);
        int bottom = Math.max(iconY + ICON, textY + TAB_FONT_H);
        return new Bounds(x, top, w, bottom - top);
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
