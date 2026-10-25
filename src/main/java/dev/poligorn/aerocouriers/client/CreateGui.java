package dev.poligorn.aerocouriers.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Adaptive Create-styled chrome: 9-slice / tiled pieces from {@link CreateGuiTextures}.
 * Create's own {@code AllGuiTextures#render} and {@code IconButton} are fixed 18×18 / atlas-sized,
 * so large windows are assembled from the same files Create uses.
 */
public final class CreateGui {
    public static final int TEXT = CreateGuiTextures.FONT_COLOR;
    public static final int TEXT_MUTED = 0x8A91A3;
    public static final int TEXT_TITLE = 0x3F4558;
    public static final int TEXT_BRASS = 0xB98D3A;
    public static final int TEXT_OK = 0x4F7A3C;
    public static final int TEXT_WARN = 0xA56A28;
    public static final int TEXT_BAD = 0xA04A3A;
    public static final int TEXT_DISABLED = 0x9AA0AE;

    private CreateGui() {
    }

    /** Brass 9-slice from {@code value_settings.png} — same pieces as Create value panels; center is tiled fill. */
    public static void drawWindow(GuiGraphics graphics, int x, int y, int w, int h) {
        int inset = 4;
        if (w > inset * 2 && h > inset * 2) {
            tile(graphics, CreateGuiTextures.PARCHMENT_FILL, x + inset, y + inset, w - inset * 2, h - inset * 2);
        }
        drawBrassFrame(graphics, x, y, w, h);
    }

    /** Readable band for title / balance over the brass fill (tiled parchment). */
    public static void drawParchmentStrip(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        tile(graphics, CreateGuiTextures.PARCHMENT_FILL, x, y, w, h);
    }

