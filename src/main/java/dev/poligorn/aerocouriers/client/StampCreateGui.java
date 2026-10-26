package dev.poligorn.aerocouriers.client;

import net.minecraft.client.gui.GuiGraphics;

/** Clipboard chrome for {@link StampCreateScreen} only (wood frame + paper + clip). */
public final class StampCreateGui {
    public static final int TEXT = 0x3E2812;
    public static final int TEXT_MUTED = 0x6B453E;
    public static final int TEXT_ACCENT = 0x875A4D;
    public static final int TEXT_OK = 0x4F6B3A;
    public static final int TEXT_WARN = 0xA56A28;
    public static final int TEXT_BAD = 0xA04A3A;

    public static final int CLIP_W = 128;
    public static final int CLIP_H = 38;
    /** Pixels the clip sprite extends above the wooden panel top (lever / ring). */
    public static final int CLIP_OVERHANG = 20;
    /** Wood border thickness before the paper sheet. */
    public static final int FRAME_PAD = 6;

    private StampCreateGui() {
    }

    public static void drawWoodenFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        CreateGui.drawWindow(graphics, x, y, w, h);
    }

    public static void drawPaperSheet(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.PAPER_SHEET, x, y, w, h);
        }
    }

    public static void drawClip(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(AerocouriersGuiSprites.CLIPBOARD_CLIP, x, y, CLIP_W, CLIP_H);
    }

    public static void drawPaperField(GuiGraphics graphics, int x, int y, int w, int h, boolean focused) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.blitSprite(
                focused ? AerocouriersGuiSprites.PAPER_FIELD_FOCUSED : AerocouriersGuiSprites.PAPER_FIELD,
                x, y, w, h);
    }

    public static void drawPaperSlot(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(AerocouriersGuiSprites.PAPER_SLOT, x, y, 18, 18);
    }
}
