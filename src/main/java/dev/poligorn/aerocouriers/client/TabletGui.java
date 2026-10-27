package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Brass handheld monitor chrome for {@link CourierBoardScreen} only. */
public final class TabletGui {
    public static final int TEXT = 0xFFAF55;
    public static final int TEXT_HEADER = 0xFFAF55;
    public static final int TEXT_BRIGHT = 0xFFD25D;
    public static final int TEXT_MUTED = 0xC87A3A;
    public static final int TEXT_WARN = 0xFF9E00;
    public static final int TEXT_BAD = 0xFF7A2F;
    public static final int TEXT_OK = 0xFFAF55;
    public static final int TEXT_BUTTON = 0xFFAF55;
    public static final int TEXT_BUTTON_HOVER = 0xFF9E00;
    public static final int TEXT_BUTTON_DISABLED = 0x873B00;

    /** Dark cavity fill (#0A0200) with a calm but visible grid (#241006). */
    private static final int SCREEN_FILL = 0xFF0A0200;
    private static final int GRID_LINE = 0xFF241006;
    private static final int LIST_PANEL_FILL = 0xCC0A0200;
    private static final int ROW_FILL = 0xFF3E2614;
    private static final int TOOLTIP_BG = 0xF02C0900;
    private static final int GRID_STEP = 9;

    /** Nine-slice brass + bevel before the screen cavity (matches {@code tablet_frame}). */
    public static final int FRAME_BORDER = 16;
    /** Inset from the inner screen cavity to tabs, headings, lists, and chrome (all tablet UIs). */
    public static final int CONTENT_INSET = 9;
    /** Antenna sprite size (2× legacy; matches {@code gen_tablet_antenna}). */
    public static final int ANTENNA_W = 20;
    public static final int ANTENNA_H = 64;
    /** Brass foot height in the sprite; equals top {@link #FRAME_BORDER} bezel when drawn flush. */
    public static final int ANTENNA_BASE_H = FRAME_BORDER;

    /** Mast + bulb pixels drawn above the frame top (sprite extends upward from the bezel). */
    public static int antennaOverhangTop() {
        return ANTENNA_H - ANTENNA_BASE_H;
    }

    /** Antenna X offset from frame left (top-left on the bezel). */
    public static int antennaLeft(int frameX) {
        return frameX + FRAME_BORDER / 2;
    }

    /** Sprite Y so the foot covers {@code [frameY, frameY + ANTENNA_BASE_H)}. */
    public static int antennaDrawY(int frameY) {
        return frameY - antennaOverhangTop();
    }

    private TabletGui() {
    }

    /**
     * Draw order: screen fill inside the inner rect, brass frame on top (transparent center),
     * antenna on top-left bezel.
     */
    /** Full device: inner grid, then brass frame and decor (used by offline previews). */
    public static void drawMonitor(GuiGraphics graphics, int frameX, int frameY, int frameW, int frameH) {
        int sx = frameX + FRAME_BORDER;
        int sy = frameY + FRAME_BORDER;
        int sw = frameW - FRAME_BORDER * 2;
        int sh = frameH - FRAME_BORDER * 2;
        drawScreenBackground(graphics, sx, sy, sw, sh, 0.0F);
        drawFrameChrome(graphics, frameX, frameY, frameW, frameH);
    }

