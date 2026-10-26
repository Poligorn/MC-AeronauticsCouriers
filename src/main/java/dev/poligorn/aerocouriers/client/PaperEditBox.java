package dev.poligorn.aerocouriers.client;

import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Edit field for clipboard / paper backgrounds: crisp ink without vanilla text shadow,
 * warm selection highlight (see {@link WaybillCreateScreen}).
 */
public class PaperEditBox extends EditBox {
    public static final int TEXT = 0x3E2812;
    public static final int TEXT_HINT = 0x8A7458;
    private static final int SELECTION = 0x66B36833;

    private final Font textFont;

    public PaperEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.textFont = font;
        setBordered(false);
        setTextShadow(false);
        setTextColor(TEXT);
        setTextColorUneditable(TEXT_HINT);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }

        String value = getValue();
        int displayPos = EditBoxAccess.displayPos(this);
        int cursorPos = getCursorPosition();
        int highlightPos = EditBoxAccess.highlightPos(this);
        boolean editable = EditBoxAccess.isEditable(this);
        int mainColor = editable ? EditBoxAccess.textColor(this) : EditBoxAccess.textColorUneditable(this);

        int cursorIndex = cursorPos - displayPos;
        String visible = textFont.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        boolean cursorInView = cursorIndex >= 0 && cursorIndex <= visible.length();
        boolean cursorBlink = isFocused()
                && (Util.getMillis() - EditBoxAccess.focusedTime(this)) / 300L % 2L == 0L
                && cursorInView;
        int textX = isBordered() ? getX() + 4 : getX();
        int textY = isBordered() ? getY() + (height - 8) / 2 : getY();
        int drawX = textX;
        int highlightIndex = Mth.clamp(highlightPos - displayPos, 0, visible.length());

        if (!visible.isEmpty()) {
            String beforeCursor = cursorInView ? visible.substring(0, cursorIndex) : visible;
            drawX = guiGraphics.drawString(
                    textFont,
                    EditBoxAccess.formatter(this).apply(beforeCursor, displayPos),
                    textX,
                    textY,
                    mainColor,
                    false);
        }

        boolean atEnd = cursorPos < value.length() || value.length() >= EditBoxAccess.maxLength(this);
        int cursorX = drawX;
        if (!cursorInView) {
            cursorX = cursorIndex > 0 ? textX + width : textX;
        } else if (atEnd) {
            cursorX = drawX - 1;
            drawX--;
        }

        if (!visible.isEmpty() && cursorInView && cursorIndex < visible.length()) {
            guiGraphics.drawString(
                    textFont,
                    EditBoxAccess.formatter(this).apply(visible.substring(cursorIndex), cursorPos),
                    drawX,
                    textY,
                    mainColor,
                    false);
        }

        @Nullable Component hint = EditBoxAccess.hint(this);
        if (hint != null && visible.isEmpty() && !isFocused()) {
            guiGraphics.drawString(textFont, hint, drawX, textY, TEXT_HINT, false);
        }

        @Nullable String suggestion = EditBoxAccess.suggestion(this);
        if (!atEnd && suggestion != null) {
            guiGraphics.drawString(textFont, suggestion, cursorX - 1, textY, TEXT_HINT, false);
        }

        if (cursorBlink) {
            if (atEnd) {
                guiGraphics.fill(RenderType.guiOverlay(), cursorX, textY - 1, cursorX + 1, textY + 1 + 9, TEXT);
            } else {
                guiGraphics.drawString(textFont, "_", cursorX, textY, mainColor, false);
            }
        }

        if (highlightIndex != cursorIndex) {
            int selStartX = textX + textFont.width(visible.substring(0, highlightIndex));
            renderSelection(guiGraphics, cursorX, textY - 1, selStartX - 1, textY + 1 + 9);
        }
    }

    private void renderSelection(GuiGraphics guiGraphics, int minX, int minY, int maxX, int maxY) {
        if (minX < maxX) {
            int swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minY < maxY) {
            int swap = minY;
            minY = maxY;
            maxY = swap;
        }
        int right = getX() + width;
        maxX = Math.min(maxX, right);
        minX = Math.min(minX, right);
        guiGraphics.fill(RenderType.guiTextHighlight(), minX, minY, maxX, maxY, SELECTION);
    }
}
