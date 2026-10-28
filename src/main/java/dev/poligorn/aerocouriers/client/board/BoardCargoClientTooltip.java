package dev.poligorn.aerocouriers.client.board;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class BoardCargoClientTooltip implements ClientTooltipComponent {
    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int PAD = 4;

    private final List<ItemStack> items;
    private final int hiddenStacks;
    private final String weightLine;

    public BoardCargoClientTooltip(BoardCargoTooltip payload) {
        this.items = payload.items();
        this.hiddenStacks = payload.hiddenStacks();
        this.weightLine = payload.weightLine();
    }

    @Override
    public int getHeight() {
        int rows = Math.max(1, (items.size() + COLS - 1) / COLS);
        int gridH = rows * SLOT;
        int footer = weightLine.isEmpty() ? 0 : 10;
        int more = hiddenStacks > 0 ? 10 : 0;
        return PAD + gridH + footer + more + PAD;
    }

    @Override
    public int getWidth(Font font) {
        int cols = Math.min(COLS, Math.max(1, items.size()));
        int gridW = cols * SLOT;
        int moreW = hiddenStacks > 0 ? font.width("+" + hiddenStacks + " …") : 0;
        int weightW = weightLine.isEmpty() ? 0 : font.width(weightLine);
        return PAD * 2 + Math.max(gridW, Math.max(moreW, weightW));
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int drawY = y + PAD;
        for (int i = 0; i < items.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int sx = x + PAD + col * SLOT;
            int sy = drawY + row * SLOT;
            ItemStack stack = items.get(i);
            graphics.renderItem(stack, sx, sy);
            graphics.renderItemDecorations(font, stack, sx, sy);
        }
        drawY += ((items.size() + COLS - 1) / COLS) * SLOT;
        if (!weightLine.isEmpty()) {
            graphics.drawString(font, weightLine, x + PAD, drawY + 2, 0xFFAF55, false);
            drawY += 10;
        }
        if (hiddenStacks > 0) {
            Component more = Component.translatable("ui.aerocouriers.row.cargo_more", hiddenStacks);
            graphics.drawString(font, more, x + PAD, drawY + 2, 0xC87A3A, false);
        }
    }
}
