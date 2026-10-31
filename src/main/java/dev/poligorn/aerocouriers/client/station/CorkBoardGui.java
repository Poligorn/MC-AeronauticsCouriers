package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Pixel chrome: sprites at native / 9-slice size, text at layout.json cell-top (baseline = y+7). */
public final class CorkBoardGui {
    public static final int HEADER = CorkPalette.HEADER_TEXT;
    public static final int INK = CorkPalette.NOTE_INK;
    public static final int INK_MUTED = CorkPalette.NOTE_MUTED;
    public static final int GOLD = CorkPalette.GOLD;
    public static final int TEAL = CorkPalette.TEAL;
    public static final int RED = CorkPalette.ADMIN_ERROR;
    public static final int PANEL = 0xFF241309;

    private CorkBoardGui() {
    }

    public static void fullBright() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void blit(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        fullBright();
        g.blitSprite(sprite, x, y, w, h);
    }

    public static void blit(GuiGraphics g, ResourceLocation sprite, CorkBoardLayout.Rect r) {
        blit(g, sprite, r.x(), r.y(), r.w(), r.h());
    }

    public static void blitTint(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.001F) {
            a = 1.0F;
        }
        g.setColor(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, a);
        g.blitSprite(sprite, x, y, w, h);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** layout.json y is the 8 px glyph-cell top; vanilla {@code drawString} uses the same origin. */
    public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    public static void text(GuiGraphics g, Font font, Component s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    public static void bold(GuiGraphics g, Font font, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
        g.drawString(font, s, x + 1, y, color, false);
    }

    public static void bold(GuiGraphics g, Font font, Component s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
        g.drawString(font, s, x + 1, y, color, false);
    }

    public static void boardWindow(GuiGraphics g, CorkBoardLayout layout) {
        fullBright();
        // Wood stays inside the frame. The 8 px / 3 px transparent margin is the world.
        blit(g, CorkSprites.WOOD_DARK_H, layout.headerPlank);
        blit(g, CorkSprites.WOOD_MID_H, layout.filterBar);
        blit(g, CorkSprites.WOOD_DARK_H, layout.footerPlank);
        blitCork(g, layout);
        blitFrame(g, layout);
    }

    public static void adminWindow(GuiGraphics g, CorkBoardLayout layout) {
        fullBright();
        blit(g, CorkSprites.WOOD_DARK_H, layout.headerPlank);
        int bgX = layout.winX + (layout.narrow ? 11 : 18);
        int bgY = layout.winY + (layout.narrow ? 51 : 58);
        int bgW = layout.narrow ? layout.winW - 22 : 604;
        blit(g, CorkSprites.ADMIN_BG, bgX, bgY, bgW, Math.max(8, layout.footerPlank.y() - bgY));
        blit(g, CorkSprites.WOOD_DARK_H, layout.footerPlank);
        blitFrame(g, layout);
    }

    /** Cork plate at native size, or tiled when the window is not 640 / 480. */
    private static void blitCork(GuiGraphics g, CorkBoardLayout layout) {
        if (!layout.narrow && layout.cork.w() == 604) {
            if (layout.cork.h() == 243) {
                blit(g, CorkSprites.CORK_AREA, layout.cork.x(), layout.cork.y(), 604, 243);
            } else {
                g.enableScissor(layout.cork.x(), layout.cork.y(),
                        layout.cork.x() + layout.cork.w(), layout.cork.y() + layout.cork.h());
                blit(g, CorkSprites.CORK_AREA, layout.cork.x(), layout.cork.y(), 604, 243);
                g.disableScissor();
            }
            return;
        }
        if (layout.narrow && layout.cork.w() == 458) {
            if (layout.cork.h() == 180) {
                blit(g, CorkSprites.CORK_AREA_COMPACT, layout.cork.x(), layout.cork.y(), 458, 180);
            } else {
                g.enableScissor(layout.cork.x(), layout.cork.y(),
                        layout.cork.x() + layout.cork.w(), layout.cork.y() + layout.cork.h());
                blit(g, CorkSprites.CORK_AREA_COMPACT, layout.cork.x(), layout.cork.y(), 458, 180);
                g.disableScissor();
            }
            return;
        }
        blit(g, CorkSprites.CORK_TILE, layout.cork);
        blit(g, CorkSprites.CORK_VIGNETTE, layout.cork);
    }

    /**
     * Frame at sprite size (640×360 / 480×270) sitting on {@code layout.winX/Y}. Other window
     * sizes use the 9-slice sprites (insets 10 / 8). Never blit {@code frame_full_*} over the
     * whole Minecraft window.
     */
    private static void blitFrame(GuiGraphics g, CorkBoardLayout layout) {
        if (layout.exactFrame) {
            if (layout.narrow) {
                blit(g, CorkSprites.FRAME_COMPACT, layout.winX, layout.winY, 480, 270);
            } else {
                blit(g, CorkSprites.FRAME_FULL, layout.winX, layout.winY, 640, 360);
            }
            return;
        }
        blit(g, layout.narrow ? CorkSprites.FRAME_9_COMPACT : CorkSprites.FRAME_9,
                layout.winX, layout.winY, layout.winW, layout.winH);
    }

    public static void section(GuiGraphics g, Font font, int x, int y, int w, int h, Component title) {
        blit(g, CorkSprites.ADMIN_SECTION, x, y, w, h);
        bold(g, font, ellipsize(font, title.getString(), Math.max(16, w - 12)),
                x + 6, y + 3, CorkPalette.ADMIN_TITLE);
    }

    public static void dialog(GuiGraphics g, int x, int y, int w, int h) {
        blit(g, CorkSprites.DIALOG, x, y, w, h);
    }

    public static void reputationBar(GuiGraphics g, int x, int y, int w, int h, int value, int max) {
        blit(g, CorkSprites.REP_TRACK, x, y, w, h);
        int fill = max <= 0 ? 0 : Mth.clamp((w - 2) * value / max, 0, w - 2);
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, CorkPalette.TEAL);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, CorkPalette.TEAL_HI);
        }
        tick(g, x, y, w, h, 10, max);
        tick(g, x, y, w, h, 25, max);
        tick(g, x, y, w, h, 50, max);
    }

    private static void tick(GuiGraphics g, int x, int y, int w, int h, int at, int max) {
        if (at > max) {
            return;
        }
        int px = x + w * at / max;
        g.fill(px, y - 2, px + 1, y + h + 1, CorkPalette.BAR_TICK);
    }

    public static void scrollBar(GuiGraphics g, CorkBoardLayout.Rect rail, int scroll, int maxScroll, int visible,
                                int total) {
        blit(g, CorkSprites.SCROLL_TRACK, rail);
        if (maxScroll <= 0 || total <= 0) {
            return;
        }
        int innerH = Math.max(8, rail.h() - 2);
        int thumbH = Math.max(12, innerH * visible / Math.max(visible, total));
        int thumbY = rail.y() + 1 + (innerH - thumbH) * scroll / Math.max(1, maxScroll);
        blit(g, CorkSprites.SCROLL_THUMB, rail.x() + 1, thumbY, 4, thumbH);
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label,
                              boolean hover, boolean enabled, ButtonStyle style) {
        ResourceLocation sprite;
        int color;
        if (!enabled) {
            sprite = CorkSprites.BTN_OFF;
            color = CorkPalette.BUTTON_DISABLED;
        } else if (style == ButtonStyle.DANGER) {
            sprite = CorkSprites.BTN_DANGER;
            color = CorkPalette.BUTTON_DANGER;
        } else if (style == ButtonStyle.GOLD) {
            sprite = hover ? CorkSprites.BTN_HOVER : CorkSprites.BTN_GOLD;
            color = CorkPalette.BUTTON_GOLD;
        } else if (style == ButtonStyle.TEAL) {
            sprite = CorkSprites.BTN_TEAL;
            color = CorkPalette.BUTTON_TEAL;
        } else if (style == ButtonStyle.SELECTED) {
            sprite = CorkSprites.BTN_SEL;
            color = CorkPalette.FILTER_ACTIVE;
        } else {
            sprite = hover ? CorkSprites.BTN_HOVER : CorkSprites.BTN;
            color = CorkPalette.BUTTON;
        }
        blit(g, sprite, x, y, w, h);
        String fitted = ellipsize(font, label.getString(), Math.max(8, w - 8));
        int tx = x + (w - font.width(fitted)) / 2;
        int ty = y + Math.max(0, (h - 8) / 2);
        if (style == ButtonStyle.GOLD || style == ButtonStyle.TEAL) {
            bold(g, font, fitted, tx, ty, color);
        } else {
            text(g, font, fitted, tx, ty, color);
        }
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label,
                              boolean hover, boolean enabled, boolean danger) {
        button(g, font, x, y, w, h, label, hover, enabled, danger ? ButtonStyle.DANGER : ButtonStyle.NORMAL);
    }

    public static void chip(GuiGraphics g, Font font, CorkBoardLayout.Rect r, Component label,
                            ResourceLocation icon, int iconW, int iconH, boolean active, boolean hover) {
        blit(g, active || hover ? CorkSprites.CHIP_ON : CorkSprites.CHIP, r);
        int tint = active ? CorkPalette.FILTER_ACTIVE : CorkPalette.FILTER;
        int ix = r.x() + 5;
        int iy = r.y() + Math.max(0, (r.h() - iconH) / 2);
        blitTint(g, icon, ix, iy, iconW, iconH, tint);
        int textX = ix + iconW + 3;
        int maxW = Math.max(8, r.x() + r.w() - 4 - textX);
        text(g, font, ellipsize(font, label.getString(), maxW),
                textX, r.y() + Math.max(0, (r.h() - 8) / 2), tint);
    }

    public static void toggle(GuiGraphics g, int x, int y, boolean on) {
        blit(g, on ? CorkSprites.TOGGLE_ON : CorkSprites.TOGGLE_OFF, x, y, 18, 9);
    }

    public static void checkbox(GuiGraphics g, int x, int y, boolean on) {
        blit(g, on ? CorkSprites.CHECK_ON : CorkSprites.CHECK_OFF, x, y, 9, 9);
    }

    public static void input(GuiGraphics g, int x, int y, int w, int h, boolean focus, boolean error) {
        blit(g, error ? CorkSprites.INPUT_ERR : focus ? CorkSprites.INPUT_FOCUS : CorkSprites.INPUT, x, y, w, h);
    }

    /** Draw at most two 8 px lines. Returns the y of the last line. */
    public static int wrap2(GuiGraphics g, Font font, String s, int x, int y, int maxW, int color) {
        if (maxW < 8 || font.width(s) <= maxW) {
            text(g, font, s, x, y, color);
            return y;
        }
        int split = -1;
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == ' ' && font.width(s.substring(0, i)) <= maxW) {
                split = i;
            }
        }
        if (split < 0) {
            text(g, font, ellipsize(font, s, maxW), x, y, color);
            return y;
        }
        text(g, font, s.substring(0, split), x, y, color);
        text(g, font, s.substring(split + 1), x, y + 9, color);
        return y + 9;
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

    public enum ButtonStyle {
        NORMAL, GOLD, TEAL, DANGER, SELECTED
    }
}
