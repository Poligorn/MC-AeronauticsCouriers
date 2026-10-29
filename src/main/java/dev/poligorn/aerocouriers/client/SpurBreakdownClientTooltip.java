package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.economy.MoneyFormat;
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

    private final long spurs;
    private final SpurBreakdown breakdown;
    private final Component footer;

    public SpurBreakdownClientTooltip(SpurBreakdownTooltip payload) {
        this.spurs = payload.spurs();
        this.breakdown = SpurBreakdown.greedy(payload.spurs());
        this.footer = Component.translatable("ui.aerocouriers.spur.tooltip.rate",
                Component.translatable("item.numismatics.spur"));
    }

    @Override
    public int getHeight() {
        int lines = Math.max(1, breakdown.lines().size());
        return PAD + (lines + 2) * LINE_H + PAD;
    }

    @Override
    public int getWidth(Font font) {
        int textMax = MoneyText.width(font, footer.getString());
        for (SpurBreakdown.Line line : breakdown.lines()) {
            textMax = Math.max(textMax, MoneyText.width(font, lineText(line).getString()));
        }
        int headerW = MoneyText.width(font, header());
        return PAD * 2 + Math.max(headerW, ICON + GAP + textMax);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int drawY = y + PAD;
        MoneyText.draw(graphics, font, header(), x + PAD, drawY, 0xFFFFFFFF, MoneyText.Face.DEFAULT);
        drawY += LINE_H;
        List<SpurBreakdown.Line> lines = breakdown.lines();
        if (lines.isEmpty()) {
            SpurCoinGraphics.drawGenericCoin(graphics, x + PAD, drawY, ICON);
            graphics.drawString(font, "0", x + PAD + ICON + GAP, drawY, 0xFFAF55, false);
            drawY += LINE_H;
        } else {
            for (SpurBreakdown.Line line : lines) {
                SpurCoinGraphics.drawDenomination(graphics, line.denomination(), x + PAD, drawY, ICON);
                MoneyText.draw(graphics, font, lineText(line).getString(), x + PAD + ICON + GAP, drawY, 0xFFAF55,
                        MoneyText.Face.DEFAULT);
                drawY += LINE_H;
            }
        }
        MoneyText.draw(graphics, font, footer.getString(), x + PAD, drawY + 1, 0xC87A3A, MoneyText.Face.DEFAULT);
    }

    private String header() {
        return Component.translatable("ui.aerocouriers.spur.tooltip.header", MoneyFormat.component(spurs)).getString();
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
