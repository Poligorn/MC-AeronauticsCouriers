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
    /** Inline coin on a text line. Same 8px as the waybill; the 16px row icon does not fit. */
    private static final int INLINE_ICON = 8;
    private static final int INLINE_GAP = 2;

    private SpurAmountUi() {
    }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public static int width(Font font, long amount, boolean lockPrefix) {
        int w = MoneyText.width(font, dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount));
        if (!MoneyText.iconUnits()) {
            w += ICON + ICON_TEXT_GAP;
        }
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
        if (!MoneyText.iconUnits()) {
            SpurCoinGraphics.drawGenericCoin(graphics, cx, y, ICON);
            cx += ICON + ICON_TEXT_GAP;
        }
        int textY = y + Math.max(0, (lockPrefix || !MoneyText.iconUnits() ? ICON : TAB_FONT_H) - 8);
        String value = dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount);
        MoneyText.draw(graphics, font, value, cx, textY, textColor, MoneyText.Face.DEFAULT);
        int w = cx + MoneyText.width(font, value) - x;
        return new Bounds(x, y, w, Math.max(ICON, 9));
    }

    /**
     * Header balance: amount and currency share the vertical centre of the tab row,
     * the same centre as the tab labels. Width includes the emerald icon or spur-word tracking.
     * Without Numismatics the emerald sits after the number; with Numismatics the coin stays on the left.
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
        int textY = rowY + Math.max(0, (rowH - TAB_FONT_H) / 2);
        String value = dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount);
        int textX = x;
        int iconY = rowY + Math.max(0, (rowH - ICON) / 2);
        if (!MoneyText.iconUnits()) {
            SpurCoinGraphics.drawGenericCoin(graphics, x, iconY, ICON);
            textX = x + ICON + ICON_TEXT_GAP;
        }
        MoneyText.draw(graphics, font, value, textX, textY, textColor, MoneyText.Face.DEFAULT);
        int w = textX + MoneyText.width(font, value) - x;
        int top = MoneyText.iconUnits() ? textY : Math.min(iconY, textY);
        int bottom = MoneyText.iconUnits()
                ? textY + TAB_FONT_H
                : Math.max(iconY + ICON, textY + TAB_FONT_H);
        return new Bounds(x, top, w, bottom - top);
    }

    /**
     * Label and amount on one text line. Without Numismatics the emerald is 8px after the number.
     * With Numismatics the coin is 8px before the amount, sharing the label baseline.
     */
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
        int labelW = font.width(label);
        int amountX = x + labelW;
        String raw = dev.poligorn.aerocouriers.economy.MoneyFormat.text(amount);
        boolean icons = MoneyText.iconUnits();
        int reserved = icons ? 0 : INLINE_ICON + INLINE_GAP;
        int textMax = Math.max(4, maxWidth - labelW - reserved);
        String shown = MoneyText.width(font, raw) > textMax ? MoneyText.trunc(font, raw, textMax) : raw;
        int textX = amountX;
        int iconY = y + Math.max(0, (TAB_FONT_H - INLINE_ICON) / 2);
        if (!icons) {
            SpurCoinGraphics.drawGenericCoin(graphics, amountX, iconY, INLINE_ICON);
            textX = amountX + INLINE_ICON + INLINE_GAP;
        }
        MoneyText.draw(graphics, font, shown, textX, y, amountColor, MoneyText.Face.DEFAULT);
        int width = textX + MoneyText.width(font, shown) - x;
        int height = Math.max(TAB_FONT_H, iconY + INLINE_ICON - y);
        return new Bounds(x, y, Math.min(maxWidth, width), height);
    }
}
