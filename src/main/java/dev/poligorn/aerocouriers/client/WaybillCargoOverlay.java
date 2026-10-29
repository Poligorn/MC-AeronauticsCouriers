package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.board.TabletDraw;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Full cargo grid for containers larger than 9 slots. Same paper popup as the destination picker.
 * Anchored under the «all cells» link; clamped so it stays on the sheet, under the clip and above the buttons.
 */
final class WaybillCargoOverlay {
    private static final int PAD = 7;
    private static final int HEADER = 21;

    private boolean open;
    private int x0;
    private int y0;
    private int w;
    private int h;
    private int rows;
    private int slots;
    private int notchX;

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    void open(WaybillCreateLayout layout, int slotCount, int linkTextX) {
        slots = Math.max(1, slotCount);
        notchX = linkTextX + 14;
        rows = Math.max(1, (slots + 8) / 9);
        w = 9 * WaybillCreateLayout.SLOT + 14;
        h = rows * WaybillCreateLayout.SLOT + 62;
        x0 = layout.contentX0 - 2;
        y0 = layout.occupiedY + 12;
        int clipBottom = layout.paperY0 + 18;
        int aboveButtons = layout.buttonY - 4;
        if (y0 + h > aboveButtons) {
            y0 = aboveButtons - h;
        }
        if (y0 < clipBottom) {
            y0 = clipBottom;
        }
        if (x0 < layout.paperX0 + 2) {
            x0 = layout.paperX0 + 2;
        }
        if (x0 + w > layout.paperX1 - 2) {
            x0 = Math.max(layout.paperX0 + 2, layout.paperX1 - 2 - w);
        }
        open = true;
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x0 && mouseX < x0 + w && mouseY >= y0 && mouseY < y0 + h;
    }

    /** @return true when the click was consumed (inside, or outside — which closes the overlay only) */
    boolean handleClick(double mouseX, double mouseY, int button) {
        if (!open) {
            return false;
        }
        if (!contains(mouseX, mouseY)) {
            close();
        }
        return true;
    }

    @Nullable
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY, List<ItemStack> cargo, CargoSize size,
                     double weight) {
        if (!open) {
            return null;
        }
        // Above the sheet's item row (items sit near z=150, the +N badge at z=300).
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        try {
        WaybillCreateGui.fill(g, x0 + 3, y0 + 3, x0 + w + 1, y0 + h + 1, 0xFF1E160C);
        WaybillCreateGui.box(g, x0, y0, x0 + w - 1, y0 + h - 1, WaybillPalette.PAPER, WaybillPalette.BLUE);
        WaybillCreateGui.outline(g, x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2, WaybillPalette.PAPER3);
        int tip = Math.max(x0 + 10, Math.min(notchX, x0 + w - 10));
        for (int i = 0; i <= 5; i++) {
            int half = (int) Math.round(6.0 * i / 5.0);
            WaybillCreateGui.hline(g, tip - half, tip + half, y0 - 5 + i, WaybillPalette.BLUE);
        }
        String title = TabletDraw.trunc(font, Component.translatable("ui.aerocouriers.create.cargo_overlay.title",
                size.name(), cargo.size(), slots).getString(), w - 16);
        WaybillCreateGui.bold(g, font, title, x0 + PAD, y0 + 5, WaybillPalette.INK);
        WaybillCreateGui.hline(g, x0 + 4, x0 + w - 5, y0 + 16, WaybillPalette.RULE);

        ItemStack hovered = null;
        int gridY = y0 + HEADER;
        for (int i = 0; i < slots; i++) {
            int col = i % 9;
            int row = i / 9;
            int sx = x0 + PAD + col * WaybillCreateLayout.SLOT;
            int sy = gridY + row * WaybillCreateLayout.SLOT;
            WaybillCreateGui.slot(g, sx, sy);
            if (i < cargo.size() && !cargo.get(i).isEmpty()) {
                ItemStack stack = cargo.get(i);
                g.renderItem(stack, sx + 1, sy + 1);
                g.renderItemDecorations(font, stack, sx + 1, sy + 1, "");
                WaybillCreateGui.stackCount(g, font, stack, sx, sy);
                if (TabletDraw.inside(mouseX, mouseY, sx, sy, WaybillCreateLayout.SLOT, WaybillCreateLayout.SLOT)) {
                    hovered = stack;
                }
            }
        }
        int wy = gridY + rows * WaybillCreateLayout.SLOT + 5;
        WaybillCreateGui.text(g, font, Component.translatable("ui.aerocouriers.create.weight").getString(),
                x0 + PAD, wy, WaybillPalette.INK);
        String weightText = Component.translatable("ui.aerocouriers.create.weight_value",
                TabletDraw.weight(weight), TabletDraw.num(size.maxWeight())).getString();
        WaybillCreateGui.textRight(g, font, weightText, x0 + w - PAD, wy, WaybillPalette.INK, false);
        int barX0 = x0 + PAD;
        int barX1 = x0 + w - 8;
        double frac = size.maxWeight() <= 0 ? 0 : weight / size.maxWeight();
        WaybillCreateGui.weightBar(g, barX0, wy + 11, barX1, frac);
        int fy = wy + 20;
        WaybillCreateGui.keycap(g, font, x0 + PAD, fy, "Esc");
        WaybillCreateGui.text(g, font, Component.translatable("ui.aerocouriers.create.cargo_overlay.close").getString(),
                x0 + PAD + WaybillCreateGui.keycapWidth(font, "Esc") + 4, fy + 2, WaybillPalette.INK2);
        return hovered;
        } finally {
            g.pose().popPose();
        }
    }
}
