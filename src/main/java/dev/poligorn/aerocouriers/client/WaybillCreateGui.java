package dev.poligorn.aerocouriers.client;

import com.mojang.math.Axis;
import dev.poligorn.aerocouriers.client.board.PixelGlyphs;
import java.util.Random;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Pressed-cardboard clipboard + paper sheet (no brass). Drawn in GUI pixels. */
public final class WaybillCreateGui {
    public enum ButtonKind {
        PRIMARY, CANCEL, PICK, DISABLED
    }

    private WaybillCreateGui() {
    }

    public static void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x1 < x0 || y1 < y0) {
            return;
        }
        g.fill(x0, y0, x1 + 1, y1 + 1, color);
    }

    public static void hline(GuiGraphics g, int x0, int x1, int y, int color) {
        fill(g, x0, y, x1, y, color);
    }

    public static void vline(GuiGraphics g, int x, int y0, int y1, int color) {
        fill(g, x, y0, x, y1, color);
    }

    public static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int border) {
        fill(g, x0, y0, x1, y1, fill);
        outline(g, x0, y0, x1, y1, border);
    }

    public static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        hline(g, x0, x1, y0, color);
        hline(g, x0, x1, y1, color);
        vline(g, x0, y0, y1, color);
        vline(g, x1, y0, y1, color);
    }

    public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
        MoneyText.draw(g, font, s, x, y, color, MoneyText.Face.PAPER);
    }

    public static void text(GuiGraphics g, Font font, Component s, int x, int y, int color) {
        text(g, font, s.getString(), x, y, color);
    }

    public static void bold(GuiGraphics g, Font font, String s, int x, int y, int color) {
        MoneyText.draw(g, font, s, x, y, color, MoneyText.Face.PAPER_BOLD);
    }

    /** Extra gap between letters. Used only for the waybill title and the published stamp. */
    public static void tracked(GuiGraphics g, Font font, String s, int x, int y, int color, int gap) {
        int cursor = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            text(g, font, ch, cursor, y, color);
            cursor += font.width(ch) + gap;
        }
    }

    public static int trackedWidth(Font font, String s, int gap) {
        if (s.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            width += font.width(s.substring(i, i + 1));
            if (i + 1 < s.length()) {
                width += gap;
            }
        }
        return width;
    }

    public static int textRight(GuiGraphics g, Font font, String s, int right, int y, int color, boolean bold) {
        int w = MoneyText.width(font, s) + (bold ? 1 : 0);
        int x = right - w;
        if (bold) {
            bold(g, font, s, x, y, color);
        } else {
            text(g, font, s, x, y, color);
        }
        return x;
    }

    public static void drawBoard(GuiGraphics g, WaybillCreateLayout l) {
        int x0 = l.boardX0;
        int y0 = l.boardY0;
        int x1 = l.boardX1;
        int y1 = l.boardY1;
        if (GuiOverride.blit(g, GuiOverride.WAYBILL_BOARD, x0, y0, x1 - x0 + 1, y1 - y0 + 1)) {
            return;
        }
        fill(g, x0 + 4, y0 + 5, x1 + 4, y1 + 5, WaybillPalette.SHADOW);
        fill(g, x0, y0, x1, y1, WaybillPalette.BOARD_EDGE);
        fill(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, WaybillPalette.BOARD_FACE);
        hline(g, x0 + 3, x1 - 3, y0 + 3, WaybillPalette.BOARD_HI);
        vline(g, x0 + 3, y0 + 3, y1 - 3, WaybillPalette.BOARD_HI2);
        hline(g, x0 + 3, x1 - 3, y1 - 3, WaybillPalette.BOARD_SH);
        vline(g, x1 - 3, y0 + 3, y1 - 3, WaybillPalette.BOARD_SH);
        hline(g, x0 + 1, x1 - 1, y0 + 1, WaybillPalette.BOARD_SH);
        vline(g, x0 + 1, y0 + 1, y1 - 1, 0xFF503018);
        Random rnd = new Random(11);
        int gx0 = x0 + 5;
        int gx1 = x1 - 8;
        int gy0 = y0 + 5;
        int gy1 = y1 - 5;
        if (gx1 > gx0 && gy1 > gy0) {
            for (int i = 0; i < 180; i++) {
                int x = gx0 + rnd.nextInt(Math.max(1, gx1 - gx0 - 26));
                int y = gy0 + rnd.nextInt(Math.max(1, gy1 - gy0));
                int n = 6 + rnd.nextInt(21);
                fill(g, x, y, Math.min(x + n, gx1), y, rnd.nextBoolean() ? WaybillPalette.BOARD_GRAIN_A
                        : WaybillPalette.BOARD_GRAIN_B);
            }
            for (int i = 0; i < 24; i++) {
                int x = gx0 + rnd.nextInt(Math.max(1, gx1 - gx0));
                int y = gy0 + rnd.nextInt(Math.max(1, gy1 - gy0));
                fill(g, x, y, x + 1, y + 1, WaybillPalette.BOARD_SH);
            }
        }
        punchCorner(g, x0, y0, 1, 1);
        punchCorner(g, x1, y0, -1, 1);
        punchCorner(g, x0, y1, 1, -1);
        punchCorner(g, x1, y1, -1, -1);
    }

    private static void punchCorner(GuiGraphics g, int cx, int cy, int sx, int sy) {
        int[][] clear = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {0, 2}, {1, 1}};
        for (int[] d : clear) {
            int x = cx + sx * d[0];
            int y = cy + sy * d[1];
            g.fill(x, y, x + 1, y + 1, 0);
        }
        int[][] dark = {{3, 0}, {0, 3}, {2, 1}, {1, 2}};
        for (int[] d : dark) {
            int x = cx + sx * d[0];
            int y = cy + sy * d[1];
            g.fill(x, y, x + 1, y + 1, WaybillPalette.BOARD_CORNER);
        }
    }

    public static void drawPaper(GuiGraphics g, WaybillCreateLayout l, int shakeY) {
        int x0 = l.paperX0;
        int y0 = l.paperY0 + shakeY;
        int x1 = l.paperX1;
        int y1 = l.paperY1 + shakeY;
        if (GuiOverride.blit(g, GuiOverride.WAYBILL_PAPER, x0, y0 - 2, x1 - x0 + 1, y1 - y0 + 3)) {
            return;
        }
        fill(g, x0 + 2, y0 + 3, x1 + 3, y1 + 3, 0x78301006);
        fill(g, x0, y0, x1, y1, WaybillPalette.PAPER);
        Random rnd = new Random(5);
        for (int i = 0; i < 120; i++) {
            int x = x0 + 2 + rnd.nextInt(Math.max(1, x1 - x0 - 3));
            int y = y0 + 2 + rnd.nextInt(Math.max(1, y1 - y0 - 3));
            g.fill(x, y, x + 1, y + 1, WaybillPalette.PAPER_SPECK);
        }
        vline(g, x0, y0, y1, WaybillPalette.PAPER3);
        hline(g, x0, x1, y1, 0xFFC4AC7C);
        vline(g, x1, y0, y1, 0xFFC4AC7C);
        int cx = l.clipCx;
        for (int x = x0; x <= x1; x++) {
            int k = Math.abs(x - cx);
            int h = k < 44 ? 2 : k < 60 ? 1 : ((x / 7) % 5 == 0 ? 1 : 0);
            for (int j = 0; j < h; j++) {
                g.fill(x, y0 - 1 - j, x + 1, y0 - j, j == 0 ? WaybillPalette.PAPER : WaybillPalette.PAPER3);
            }
            g.fill(x, y0, x + 1, y0 + 1, (x / 9) % 4 == 0 ? WaybillPalette.PAPER3 : WaybillPalette.PAPER);
        }
    }

    public static void drawClip(GuiGraphics g, WaybillCreateLayout l) {
        int cx = l.clipCx;
        int by0 = l.boardY0;
        int py0 = l.paperY0;
        int clipX = cx - 75;
        int clipY = by0 - 27;
        int clipW = 157;
        int clipH = (py0 + 17) - clipY;
        if (GuiOverride.blit(g, GuiOverride.WAYBILL_CLIP, clipX, clipY, clipW, clipH)) {
            return;
        }
        fill(g, cx - 62, py0 + 14, cx + 66, py0 + 17, 0x5F281406);
        fill(g, cx - 70, by0 + 9, cx + 82, by0 + 15, 0x3C000000);
        fill(g, cx - 62, by0 + 6, cx + 62, py0 + 12, WaybillPalette.STEEL_OUT);
        fill(g, cx - 61, by0 + 7, cx + 61, py0 + 11, WaybillPalette.STEEL_M3);
        hline(g, cx - 61, cx + 61, by0 + 7, WaybillPalette.STEEL_M1);
        hline(g, cx - 61, cx + 61, py0 + 11, WaybillPalette.STEEL_M4);
        hline(g, cx - 62, cx + 62, py0 + 12, WaybillPalette.STEEL_OUT);
        int plateY0 = by0 - 18;
        int plateY1 = by0 + 9;
        fill(g, cx - 75, plateY0, cx + 75, plateY1, WaybillPalette.STEEL_OUT);
        fill(g, cx - 74, plateY0 + 1, cx + 74, plateY1 - 1, WaybillPalette.STEEL_M2);
        hline(g, cx - 74, cx + 74, plateY0 + 1, WaybillPalette.STEEL_HL);
        hline(g, cx - 74, cx + 74, plateY0 + 2, WaybillPalette.STEEL_HL);
        hline(g, cx - 74, cx + 74, plateY0 + 3, WaybillPalette.STEEL_M1);
        fill(g, cx - 74, plateY1 - 3, cx + 74, plateY1 - 1, WaybillPalette.STEEL_M3);
        hline(g, cx - 74, cx + 74, plateY1 - 1, WaybillPalette.STEEL_M4);
        int ly0 = plateY0 - 9;
        fill(g, cx - 55, ly0, cx + 55, plateY0 + 2, WaybillPalette.STEEL_OUT);
        fill(g, cx - 54, ly0 + 1, cx + 54, plateY0 + 1, WaybillPalette.STEEL_M2);
        hline(g, cx - 54, cx + 54, ly0 + 1, WaybillPalette.STEEL_HL);
        hline(g, cx - 54, cx + 54, ly0 + 2, WaybillPalette.STEEL_M1);
        hline(g, cx - 54, cx + 54, ly0 + 6, WaybillPalette.STEEL_M3);
        hline(g, cx - 54, cx + 54, ly0 + 7, WaybillPalette.STEEL_M4);
        fill(g, cx - 54, ly0 + 3, cx - 50, ly0 + 7, WaybillPalette.STEEL_M3);
        fill(g, cx + 50, ly0 + 3, cx + 54, ly0 + 7, WaybillPalette.STEEL_M3);
        for (int x = cx - 45; x <= cx + 45; x += 6) {
            vline(g, x, ly0 + 3, ly0 + 5, WaybillPalette.STEEL_M3);
        }
        fill(g, cx - 32 - 2, plateY0 + 7, cx - 32 + 2, plateY0 + 11, WaybillPalette.STEEL_M1);
        fill(g, cx + 32 - 2, plateY0 + 7, cx + 32 + 2, plateY0 + 11, WaybillPalette.STEEL_M1);
        fill(g, cx - 15, plateY0 + 7, cx + 15, plateY0 + 11, WaybillPalette.STEEL_M4);
        hline(g, cx - 15, cx + 15, plateY0 + 7, WaybillPalette.STEEL_M3);
    }

    public static void section(GuiGraphics g, Font font, int x0, int x1, int y, int num, String label) {
        fill(g, x0, y, x0 + 8, y + 8, WaybillPalette.BROWN);
        text(g, font, String.valueOf(num), x0 + 2, y, WaybillPalette.PAPER);
        text(g, font, label, x0 + 13, y, WaybillPalette.INK);
        int xe = x0 + 13 + font.width(label) + 4;
        if (xe < x1) {
            hline(g, xe, x1, y + 4, WaybillPalette.RULE);
        }
    }

    public static void field(GuiGraphics g, int x0, int y0, int x1, boolean bad) {
        int h = WaybillCreateLayout.FIELD_H;
        box(g, x0, y0, x1, y0 + h - 1, bad ? WaybillPalette.FIELD_BAD : WaybillPalette.FIELD, bad
                ? WaybillPalette.RED : WaybillPalette.FIELD_BD);
        hline(g, x0 + 1, x1 - 1, y0 + 1, bad ? WaybillPalette.FIELD_BAD_HI : WaybillPalette.FIELD_HI);
    }

    public static void chip(GuiGraphics g, Font font, int x, int y, int w, String label, boolean sel, boolean hover) {
        int h = WaybillCreateLayout.CHIP_H;
        if (sel) {
            box(g, x, y, x + w - 1, y + h - 1, WaybillPalette.BROWN, WaybillPalette.INK);
            text(g, font, label, x + (w - font.width(label)) / 2, y + 2, WaybillPalette.PAPER);
        } else {
            int fill = hover ? 0xFFE8D8B0 : WaybillPalette.PAPER2;
            box(g, x, y, x + w - 1, y + h - 1, fill, WaybillPalette.FIELD_BD);
            text(g, font, label, x + (w - font.width(label)) / 2, y + 2, WaybillPalette.INK);
        }
    }

    public static void helpIcon(GuiGraphics g, Font font, int x, int y) {
        box(g, x, y, x + 8, y + 8, WaybillPalette.PAPER2, WaybillPalette.INK2);
        text(g, font, "?", x + 2, y, WaybillPalette.INK2);
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, ButtonKind kind,
                             String[] icon, boolean hover) {
        int bg;
        int bd;
        int hi;
        int tx;
        boolean bold = false;
        switch (kind) {
            case PRIMARY -> {
                bg = hover ? 0xFFE8A838 : WaybillPalette.PRIMARY;
                bd = WaybillPalette.PRIMARY_BD;
                hi = WaybillPalette.PRIMARY_HI;
                tx = WaybillPalette.PRIMARY_TX;
                bold = true;
            }
            case CANCEL -> {
                bg = hover ? 0xFF96604C : WaybillPalette.CANCEL;
                bd = WaybillPalette.CANCEL_BD;
                hi = WaybillPalette.CANCEL_HI;
                tx = WaybillPalette.CANCEL_TX;
            }
            case PICK -> {
                bg = hover ? 0xFFE8F4FC : WaybillPalette.PICK_BG;
                bd = WaybillPalette.PICK_BD;
                hi = WaybillPalette.PICK_HI;
                tx = WaybillPalette.BLUE;
            }
            default -> {
                bg = WaybillPalette.DIS_BG;
                bd = WaybillPalette.DIS_BD;
                hi = WaybillPalette.PAPER2;
                tx = WaybillPalette.DIS_TX;
            }
        }
        box(g, x, y, x + w - 1, y + h - 1, bg, bd);
        hline(g, x + 1, x + w - 2, y + 1, hi);
        int iconW = icon == null ? 0 : PixelGlyphs.width(icon) + 4;
        String shown = MoneyText.trunc(font, label, Math.max(8, w - 8 - iconW));
        int lw = MoneyText.width(font, shown) + (bold ? 1 : 0);
        int textX = x + (w - lw - iconW) / 2;
        int textY = y + (h - 8) / 2;
        if (icon != null) {
            PixelGlyphs.draw(g, textX, textY + 1, icon, tx);
            textX += iconW;
        }
        if (bold) {
            bold(g, font, shown, textX, textY, tx);
        } else {
            text(g, font, shown, textX, textY, tx);
        }
    }

    public static void keycap(GuiGraphics g, Font font, int x, int y, String s) {
        int w = font.width(s) + 8;
        box(g, x, y, x + w - 1, y + 11, WaybillPalette.PAPER2, WaybillPalette.INK2);
        hline(g, x + 1, x + w - 2, y + 11, WaybillPalette.PAPER3);
        text(g, font, s, x + 4, y + 2, WaybillPalette.INK2);
    }

    public static int keycapWidth(Font font, String s) {
        return font.width(s) + 8;
    }

    public static void slot(GuiGraphics g, int x, int y) {
        box(g, x, y, x + 17, y + 17, WaybillPalette.SLOT_BG, WaybillPalette.SLOT_BD);
        hline(g, x + 1, x + 16, y + 1, WaybillPalette.SLOT_SH);
        vline(g, x + 1, y + 1, y + 16, WaybillPalette.SLOT_SH);
    }

    /** Blue "+N" on the 9th cell. Sits on the top of the slot; the stack count stays at the bottom. */
    public static void hiddenStacksBadge(GuiGraphics g, Font font, int slotX, int slotY, int hidden) {
        String label = "+" + hidden;
        Component ink = WaybillFonts.ink(label);
        int bw = font.width(ink) + 5;
        int bx = slotX + WaybillCreateLayout.SLOT - bw + 1;
        int by = slotY - 3;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        if (!GuiOverride.blit(g, GuiOverride.HIDDEN_STACKS_BADGE, bx, by, bw, 10)) {
            box(g, bx, by, bx + bw - 1, by + 9, WaybillPalette.BLUE, 0xFF0E2456);
        }
        g.drawString(font, ink, bx + 3, by + 1, 0xFFF4F8FF, false);
        g.pose().popPose();
    }

    public static void stackCount(GuiGraphics g, Font font, ItemStack stack, int sx, int sy) {
        if (stack.getCount() <= 1) {
            return;
        }
        String count = String.valueOf(stack.getCount());
        int x = sx + 16 - font.width(count);
        int y = sy + 8;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F);
        g.drawString(font, count, x + 1, y + 1, 0xFF000000, false);
        g.drawString(font, count, x, y, WaybillPalette.COUNT, false);
        g.pose().popPose();
    }

    public static void spur(GuiGraphics g, int x, int y) {
        if (!SpurCoinGraphics.useNumismaticsItems()) {
            SpurCoinGraphics.drawGenericCoin(g, x, y, 8);
            return;
        }
        if (GuiOverride.blit(g, GuiOverride.WAYBILL_SPUR, x, y, 8, 7)) {
            return;
        }
        int o = 0xFF784018;
        int m = 0xFFC47434;
        int h = 0xFFECB05A;
        String[] rows = {
                "..###..",
                ".#hmmm#.",
                "#hmmmmm#",
                "#mmmmmm#",
                "#mmmmmm#",
                ".#mmmm#.",
                "..###.."
        };
        for (int j = 0; j < rows.length; j++) {
            String row = rows[j];
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                int col = switch (c) {
                    case '#' -> o;
                    case 'm' -> m;
                    case 'h' -> h;
                    default -> 0;
                };
                if (col != 0) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, col);
                }
            }
        }
        g.fill(x + 3, y + 3, x + 4, y + 4, o);
    }

    public static void dashArrow(GuiGraphics g, int x0, int x1, int y) {
        for (int x = x0; x < x1; x += 3) {
            g.fill(x, y, Math.min(x + 2, x1), y + 1, WaybillPalette.INK2);
        }
        PixelGlyphs.draw(g, x1, y - 2, PixelGlyphs.ARROW, WaybillPalette.INK2);
    }

    public static void tooltip(GuiGraphics g, Font font, int x, int y, String[] lines) {
        int w = 10;
        for (String line : lines) {
            w = Math.max(w, MoneyText.width(font, line) + 10);
        }
        int h = lines.length * 10 + 6;
        box(g, x, y, x + w - 1, y + h - 1, WaybillPalette.TOOLTIP_BG, WaybillPalette.TOOLTIP_BD);
        outline(g, x + 1, y + 1, x + w - 2, y + h - 2, WaybillPalette.TOOLTIP_IN);
        for (int i = 0; i < lines.length; i++) {
            text(g, font, lines[i], x + 5, y + 3 + i * 10, WaybillPalette.TOOLTIP_TX);
        }
    }

    public static void weightBar(GuiGraphics g, int x0, int y, int x1, double fraction) {
        box(g, x0, y, x1, y + 6, WaybillPalette.PAPER3, WaybillPalette.INK2);
        int inner = x1 - x0 - 1;
        int fw = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * inner);
        if (fw > 0) {
            fill(g, x0 + 1, y + 1, x0 + fw, y + 5, WaybillPalette.TEAL);
            hline(g, x0 + 1, x0 + fw, y + 1, WaybillPalette.TEAL_HI);
        }
    }

    public static void metaRow(GuiGraphics g, Font font, int x0, int x1, int y, String label, String value,
                               String[] icon, int iconColor) {
        text(g, font, label, x0, y, WaybillPalette.INK2);
        int iconW = icon == null ? 0 : PixelGlyphs.width(icon) + 4;
        int valueRoom = Math.max(8, x1 - x0 - font.width(label) - 6 - iconW);
        String shown = MoneyText.trunc(font, value, valueRoom);
        int vw = MoneyText.width(font, shown);
        int vx = x1 - vw;
        if (icon != null) {
            PixelGlyphs.draw(g, vx - PixelGlyphs.width(icon) - 4, y, icon, iconColor);
        }
        text(g, font, shown, vx, y, WaybillPalette.INK);
    }

    /** Short «no collateral» plate: width follows the label, unlike the full-width risk banner. */
    public static void peacePlate(GuiGraphics g, Font font, int x, int y, String line) {
        int w = font.width(line) + 12;
        box(g, x, y, x + w, y + 13, WaybillPalette.PAPER2, WaybillPalette.RULE);
        text(g, font, line, x + 6, y + 3, WaybillPalette.INK);
    }

    public static void riskBanner(GuiGraphics g, Font font, int x0, int y, int x1, String line) {
        box(g, x0, y, x1, y + 13, WaybillPalette.RED_BG, WaybillPalette.RED);
        PixelGlyphs.draw(g, x0 + 5, y + 4, PixelGlyphs.WARN, WaybillPalette.RED);
        text(g, font, line, x0 + 16, y + 3, WaybillPalette.INK);
    }

    public static void stampBurst(GuiGraphics g, int cx, int cy, float scale) {
        java.util.Random rnd = new java.util.Random(2);
        int w = (int) (100 * scale) / 2 + 6;
        int h = (int) (20 * scale) / 2 + 6;
        for (int k = 0; k < 26; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            int x = cx + (int) (Math.cos(ang) * (w + rnd.nextInt(13)));
            int y = cy + (int) (Math.sin(ang) * (h + rnd.nextInt(11)));
            int col = k % 2 == 0 ? 0xFFFAE2AA : WaybillPalette.STAMP;
            g.fill(x, y, x + 2, y + 2, col);
        }
    }

    public static void stamp(GuiGraphics g, Font font, int cx, int cy, float scale, float rotDeg, float alpha) {
        String label = Component.translatable("ui.aerocouriers.create.stamped").getString();
        int w = 100;
        int h = 20;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 400.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-w / 2.0F, -h / 2.0F, 0.0F);
        if (GuiOverride.blit(g, GuiOverride.WAYBILL_STAMP, 0, 0, w, h)) {
            g.pose().popPose();
            return;
        }
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24;
        int ink = a | (WaybillPalette.STAMP & 0x00FFFFFF);
        outline(g, 0, 0, w - 1, h - 1, ink);
        outline(g, 2, 2, w - 3, h - 3, ink);
        int tx = (w - trackedWidth(font, label, 1)) / 2;
        tracked(g, font, label, tx, 6, ink, 1);
        g.pose().popPose();
    }
}
