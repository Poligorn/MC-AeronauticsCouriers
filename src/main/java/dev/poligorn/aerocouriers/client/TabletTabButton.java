package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Board tab: tablet monitor style or vanilla when configured. */
public final class TabletTabButton extends Button {
    private boolean selected;
    private final boolean tabletChrome;

    public TabletTabButton(int x, int y, int w, int h, Component label, Runnable onSelect) {
        super(x, y, w, h, label, b -> onSelect.run(), DEFAULT_NARRATION);
        this.tabletChrome = !CouriersClientConfig.useVanillaSprites();
    }

    public TabletTabButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        WidgetSprites sprites = tabletChrome
                ? AerocouriersGuiSprites.tabletTabSprites(selected)
                : AerocouriersGuiSprites.tabSprites(selected);
        ResourceLocation sprite = sprites.get(selected, isHoveredOrFocused());
        int drawH = selected ? height : height - 4;
        int drawY = getY() + (selected ? 0 : 4);
        graphics.blitSprite(sprite, getX(), drawY, width, drawH);
        int color;
        if (!active) {
            color = tabletChrome ? TabletGui.TEXT_BUTTON_DISABLED : CreateGui.TEXT_BUTTON_DISABLED;
        } else if (selected) {
            color = tabletChrome ? TabletGui.TEXT_BRIGHT : CreateGui.TEXT_BUTTON;
        } else if (isHoveredOrFocused()) {
            color = tabletChrome ? TabletGui.TEXT_BUTTON_HOVER : CreateGui.TEXT_MUTED;
        } else {
            color = tabletChrome ? TabletGui.TEXT_MUTED : CreateGui.TEXT_MUTED;
        }
        var font = Minecraft.getInstance().font;
        int textY = getY() + (height - 8) / 2;
        graphics.drawCenteredString(font, getMessage(), getX() + width / 2, textY, color);
    }
}
