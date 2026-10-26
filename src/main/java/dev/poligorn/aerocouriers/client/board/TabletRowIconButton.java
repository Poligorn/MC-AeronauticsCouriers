package dev.poligorn.aerocouriers.client.board;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Square 18×18 tablet icon button on board list rows. */
public final class TabletRowIconButton extends Button {
    public enum Kind {
        MAP(AerocouriersGuiSprites.ICON_MAP_PIN),
        CRATE(AerocouriersGuiSprites.ICON_CRATE);

        private final ResourceLocation sprite;

        Kind(ResourceLocation sprite) {
            this.sprite = sprite;
        }
    }

    private final UUID orderId;
    private final Kind kind;
    private final @Nullable Consumer<UUID> onActivate;

    public TabletRowIconButton(int x, int y, UUID orderId, Kind kind, @Nullable Consumer<UUID> onActivate) {
        super(x, y, BoardRowLayout.ICON, BoardRowLayout.ICON, Component.empty(), b -> {
            if (onActivate != null) {
                onActivate.accept(orderId);
            }
        }, DEFAULT_NARRATION);
        this.orderId = orderId;
        this.kind = kind;
        this.onActivate = onActivate;
    }

    public UUID orderId() {
        return orderId;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        WidgetSprites sprites = AerocouriersGuiSprites.tabletButtonSprites();
        ResourceLocation bg = sprites.get(active, isHoveredOrFocused());
        graphics.blitSprite(bg, getX(), getY(), width, height);
        int icon = (width - 16) / 2;
        graphics.blitSprite(kind.sprite, getX() + icon, getY() + icon, 16, 16);
        if (!active) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x66000000);
        }
    }

    public static int labelColor(boolean hovered) {
        return hovered ? TabletGui.TEXT_BUTTON_HOVER : TabletGui.TEXT_BUTTON;
    }
}