    /** Brass bezel and antenna — draw after widgets so blur/sprite state cannot drop them. */
    public static void drawFrameChrome(GuiGraphics graphics, int frameX, int frameY, int frameW, int frameH) {
        if (frameW > 0 && frameH > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.TABLET_FRAME, frameX, frameY, frameW, frameH);
        }
        graphics.blitSprite(AerocouriersGuiSprites.TABLET_DECOR_ANTENNA,
                antennaLeft(frameX), antennaDrawY(frameY), ANTENNA_W, ANTENNA_H);
    }

    public static void drawScreenBackground(GuiGraphics graphics, int x, int y, int w, int h, float partialTick) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, SCREEN_FILL);
        if (!CouriersClientConfig.tabletGridAnimation()) {
            drawGridLines(graphics, x, y, w, h, 0, 0);
            return;
        }
        int contentX = x + CONTENT_INSET;
        int contentY = y + CONTENT_INSET;
        int contentW = w - CONTENT_INSET * 2;
        int contentH = h - CONTENT_INSET * 2;
        if (contentW <= 0 || contentH <= 0) {
            drawGridLines(graphics, x, y, w, h, 0, 0);
            return;
        }
        double scrollPx = (Util.getMillis() + partialTick) * CouriersClientConfig.tabletGridSpeed() / 1000.0;
        int pixelOffset = (int) Math.floor(scrollPx);
        int shiftX = positiveMod(-pixelOffset, GRID_STEP);
        int shiftY = positiveMod(pixelOffset, GRID_STEP);
        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        drawGridLines(graphics, contentX, contentY, contentW, contentH, shiftX, shiftY);
        graphics.disableScissor();
    }

    private static void drawGridLines(GuiGraphics graphics, int x, int y, int w, int h, int shiftX, int shiftY) {
        for (int gx = x - GRID_STEP + shiftX; gx < x + w + GRID_STEP; gx += GRID_STEP) {
            if (gx >= x && gx < x + w) {
                graphics.fill(gx, y, gx + 1, y + h, GRID_LINE);
            }
        }
        for (int gy = y - GRID_STEP + shiftY; gy < y + h + GRID_STEP; gy += GRID_STEP) {
            if (gy >= y && gy < y + h) {
                graphics.fill(x, gy, x + w, gy + 1, GRID_LINE);
            }
        }
    }

    private static int positiveMod(int value, int modulus) {
        int r = value % modulus;
        return r < 0 ? r + modulus : r;
    }

    public static void drawListPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, LIST_PANEL_FILL);
        graphics.renderOutline(x, y, w, h, 0xFFB36833);
    }

    public static void drawRow(GuiGraphics graphics, int x, int y, int w, int h, boolean selected) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, ROW_FILL);
        graphics.renderOutline(x, y, w, h, 0xFFB36833);
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
        graphics.fill(x, y, x + boxW, y + boxH, TOOLTIP_BG);
        graphics.renderOutline(x, y, boxW, boxH, 0xFFB36833);
        int drawY = y + pad;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + pad, drawY, TEXT, false);
            drawY += 10;
        }
    }

    /** Frame top-left when centering the device (frame + decor) on the screen. */
    public static int frameLeft(int screenW, int frameW) {
        return Math.max(2, (screenW - frameW) / 2);
    }

    /**
     * Frame top Y; centers antenna + frame as one block so the mast is not clipped.
     * Antenna tip is at {@code frameTop - antennaOverhangTop()}.
     */
    public static int frameTop(int screenH, int frameH) {
        int over = antennaOverhangTop();
        int blockH = over + frameH;
        int blockTop = Math.max(2, (screenH - blockH) / 2);
        return blockTop + over;
    }

    /** Max frame height that fits below the antenna on this screen. */
    public static int maxFrameHeight(int screenH) {
        return Math.max(160, screenH - antennaOverhangTop() - 12);
    }

    public static int cavityLeft(int frameLeft) {
        return frameLeft + FRAME_BORDER;
    }

    public static int cavityTop(int frameTop) {
        return frameTop + FRAME_BORDER;
    }

    public static int cavityWidth(int frameWidth) {
        return frameWidth - FRAME_BORDER * 2;
    }

    public static int cavityHeight(int frameHeight) {
        return frameHeight - FRAME_BORDER * 2;
    }

    public static int contentLeft(int frameLeft) {
        return cavityLeft(frameLeft) + CONTENT_INSET;
    }

    public static int contentTop(int frameTop) {
        return cavityTop(frameTop) + CONTENT_INSET;
    }

    public static int contentWidth(int frameWidth) {
        return cavityWidth(frameWidth) - CONTENT_INSET * 2;
    }

    public static int contentHeight(int frameHeight) {
        return cavityHeight(frameHeight) - CONTENT_INSET * 2;
    }
}
