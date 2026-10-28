package dev.poligorn.aerocouriers.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Adaptive CSI-inspired chrome using mod GUI sprites ({@link AerocouriersGuiSprites}).
 */
public final class CreateGui {
    public static final int TEXT = 0xF8F8EC;
    public static final int TEXT_MUTED = 0xCDBCA8;
    /** Dark text on cream {@link AerocouriersGuiSprites#TITLE_LABEL}. */
    public static final int TEXT_TITLE = 0x404040;
    public static final int TEXT_BRASS = 0xF7CB6C;
    public static final int TEXT_OK = 0xA8C98A;
    public static final int TEXT_WARN = 0xE3A73A;
    public static final int TEXT_BAD = 0xC47C4A;
    public static final int TEXT_BUTTON = 0xFFFFFF;
    public static final int TEXT_BUTTON_DISABLED = 0xA0A0A0;

    private CreateGui() {
    }

    public static void drawWindow(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.PANEL, x, y, w, h);
        }
    }

    public static void drawTitleLabel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.TITLE_LABEL, x, y, w, h);
        }
    }

    public static void drawInset(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            graphics.blitSprite(AerocouriersGuiSprites.INSET, x, y, w, h);
        }
    }

    public static void drawField(GuiGraphics graphics, int x, int y, int w, int h, boolean focused) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.blitSprite(
                focused ? AerocouriersGuiSprites.TEXT_FIELD_FOCUSED : AerocouriersGuiSprites.TEXT_FIELD,
                x, y, w, h);
    }

    public static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(AerocouriersGuiSprites.CONTAINER_SLOT, x, y, 18, 18);
    }

    public static void drawRow(GuiGraphics graphics, int x, int y, int w, int h, boolean selected) {
        if (selected && w > 0 && h > 0) {
            graphics.fill(x, y, x + w, y + h, 0x40AA786A);
        }
    }

    public static void drawScrollbar(GuiGraphics graphics, int x, int y, int h, float progress, float thumb) {
        int barH = Math.max(12, h);
        graphics.blitSprite(AerocouriersGuiSprites.SCROLLER_TRACK, x, y, 6, barH);
        int thumbH = Math.max(9, Math.round(barH * thumb));
        int thumbY = y + Math.round((barH - thumbH) * progress);
        graphics.blitSprite(AerocouriersGuiSprites.SCROLLER, x, thumbY, 6, thumbH);
    }

    public static void drawBanner(GuiGraphics graphics, int x, int y, int w) {
        drawTitleLabel(graphics, x, y, w, 18);
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
        drawInset(graphics, x, y, boxW, boxH);
        int drawY = y + pad;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + pad, drawY, TEXT, false);
            drawY += 10;
        }
    }
}
