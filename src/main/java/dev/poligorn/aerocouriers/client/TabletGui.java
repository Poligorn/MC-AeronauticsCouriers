package dev.poligorn.aerocouriers.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Brass handheld monitor chrome for {@link CourierBoardScreen} only. */
public final class TabletGui {
    public static final int TEXT = 0xFFAF55;
    public static final int TEXT_HEADER = 0xFF7A2F;
    public static final int TEXT_BRIGHT = 0xFFD25D;
    public static final int TEXT_MUTED = 0xC87A3A;
    public static final int TEXT_WARN = 0xFF9E00;
    public static final int TEXT_BAD = 0xFF7A2F;
    public static final int TEXT_OK = 0xFFAF55;
    public static final int TEXT_BUTTON = 0xFFAF55;
    public static final int TEXT_BUTTON_HOVER = 0xFF9E00;
    public static final int TEXT_BUTTON_DISABLED = 0x873B00;

    /** Brass border thickness (nine-slice). */
    public static final int FRAME_BORDER = 6;
    public static final int ANTENNA_W = 28;
    public static final int ANTENNA_H = 22;
    /** Pixels the antenna sprite extends above the frame top. */
    public static final int ANTENNA_OVERHANG_TOP = 16;
    public static final int GEAR_W = 22;
    public static final int GEAR_H = 36;
    /** Pixels the gear sprite extends left of the frame. */
    public static final int GEAR_OVERHANG_LEFT = 12;

    private TabletGui() {
    }

    public static void drawMonitor(GuiGraphics graphics, int frameX, int frameY, int frameW, int frameH) {
        int gearX = frameX - GEAR_OVERHANG_LEFT;
        int gearY = frameY + (frameH - GEAR_H) / 2;
        graphics.blitSprite(AerocouriersGuiSprites.TABLET_DECOR_GEAR, gearX, gearY, GEAR_W, GEAR_H);
        if (frameW > 0 && frameH > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.TABLET_FRAME, frameX, frameY, frameW, frameH);
        }
        int sx = frameX + FRAME_BORDER;
        int sy = frameY + FRAME_BORDER;
        int sw = frameW - FRAME_BORDER * 2;
        int sh = frameH - FRAME_BORDER * 2;
        if (sw > 0 && sh > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.TABLET_GRID, sx, sy, sw, sh);
            graphics.fill(sx, sy, sx + sw, sy + 2, 0x33FF7A2F);
            graphics.fill(sx, sy + sh - 2, sx + sw, sy + sh, 0x33FF7A2F);
            graphics.fill(sx, sy, sx + 2, sy + sh, 0x33FF7A2F);
            graphics.fill(sx + sw - 2, sy, sx + sw, sy + sh, 0x33FF7A2F);
            for (int y = sy + 3; y < sy + sh; y += 4) {
                graphics.fill(sx, y, sx + sw, y + 1, 0x18000000);
            }
        }
        int ax = frameX + 4;
        int ay = frameY - ANTENNA_OVERHANG_TOP;
        graphics.blitSprite(AerocouriersGuiSprites.TABLET_DECOR_ANTENNA, ax, ay, ANTENNA_W, ANTENNA_H);
    }

    public static void drawListPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, 0xCC2C0900);
        graphics.renderOutline(x, y, w, h, 0xFFB36833);
    }

    public static void drawRow(GuiGraphics graphics, int x, int y, int w, int h, boolean selected) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.blitSprite(AerocouriersGuiSprites.TABLET_LIST_ROW, x, y, w, h);
        if (selected) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x30FFAF55);
            graphics.renderOutline(x, y, w, h, 0xFFFFAF55);
        }
    }

    public static void drawScrollbar(GuiGraphics graphics, int x, int y, int h, float progress, float thumb) {
        int barH = Math.max(12, h);
        graphics.blitSprite(AerocouriersGuiSprites.TABLET_SCROLLER_TRACK, x, y, 6, barH);
        int thumbH = Math.max(9, Math.round(barH * thumb));
        int thumbY = y + Math.round((barH - thumbH) * progress);
        graphics.blitSprite(AerocouriersGuiSprites.TABLET_SCROLLER, x, thumbY, 6, thumbH);
    }

    public static void drawSpeechTooltip(GuiGraphics graphics, Font font, Component text, int mouseX, int mouseY,
                                        int maxWidth) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        if (lines.isEmpty()) {
            return;
        }
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int pad = 6;
        int boxW = textW + pad * 2;
        int boxH = lines.size() * 10 + pad * 2;
        int x = mouseX + 8;
        int y = mouseY - 12;
        graphics.fill(x, y, x + boxW, y + boxH, 0xF02C0900);
        graphics.renderOutline(x, y, boxW, boxH, 0xFFB36833);
        int drawY = y + pad;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + pad, drawY, TEXT, false);
            drawY += 10;
        }
    }

    /** Frame top-left when centering the device (frame + decor) on the screen. */
    public static int frameLeft(int screenW, int frameW) {
        int totalW = frameW + GEAR_OVERHANG_LEFT;
        return Math.max(GEAR_OVERHANG_LEFT + 2, (screenW - totalW) / 2 + GEAR_OVERHANG_LEFT);
    }

    public static int frameTop(int screenH, int frameH) {
        int totalH = frameH + ANTENNA_OVERHANG_TOP;
        return Math.max(ANTENNA_OVERHANG_TOP + 2, (screenH - totalH) / 2 + ANTENNA_OVERHANG_TOP);
    }
}
