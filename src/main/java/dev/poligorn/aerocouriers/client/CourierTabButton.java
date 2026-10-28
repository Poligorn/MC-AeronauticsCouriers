package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Board tab using CSI tab sprites (unselected tabs are 4px shorter visually). */
public final class CourierTabButton extends Button {
    private boolean selected;

    public CourierTabButton(int x, int y, int w, int h, Component label, Runnable onSelect) {
        super(x, y, w, h, label, b -> onSelect.run(), DEFAULT_NARRATION);
    }

    public CourierTabButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        WidgetSprites sprites = AerocouriersGuiSprites.tabSprites(selected);
        ResourceLocation sprite = sprites.get(selected, isHoveredOrFocused());
        int drawH = selected ? height : height - 4;
        int drawY = getY() + (selected ? 0 : 4);
        graphics.blitSprite(sprite, getX(), drawY, width, drawH);
        int color = active ? (selected ? CreateGui.TEXT_BUTTON : CreateGui.TEXT_MUTED) : CreateGui.TEXT_BUTTON_DISABLED;
        var font = Minecraft.getInstance().font;
        int textY = getY() + (height - 8) / 2;
        graphics.drawCenteredString(font, getMessage(), getX() + width / 2, textY, color);
    }
}
