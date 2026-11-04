package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.TabletAnim;
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
        return draw(graphics, font, x, y, maxWidth, fraction, xpInRank, span, 0.0F);
    }

    public static Layout draw(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int maxWidth,
            float fraction,
            int xpInRank,
            int span,
            float rankUpFlash
    ) {
        Component xpText = xpFractionText(xpInRank, span);
        int textW = font.width(xpText);
        int lineH = Math.max(BAR_HEIGHT, font.lineHeight);
        int available = maxWidth - textW - TEXT_GAP;
        if (available >= MIN_BAR_WIDTH) {
            int barW = available;
            drawBar(graphics, x, y, barW, fraction, rankUpFlash);
            int textX = x + barW + TEXT_GAP;
            int textY = y + (BAR_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, xpText, textX, textY, TabletGui.TEXT, false);
            return new Layout(x, y, barW, BAR_HEIGHT, textX, textY, lineH);
        }
        drawBar(graphics, x, y, maxWidth, fraction, rankUpFlash);
        int textY = y + BAR_HEIGHT + 2;
        graphics.drawString(font, xpText, x, textY, TabletGui.TEXT, false);
        return new Layout(x, y, maxWidth, BAR_HEIGHT, x, textY, textY + font.lineHeight - y);
    }

    /** Height of {@link #draw} for this width — text beside the bar, or wrapped under it. */
    public static int blockHeight(Font font, int maxWidth, int xpInRank, int span) {
        int textW = font.width(xpFractionText(xpInRank, span));
        int available = maxWidth - textW - TEXT_GAP;
        if (available >= MIN_BAR_WIDTH) {
            return Math.max(BAR_HEIGHT, font.lineHeight);
        }
        return BAR_HEIGHT + 2 + font.lineHeight;
    }

    private static Component xpFractionText(int xpInRank, int span) {
        return Component.translatable("ui.aerocouriers.profile.xp_fraction", xpInRank, span);
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int w, float fraction, float rankUpFlash) {
        if (w <= 0) {
            return;
        }
        int frame = rankUpFlash > 0.001F
                ? blendTowardWhite(FRAME_COLOR, rankUpFlash * 0.55F)
                : FRAME_COLOR;
        graphics.renderOutline(x, y, w, BAR_HEIGHT, frame);
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
            int fill = rankUpFlash > 0.001F
                    ? blendTowardWhite(FILL_COLOR, rankUpFlash * 0.65F)
                    : FILL_COLOR;
            graphics.fill(innerX, innerY, innerX + fillW, innerY + innerH, fill);
        }
        if (rankUpFlash > 0.001F) {
            graphics.fill(x, y, x + w, y + BAR_HEIGHT, TabletAnim.withAlpha(0x50FFFFFF, rankUpFlash * 0.45F));
        }
    }

    private static int blendTowardWhite(int argb, float amount) {
        float a = amount * (argb >>> 24) / 255.0F;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int nr = (int) (r + (255 - r) * amount);
        int ng = (int) (g + (255 - g) * amount);
        int nb = (int) (b + (255 - b) * amount);
        int na = (int) Math.min(255, (argb >>> 24) + a * 255);
        return na << 24 | nr << 16 | ng << 8 | nb;
    }

    public static String formatCollateral(long spurs) {
        if (spurs >= Long.MAX_VALUE / 2) {
            return "∞";
        }
        return dev.poligorn.aerocouriers.economy.MoneyFormat.text(spurs);
    }
}
