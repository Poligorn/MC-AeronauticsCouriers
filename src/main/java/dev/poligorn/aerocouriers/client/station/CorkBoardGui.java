package dev.poligorn.aerocouriers.client.station;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Original pixel chrome for the cork board and village admin panel. */
public final class CorkBoardGui {
    public static final int WOOD = 0xFF6B4423;
    public static final int WOOD_DARK = 0xFF4A2E14;
    public static final int WOOD_LIGHT = 0xFF8A5A30;
    public static final int WOOD_LINE = 0xFFD4A84A;
    public static final int CORK = 0xFFC4A06A;
    public static final int CORK_DARK = 0xFFB08950;
    public static final int CORK_SPECK = 0xFFA07840;
    public static final int INK = 0xFF3A2A18;
    public static final int INK_MUTED = 0xFF7A6040;
    public static final int CREAM = 0xFFF3E6C4;
    public static final int GOLD_PAPER = 0xFFF0D48A;
    public static final int PINK_PAPER = 0xFFF2D4E4;
    public static final int LOCK_PAPER = 0xFFC8C4BC;
    public static final int TEAL = 0xFF2A9A94;
    public static final int TEAL_DARK = 0xFF1E6E6A;
    public static final int RED = 0xFFC04030;
    public static final int GOLD = 0xFFC4A020;
    public static final int HEADER = 0xFFF5E6C8;
    public static final int PANEL = 0xFF3E2814;
    public static final int PANEL_LINE = 0xFF2A1A0C;
    public static final int BAR_BG = 0xFF2A1A0C;
    public static final int BAR_FILL = 0xFF3AA8A0;

    private CorkBoardGui() {
    }

