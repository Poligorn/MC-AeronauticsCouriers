package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.TabletGui;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Rank XP bar on the board profile tab (brass frame, orange fill on #0A0200). */
public final class ProfileRankBar {
    public static final int BAR_HEIGHT = 8;
    private static final int FRAME_COLOR = 0xFFB36833;
    private static final int TRACK_COLOR = 0xFF0A0200;
    private static final int FILL_COLOR = 0xFFFF9E00;
    private static final int TEXT_GAP = 4;
    private static final int MIN_BAR_WIDTH = 24;

    private ProfileRankBar() {
    }

    public record Layout(int barX, int barY, int barW, int barH, int textX, int textY, int blockH) {
        public boolean contains(int mouseX, int mouseY) {
            int right = Math.max(barX + barW, textX + 1);
            int bottom = barY + blockH;
            return mouseX >= barX && mouseX < right && mouseY >= barY && mouseY < bottom;
        }
    }

    public static Layout draw(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int maxWidth,
            float fraction,
            int xpInRank,
            int span
    ) {
        Component xpText = Component.translatable(
                "ui.aerocouriers.profile.xp_fraction", xpInRank, span);
        int textW = font.width(xpText);
        int lineH = Math.max(BAR_HEIGHT, font.lineHeight);
        int available = maxWidth - textW - TEXT_GAP;
        if (available >= MIN_BAR_WIDTH) {
            int barW = available;
            drawBar(graphics, x, y, barW, fraction);
            int textX = x + barW + TEXT_GAP;
            int textY = y + (BAR_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, xpText, textX, textY, TabletGui.TEXT, false);
            return new Layout(x, y, barW, BAR_HEIGHT, textX, textY, lineH);
        }
        drawBar(graphics, x, y, maxWidth, fraction);
        int textY = y + BAR_HEIGHT + 2;
        graphics.drawString(font, xpText, x, textY, TabletGui.TEXT, false);
        return new Layout(x, y, maxWidth, BAR_HEIGHT, x, textY, textY + font.lineHeight - y);
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int w, float fraction) {
        if (w <= 0) {
            return;
        }
        graphics.renderOutline(x, y, w, BAR_HEIGHT, FRAME_COLOR);
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = w - 2;
        int innerH = BAR_HEIGHT - 2;
        if (innerW <= 0 || innerH <= 0) {
            return;
        }
        graphics.fill(innerX, innerY, innerX + innerW, innerY + innerH, TRACK_COLOR);
        int fillW = Math.max(0, Math.min(innerW, Math.round(innerW * Math.max(0.0F, Math.min(1.0F, fraction)))));
        if (fillW > 0) {
            graphics.fill(innerX, innerY, innerX + fillW, innerY + innerH, FILL_COLOR);
        }
    }

    public static String formatCollateral(long spurs) {
        if (spurs >= Long.MAX_VALUE / 2) {
            return "∞";
        }
        return String.format(Locale.ROOT, "%d", spurs);
    }
}
