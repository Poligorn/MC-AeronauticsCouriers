package dev.poligorn.aerocouriers.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Drawing helpers aligned with mockup build.py (slot, bevel, bold, clip). */
public final class TabletMockupUi {
    public static final int SLOT = 18;

    private TabletMockupUi() {
    }

    public static void drawBevelPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TabletMockupPalette.OUT);
        g.fill(x, y, x + w, y + h, TabletMockupPalette.PANEL_BG);
        bevel(g, x, y, x + w - 1, y + h - 1, rgb(190, 140, 60), rgb(100, 64, 24));
    }

    public static void drawSlot(GuiGraphics g, Font font, int x, int y, ItemStack stack) {
        g.fill(x, y, x + SLOT, y + SLOT, TabletMockupPalette.SLOT_FILL);
        g.renderOutline(x, y, SLOT, SLOT, TabletMockupPalette.SLOT_EDGE);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + 2, 0xFF0A0502);
        g.fill(x + 1, y + 1, x + 2, y + SLOT - 1, 0xFF0A0502);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }

    public static void drawBold(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y, color, false);
        g.drawString(font, text, x, y, color, false);
    }

    public static void drawRight(GuiGraphics g, Font font, String text, int rightX, int y, int color, boolean bold) {
        int x = rightX - font.width(text) - (bold ? 1 : 0);
        if (bold) {
            drawBold(g, font, text, x, y, color);
        } else {
            g.drawString(font, text, x, y, color, false);
        }
    }

    public static String clip(Font font, String raw, int maxWidth) {
        if (font.width(raw) <= maxWidth) {
            return raw;
        }
        String ell = "…";
        String s = font.plainSubstrByWidth(raw, Math.max(4, maxWidth - font.width(ell)));
        return s.stripTrailing() + ell;
    }

    public static void sep(GuiGraphics g, int x0, int x1, int y) {
        g.hLine(x0, x1, y, TabletMockupPalette.SEP_DARK);
    }

    public enum Btn3Kind {
        PRIMARY,
        MAP,
        BACK
    }

    /** Mockup btn3(): height 20, label centered. */
    public static void drawBtn3(GuiGraphics g, Font font, int x, int y, int w, String label, Btn3Kind kind) {
        int h = 20;
        int fill;
        int outline;
        int textColor;
        switch (kind) {
            case PRIMARY -> {
                fill = rgb(44, 110, 100);
                outline = TabletMockupPalette.TEAL;
                textColor = rgb(14, 30, 26);
            }
            case MAP -> {
                fill = rgb(26, 44, 60);
                outline = TabletMockupPalette.BLUE;
                textColor = TabletMockupPalette.BLUE;
            }
            default -> {
                fill = rgb(36, 20, 10);
                outline = TabletMockupPalette.BRASS;
                textColor = TabletMockupPalette.SEC;
            }
        }
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, outline);
        g.hLine(x + 1, x + w - 2, y + 1, kind == Btn3Kind.PRIMARY ? TabletMockupPalette.TEAL : outline);
        int tx = x + Math.max(4, (w - font.width(label) - (kind == Btn3Kind.PRIMARY ? 1 : 0)) / 2);
        int ty = y + 6;
        if (kind == Btn3Kind.PRIMARY) {
            drawBold(g, font, label, tx, ty, textColor);
        } else {
            g.drawString(font, label, tx, ty, textColor, false);
        }
    }

    private static void bevel(GuiGraphics g, int x0, int y0, int x1, int y1, int tl, int br) {
        g.hLine(x0, x1, y0, tl);
        g.vLine(x0, y0, y1, tl);
        g.hLine(x0, x1, y1, br);
        g.vLine(x1, y0, y1, br);
    }

    private static int rgb(int r, int g, int b) {
        return TabletMockupPalette.rgb(r, g, b);
    }
}