    public static void dimWorld(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xB0100804);
    }

    public static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, WOOD_DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, WOOD);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, WOOD_LIGHT);
        graphics.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, WOOD_DARK);
        graphics.fill(x + 3, y + 3, x + w - 3, y + 4, WOOD_LINE);
        graphics.fill(x + CorkBoardLayout.FRAME - 1, y + CorkBoardLayout.FRAME - 1,
                x + w - CorkBoardLayout.FRAME + 1, y + h - CorkBoardLayout.FRAME + 1, WOOD_DARK);
    }

    public static void cork(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, CORK);
        for (int py = 0; py < h; py += 3) {
            for (int px = 0; px < w; px += 4) {
                int hash = (px * 13 + py * 37 + (px ^ py)) & 7;
                if (hash == 0) {
                    graphics.fill(x + px, y + py, x + px + 1, y + py + 1, CORK_SPECK);
                } else if (hash == 1) {
                    graphics.fill(x + px + 1, y + py + 1, x + px + 2, y + py + 2, CORK_DARK);
                }
            }
        }
    }

    public static void headerBand(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, WOOD);
        graphics.fill(x, y + h - 1, x + w, y + h, WOOD_LINE);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL_LINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
    }

    public static void paper(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        graphics.fill(x, y, x + w, y + h, border);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void pin(GuiGraphics graphics, int cx, int cy) {
        graphics.fill(cx - 2, cy - 1, cx + 3, cy + 2, 0xFF8A8A8A);
        graphics.fill(cx - 1, cy - 2, cx + 2, cy + 3, 0xFF8A8A8A);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFE8E8E8);
        graphics.fill(cx, cy, cx + 1, cy + 1, 0xFF6A6A6A);
    }

    public static void waxSeal(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y, x + 7, y + 8, 0xFF8A2020);
        graphics.fill(x, y + 1, x + 8, y + 7, 0xFF8A2020);
        graphics.fill(x + 2, y + 2, x + 6, y + 6, 0xFFC04030);
        graphics.fill(x + 3, y + 3, x + 5, y + 5, 0xFFE0C040);
    }

    public static void ribbon(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 6, y + 12, 0xFFC04070);
        graphics.fill(x + 1, y + 1, x + 5, y + 5, 0xFFE070A0);
        graphics.fill(x + 2, y + 5, x + 4, y + 11, 0xFFA02858);
    }

    public static void lock(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 2, y, x + 7, y + 3, INK_MUTED);
        graphics.fill(x + 1, y + 3, x + 8, y + 9, INK);
        graphics.fill(x + 3, y + 5, x + 6, y + 8, 0xFFD8D0C0);
    }

    public static void sizeStamp(GuiGraphics graphics, Font font, int x, int y, String size, boolean large) {
        int w = large ? 16 : 13;
        int h = large ? 13 : 11;
        graphics.fill(x, y, x + w, y + h, 0xFFE8D8A8);
        graphics.fill(x, y, x + w, y + 1, 0xFFB0A070);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFFB0A070);
        graphics.drawString(font, size, x + (w - font.width(size)) / 2, y + (h - 8) / 2, INK, false);
    }

    public static void badge(GuiGraphics graphics, Font font, int x, int y, Component text, int bg, int fg) {
        int w = font.width(text) + 8;
        graphics.fill(x, y, x + w, y + 11, bg);
        graphics.drawString(font, text, x + 4, y + 2, fg, false);
    }

    public static void progressStamp(GuiGraphics graphics, Font font, int x, int y, int w, Component title,
                                     Component nick) {
        int h = 22;
        graphics.fill(x, y, x + w, y + h, TEAL_DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE8F4F2);
        graphics.drawString(font, title, x + 4, y + 2, TEAL_DARK, false);
        graphics.drawString(font, nick, x + 4, y + 11, TEAL, false);
    }

    public static void reputationBar(GuiGraphics graphics, int x, int y, int w, int h, int value, int max) {
        graphics.fill(x, y, x + w, y + h, BAR_BG);
        int fill = max <= 0 ? 0 : Mth.clamp(w * value / max, 0, w);
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + h, BAR_FILL);
        }
        mark(graphics, x, w, y, h, 10, max);
        mark(graphics, x, w, y, h, 25, max);
        mark(graphics, x, w, y, h, 50, max);
    }

    private static void mark(GuiGraphics graphics, int x, int w, int y, int h, int at, int max) {
        if (at > max) {
            return;
        }
        int px = x + w * at / max;
        graphics.fill(px, y - 1, px + 1, y + h + 1, 0x66FFFFFF);
    }

    public static void scrollBar(GuiGraphics graphics, int x, int y, int w, int h, int scroll, int maxScroll) {
        graphics.fill(x, y, x + w, y + h, 0x662A1A0C);
        if (maxScroll <= 0) {
            return;
        }
        int thumbH = Math.max(16, h / (maxScroll + 1));
        int thumbY = y + (h - thumbH) * scroll / maxScroll;
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, WOOD_LIGHT);
        graphics.fill(x + 1, thumbY + 1, x + w - 1, thumbY + thumbH - 1, WOOD);
    }

    public static void button(GuiGraphics graphics, Font font, int x, int y, int w, int h, Component label,
                              boolean hover, boolean enabled, boolean danger) {
        int border = danger ? RED : (hover && enabled ? WOOD_LINE : WOOD_DARK);
        int fill = !enabled ? 0xFF5A4030 : danger ? 0xFF6A2820 : hover ? 0xFF8A5A30 : WOOD;
        int text = !enabled ? 0xFF8A7A60 : HEADER;
        graphics.fill(x, y, x + w, y + h, border);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
    }

    public static void chip(GuiGraphics graphics, Font font, int x, int y, int w, int h, Component label,
                            boolean on, boolean hover) {
        int fill = on ? 0xFF8A5A30 : hover ? 0xFF5A3A20 : 0xFF3E2814;
        graphics.fill(x, y, x + w, y + h, WOOD_LINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        graphics.drawString(font, label, x + 5, y + (h - 8) / 2, HEADER, false);
    }

    public static void checkbox(GuiGraphics graphics, Font font, int x, int y, Component label, boolean on) {
        graphics.fill(x, y, x + 9, y + 9, WOOD_LINE);
        graphics.fill(x + 1, y + 1, x + 8, y + 8, BAR_BG);
        if (on) {
            graphics.fill(x + 2, y + 2, x + 7, y + 7, BAR_FILL);
        }
        graphics.drawString(font, label, x + 12, y, HEADER, false);
    }

    public static int chipW(Font font, Component label) {
        return font.width(label) + 12;
    }

    public static String ellipsize(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "…") > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
