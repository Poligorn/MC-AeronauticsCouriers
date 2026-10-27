package dev.poligorn.aerocouriers.client.board;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Label + value dropdown chip (cycles on click; arrow zone toggles sort direction). */
public final class TabletFilterDropdown extends Button {
    static final int ARROW_W = 12;
    private final Component prefix;
    private Component value;
    private boolean sortStyle;
    private boolean sortDescending;
    private @Nullable Component hint;
    private Runnable onCycle = () -> {};
    private Runnable onToggleDirection = () -> {};

    public TabletFilterDropdown(int x, int y, int w, int h, Component prefix, Component value) {
        super(x, y, w, h, Component.empty(), b -> {}, DEFAULT_NARRATION);
        this.prefix = prefix;
        this.value = value;
    }

    public TabletFilterDropdown sortStyle(boolean descending) {
        this.sortStyle = true;
        this.sortDescending = descending;
        return this;
    }

    public TabletFilterDropdown onCycle(Runnable onCycle) {
        this.onCycle = onCycle;
        return this;
    }

    public TabletFilterDropdown onToggleDirection(Runnable onToggleDirection) {
        this.onToggleDirection = onToggleDirection;
        return this;
    }

    public TabletFilterDropdown hint(Component hint) {
        this.hint = hint;
        return this;
    }

    @Nullable
    public Component hint() {
        return hint;
    }

    public void setValue(Component value) {
        this.value = value;
    }

    public void setSortDescending(boolean sortDescending) {
        this.sortDescending = sortDescending;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0) {
            return false;
        }
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (sortStyle && mouseX >= getX() + width - ARROW_W) {
            onToggleDirection.run();
        } else {
            onCycle.run();
        }
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        graphics.blitSprite(AerocouriersGuiSprites.tabletButtonSprites().get(true, isHoveredOrFocused()),
                getX(), getY(), width, height);
        var font = Minecraft.getInstance().font;
        int color = active ? TabletGui.TEXT : TabletGui.TEXT_BUTTON_DISABLED;
        String prefixRaw = prefix.getString() + " ";
        int x = getX() + 4;
        graphics.drawString(font, prefixRaw, x, getY() + (height - 8) / 2, TabletGui.TEXT_MUTED, false);
        x += font.width(prefixRaw);
        String valueRaw = value.getString();
        if (sortStyle) {
            valueRaw = valueRaw + (sortDescending ? " ▼" : " ▲");
        } else {
            valueRaw = valueRaw + " ▾";
        }
        int maxW = getX() + width - 4 - x;
        String shown = font.plainSubstrByWidth(valueRaw, maxW);
        graphics.drawString(font, shown, x, getY() + (height - 8) / 2, color, false);
    }
}