    public static void drawBrassFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        CreateGuiTextures.BRASS_TL.blit(graphics, x, y);
        CreateGuiTextures.BRASS_TR.blit(graphics, x + w - CreateGuiTextures.BRASS_TR.width, y);
        CreateGuiTextures.BRASS_BL.blit(graphics, x, y + h - CreateGuiTextures.BRASS_BL.height);
        CreateGuiTextures.BRASS_BR.blit(graphics, x + w - CreateGuiTextures.BRASS_BR.width,
                y + h - CreateGuiTextures.BRASS_BR.height);
        tile(graphics, CreateGuiTextures.BRASS_TOP,
                x + 4, y, Math.max(0, w - 8), CreateGuiTextures.BRASS_TOP.height);
        tile(graphics, CreateGuiTextures.BRASS_BOTTOM,
                x + 4, y + h - CreateGuiTextures.BRASS_BOTTOM.height,
                Math.max(0, w - 8), CreateGuiTextures.BRASS_BOTTOM.height);
        tile(graphics, CreateGuiTextures.BRASS_LEFT,
                x, y + 4, CreateGuiTextures.BRASS_LEFT.width, Math.max(0, h - 8));
        tile(graphics, CreateGuiTextures.BRASS_RIGHT,
                x + w - CreateGuiTextures.BRASS_RIGHT.width, y + 4,
                CreateGuiTextures.BRASS_RIGHT.width, Math.max(0, h - 8));
    }

    public static void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                                 boolean enabled, boolean hovered, boolean pressed, boolean green) {
        CreateGuiTextures slice = !enabled ? CreateGuiTextures.BUTTON_DISABLED
                : pressed ? CreateGuiTextures.BUTTON_DOWN
                : hovered ? CreateGuiTextures.BUTTON_HOVER
                : green ? CreateGuiTextures.BUTTON_GREEN
                : CreateGuiTextures.BUTTON;
        nineSlice(graphics, slice, x, y, w, h, 4, 4, 4, 4);
    }

    public static void drawField(GuiGraphics graphics, int x, int y, int w, int h) {
        int topH = Math.min(CreateGuiTextures.TEXTBOX_TOP.height, Math.max(8, h - 4));
        int botH = Math.min(CreateGuiTextures.TEXTBOX_BOTTOM.height, Math.max(2, h - topH));
        int midH = h - topH - botH;
        blitHorizontal(graphics, CreateGuiTextures.TEXTBOX_TOP, x, y, w, topH, 8,
                CreateGuiTextures.TEXTBOX_TOP.v);
        if (midH > 0) {
            blitHorizontal(graphics, CreateGuiTextures.TEXTBOX_MIDDLE, x, y + topH, w, midH, 8,
                    CreateGuiTextures.TEXTBOX_MIDDLE.v);
        }
        blitHorizontal(graphics, CreateGuiTextures.TEXTBOX_BOTTOM, x, y + h - botH, w, botH, 8,
                CreateGuiTextures.TEXTBOX_BOTTOM.v);
    }

    public static void drawSlot(GuiGraphics graphics, int x, int y) {
        CreateGuiTextures.STOCK_SLOT.blit(graphics, x, y);
    }

    public static void drawRow(GuiGraphics graphics, int x, int y, int w, int h, boolean selected) {
        if (selected) {
            blitHorizontal(graphics, CreateGuiTextures.STOCK_ROW, x, y, w, h, 8,
                    CreateGuiTextures.STOCK_ROW.v);
        }
    }

    public static void drawScrollbar(GuiGraphics graphics, int x, int y, int h, float progress, float thumb) {
        int barH = Math.max(12, h);
        CreateGuiTextures.STOCK_SCROLL_TOP.blit(graphics, x, y);
        tile(graphics, CreateGuiTextures.STOCK_SCROLL_PAD,
                x, y + CreateGuiTextures.STOCK_SCROLL_TOP.height,
                CreateGuiTextures.STOCK_SCROLL_PAD.width,
                Math.max(1, barH - CreateGuiTextures.STOCK_SCROLL_TOP.height
                        - CreateGuiTextures.STOCK_SCROLL_BOT.height));
        CreateGuiTextures.STOCK_SCROLL_BOT.blit(graphics, x, y + barH - CreateGuiTextures.STOCK_SCROLL_BOT.height);
        int thumbH = Math.max(CreateGuiTextures.STOCK_SCROLL_MID.height, Math.round(barH * thumb));
        int thumbY = y + Math.round((barH - thumbH) * progress);
        CreateGuiTextures.STOCK_SCROLL_MID.blit(graphics, x,
                Math.min(y + barH - CreateGuiTextures.STOCK_SCROLL_MID.height, Math.max(y, thumbY)));
    }

    public static void drawBanner(GuiGraphics graphics, int x, int y, int w) {
        CreateGuiTextures.STOCK_BANNER_L.blit(graphics, x, y);
        tile(graphics, CreateGuiTextures.STOCK_BANNER_M,
                x + CreateGuiTextures.STOCK_BANNER_L.width, y,
                Math.max(1, w - CreateGuiTextures.STOCK_BANNER_L.width - CreateGuiTextures.STOCK_BANNER_R.width),
                CreateGuiTextures.STOCK_BANNER_M.height);
        CreateGuiTextures.STOCK_BANNER_R.blit(graphics,
                x + w - CreateGuiTextures.STOCK_BANNER_R.width, y);
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
        nineSlice(graphics, CreateGuiTextures.SPEECH_BORDER, x - 1, y - 1, boxW + 2, boxH + 2, 2, 2, 2, 2);
        nineSlice(graphics, CreateGuiTextures.SPEECH_BG, x, y, boxW, boxH, 2, 2, 2, 2);
        int drawY = y + pad;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + pad, drawY, TEXT, false);
            drawY += 10;
        }
    }

    static void blit(GuiGraphics graphics, CreateGuiTextures slice, int x, int y) {
        slice.blit(graphics, x, y);
    }

    private static void blitCapStrip(GuiGraphics graphics, CreateGuiTextures strip,
                                     int x, int y, int w, int h, int cap, boolean bottomAlign) {
        int srcY = bottomAlign ? strip.v + Math.max(0, strip.height - h) : strip.v;
        int srcH = Math.min(h, strip.height);
        blitRegion(graphics, strip.location, x, y, cap, h, strip.u, srcY, cap, srcH);
        blitRegion(graphics, strip.location, x + w - cap, y, cap, h,
                strip.u + strip.width - cap, srcY, cap, srcH);
        int mid = w - cap * 2;
        if (mid > 0) {
            // 8px cream column just inside the cap — avoids the header search widget.
            tileRegion(graphics, strip.location, x + cap, y, mid, h, strip.u + cap, srcY, 8, srcH);
        }
    }

    private static void blitHorizontal(GuiGraphics graphics, CreateGuiTextures strip,
                                       int x, int y, int w, int h, int cap, int srcY) {
        int srcH = Math.min(h, strip.height);
        blitRegion(graphics, strip.location, x, y, cap, h, strip.u, srcY, cap, srcH);
        blitRegion(graphics, strip.location, x + w - cap, y, cap, h,
                strip.u + strip.width - cap, srcY, cap, srcH);
        int mid = w - cap * 2;
        if (mid > 0) {
            tileRegion(graphics, strip.location, x + cap, y, mid, h,
                    strip.u + cap, srcY, Math.max(1, strip.width - cap * 2), srcH);
        }
    }

    static void nineSlice(GuiGraphics graphics, CreateGuiTextures slice,
                          int x, int y, int w, int h, int left, int top, int right, int bottom) {
        int srcMidW = Math.max(1, slice.width - left - right);
        int srcMidH = Math.max(1, slice.height - top - bottom);
        blitRegion(graphics, slice.location, x, y, left, top, slice.u, slice.v, left, top);
        blitRegion(graphics, slice.location, x + w - right, y, right, top,
                slice.u + slice.width - right, slice.v, right, top);
        blitRegion(graphics, slice.location, x, y + h - bottom, left, bottom,
                slice.u, slice.v + slice.height - bottom, left, bottom);
        blitRegion(graphics, slice.location, x + w - right, y + h - bottom, right, bottom,
                slice.u + slice.width - right, slice.v + slice.height - bottom, right, bottom);
        if (w > left + right) {
            tileRegion(graphics, slice.location, x + left, y, w - left - right, top,
                    slice.u + left, slice.v, srcMidW, top);
            tileRegion(graphics, slice.location, x + left, y + h - bottom, w - left - right, bottom,
                    slice.u + left, slice.v + slice.height - bottom, srcMidW, bottom);
        }
        if (h > top + bottom) {
            tileRegion(graphics, slice.location, x, y + top, left, h - top - bottom,
                    slice.u, slice.v + top, left, srcMidH);
            tileRegion(graphics, slice.location, x + w - right, y + top, right, h - top - bottom,
                    slice.u + slice.width - right, slice.v + top, right, srcMidH);
        }
        if (w > left + right && h > top + bottom) {
            tileRegion(graphics, slice.location, x + left, y + top, w - left - right, h - top - bottom,
                    slice.u + left, slice.v + top, srcMidW, srcMidH);
        }
    }

    static void tile(GuiGraphics graphics, CreateGuiTextures slice, int x, int y, int w, int h) {
        tileRegion(graphics, slice.location, x, y, w, h, slice.u, slice.v, slice.width, slice.height);
    }

    static void blitRegion(GuiGraphics graphics, ResourceLocation texture,
                           int x, int y, int w, int h, int u, int v, int regionW, int regionH) {
        if (w <= 0 || h <= 0 || regionW <= 0 || regionH <= 0) {
            return;
        }
        graphics.blit(texture, x, y, w, h, (float) u, (float) v, regionW, regionH,
                CreateGuiTextures.ATLAS, CreateGuiTextures.ATLAS);
    }

    static void tileRegion(GuiGraphics graphics, ResourceLocation texture,
                           int x, int y, int w, int h, int u, int v, int tileW, int tileH) {
        if (w <= 0 || h <= 0 || tileW <= 0 || tileH <= 0) {
            return;
        }
        int endX = x + w;
        int endY = y + h;
        for (int yy = y; yy < endY; yy += tileH) {
            int dh = Math.min(tileH, endY - yy);
            for (int xx = x; xx < endX; xx += tileW) {
                int dw = Math.min(tileW, endX - xx);
                graphics.blit(texture, xx, yy, dw, dh, (float) u, (float) v, dw, dh,
                        CreateGuiTextures.ATLAS, CreateGuiTextures.ATLAS);
            }
        }
    }
}
