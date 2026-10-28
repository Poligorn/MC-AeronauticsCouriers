package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.economy.SpurBreakdown;
import dev.poligorn.aerocouriers.economy.SpurDenomination;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public final class SpurBreakdownClientTooltip implements ClientTooltipComponent {
    private static final int ICON = 9;
    private static final int LINE_H = 10;
    private static final int PAD = 4;
    private static final int GAP = 3;

    private final SpurBreakdown breakdown;
    private final Component footer;

    public SpurBreakdownClientTooltip(SpurBreakdownTooltip payload) {
        this.breakdown = SpurBreakdown.greedy(payload.spurs());
        this.footer = Component.translatable("ui.aerocouriers.spur.tooltip.rate");
    }

    @Override
    public int getHeight() {
        int lines = Math.max(1, breakdown.lines().size());
        return PAD + lines * LINE_H + LINE_H + PAD;
    }

    @Override
    public int getWidth(Font font) {
        int max = font.width(footer);
        for (SpurBreakdown.Line line : breakdown.lines()) {
            max = Math.max(max, font.width(lineText(line)));
        }
        return PAD * 2 + ICON + GAP + max;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int drawY = y + PAD;
        List<SpurBreakdown.Line> lines = breakdown.lines();
        if (lines.isEmpty()) {
            SpurCoinGraphics.drawGenericCoin(graphics, x + PAD, drawY, ICON);
            graphics.drawString(font, "0", x + PAD + ICON + GAP, drawY, 0xFFAF55, false);
            drawY += LINE_H;
        } else {
            for (SpurBreakdown.Line line : lines) {
                SpurCoinGraphics.drawDenomination(graphics, line.denomination(), x + PAD, drawY, ICON);
                graphics.drawString(font, lineText(line), x + PAD + ICON + GAP, drawY, 0xFFAF55, false);
                drawY += LINE_H;
            }
        }
        graphics.drawString(font, footer, x + PAD, drawY + 1, 0xC87A3A, false);
    }

    private static Component lineText(SpurBreakdown.Line line) {
        SpurDenomination denom = line.denomination();
        return Component.translatable(
                "ui.aerocouriers.spur.tooltip.denom_line",
                line.count(),
                Component.translatable(denom.nameTranslationKey()),
                Component.translatable(denom.englishNameTranslationKey()),
                denom.value());
    }
}
