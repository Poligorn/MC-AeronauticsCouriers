package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original pixel chrome for the cork board and village admin panel. */
public final class CorkBoardGui {
    public static final int WOOD = 0xFF8A582C;
    public static final int WOOD_DARK = 0xFF4E2C12;
    public static final int WOOD_LIGHT = 0xFFB4743C;
    public static final int WOOD_LINE = 0xFFD4A84A;
    public static final int CORK = 0xFFE2B86E;
    public static final int CORK_DARK = 0xFFB88848;
    public static final int CORK_SPECK = 0xFF8E6428;
    public static final int INK = 0xFF3A2814;
    public static final int INK_MUTED = 0xFF7A6040;
    public static final int CREAM = 0xFFF7EAC8;
    public static final int GOLD_PAPER = 0xFFF4D478;
    public static final int PINK_PAPER = 0xFFF4D4E6;
    public static final int LOCK_PAPER = 0xFFD4D0C6;
    public static final int TEAL = 0xFF2A9A94;
    public static final int TEAL_DARK = 0xFF1A6460;
    public static final int RED = 0xFFC03828;
    public static final int GOLD = 0xFFE0C048;
    public static final int HEADER = 0xFFF5E6C8;
    public static final int PANEL = 0xFF4A2E16;
    public static final int PANEL_LINE = 0xFF2A180A;
    public static final int BAR_BG = 0xFF2A1A0C;
    public static final int BAR_FILL = 0xFF3AA8A0;

    private static final ResourceLocation[] PINS = {
            AerocouriersGuiSprites.CORK_PIN_RED,
            AerocouriersGuiSprites.CORK_PIN_TEAL,
            AerocouriersGuiSprites.CORK_PIN_BRASS,
            AerocouriersGuiSprites.CORK_PIN_PINK
    };

    private CorkBoardGui() {
    }

    public static void fullBright() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
    }

    public static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_FRAME, x, y, w, h);
    }

    public static void cork(GuiGraphics graphics, int x, int y, int w, int h) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_TILE, x, y, w, h);
    }

    public static void headerBand(GuiGraphics graphics, int x, int y, int w, int h) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_HEADER, x, y, w, h);
    }

    public static void interior(GuiGraphics graphics, int x, int y, int w, int h) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_INTERIOR, x, y, w, h);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_PANEL, x, y, w, h);
    }

    public static void paper(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        if (fill == PANEL) {
            panel(graphics, x, y, w, h);
            return;
        }
        paper(graphics, x, y, w, h, paperSprite(fill));
    }

    public static void paper(GuiGraphics graphics, int x, int y, int w, int h, ResourceLocation sprite) {
        fullBright();
        graphics.blitSprite(sprite, x, y, w, h);
    }

    public static ResourceLocation paperSprite(int fill) {
        if (fill == LOCK_PAPER) {
            return AerocouriersGuiSprites.CORK_PAPER_LOCKED;
        }
        if (fill == GOLD_PAPER) {
            return AerocouriersGuiSprites.CORK_PAPER_GOLD;
        }
        if (fill == PINK_PAPER) {
            return AerocouriersGuiSprites.CORK_PAPER_PINK;
        }
        if (fill == 0xFFF4D8B0) {
            return AerocouriersGuiSprites.CORK_PAPER_BLEND;
        }
        return AerocouriersGuiSprites.CORK_PAPER;
    }

    public static void pin(GuiGraphics graphics, int cx, int cy) {
        pin(graphics, cx, cy, 0);
    }

    public static void pin(GuiGraphics graphics, int cx, int cy, int variant) {
        fullBright();
        ResourceLocation sprite = PINS[Math.floorMod(variant, PINS.length)];
        graphics.blitSprite(sprite, cx - 4, cy - 3, 9, 9);
    }

    public static void waxSeal(GuiGraphics graphics, int x, int y) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_WAX, x, y, 14, 14);
    }

    public static void ribbon(GuiGraphics graphics, int x, int y) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_RIBBON, x, y, 12, 16);
    }

    public static void lock(GuiGraphics graphics, int x, int y) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_LOCK, x, y, 10, 12);
    }

    public static void sizeStamp(GuiGraphics graphics, Font font, int x, int y, String size, boolean large) {
        int w = large ? 18 : 16;
        int h = 13;
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_STAMP, x, y, w, h);
        graphics.drawString(font, size, x + (w - font.width(size)) / 2, y + 3, INK, false);
    }

    public static void badge(GuiGraphics graphics, Font font, int x, int y, Component text, int bg, int fg) {
        int w = font.width(text) + 8;
        graphics.fill(x, y, x + w, y + 11, bg);
        graphics.drawString(font, text, x + 4, y + 2, fg, false);
    }

    public static void progressStamp(GuiGraphics graphics, Font font, int x, int y, int w, Component title,
                                     Component nick) {
        int h = 22;
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_PROGRESS, x, y, Math.max(48, w), h);
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
        fullBright();
        ResourceLocation sprite;
        if (!enabled) {
            sprite = AerocouriersGuiSprites.CORK_BUTTON;
        } else if (danger) {
            sprite = hover
                    ? AerocouriersGuiSprites.CORK_BUTTON_DANGER_HOVER
                    : AerocouriersGuiSprites.CORK_BUTTON_DANGER;
        } else {
            sprite = hover ? AerocouriersGuiSprites.CORK_BUTTON_HOVER : AerocouriersGuiSprites.CORK_BUTTON;
        }
        graphics.blitSprite(sprite, x, y, w, h);
        int text = !enabled ? 0xFF8A7A60 : HEADER;
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
    }

    public static void chip(GuiGraphics graphics, Font font, int x, int y, int w, int h, Component label,
                            boolean on, boolean hover) {
        fullBright();
        graphics.blitSprite(AerocouriersGuiSprites.CORK_CHIP, x, y, w, h);
        if (on) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x668A5A30);
        } else if (hover) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x335A3A20);
        }
        graphics.drawString(font, label, x + 5, y + (h - 8) / 2, HEADER, false);
    }

    public static void toggle(GuiGraphics graphics, Font font, int x, int y, Component label, boolean on) {
        fullBright();
        graphics.blitSprite(on ? AerocouriersGuiSprites.CORK_SWITCH_ON : AerocouriersGuiSprites.CORK_SWITCH_OFF,
                x, y, 22, 12);
        graphics.drawString(font, label, x + 26, y + 2, HEADER, false);
    }

    public static int toggleW(Font font, Component label) {
        return 26 + font.width(label);
    }

    public static void checkbox(GuiGraphics graphics, Font font, int x, int y, Component label, boolean on) {
        toggle(graphics, font, x, y, label, on);
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
