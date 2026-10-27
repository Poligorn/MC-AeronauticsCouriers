package dev.poligorn.aerocouriers.client.board;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Compact on/off chip for tablet filter rows. */
public final class TabletFilterChip extends Button {
    private boolean selected = true;
    private @Nullable Component hint;

    public TabletFilterChip(int x, int y, int w, int h, Component label, Runnable onPress) {
        super(x, y, w, h, label, b -> onPress.run(), DEFAULT_NARRATION);
    }

    public TabletFilterChip selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public TabletFilterChip hint(Component hint) {
        this.hint = hint;
        return this;
    }

    @Nullable
    public Component hint() {
        return hint;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        ResourceLocation sprite = AerocouriersGuiSprites.tabletTabSprites(selected).get(selected, isHoveredOrFocused());
        int drawH = selected ? height : height - 2;
        int drawY = getY() + (selected ? 0 : 2);
        graphics.blitSprite(sprite, getX(), drawY, width, drawH);
        int color = !active
                ? TabletGui.TEXT_BUTTON_DISABLED
                : selected ? TabletGui.TEXT_BRIGHT : TabletGui.TEXT_MUTED;
        if (isHoveredOrFocused() && active && !selected) {
            color = TabletGui.TEXT;
        }
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, color);
    }
}
