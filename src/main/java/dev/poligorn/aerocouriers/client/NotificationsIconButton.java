package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 20×20 bell opener with unread-notification badge. */
public final class NotificationsIconButton extends Button {
    private final int unread;
    private final boolean tabletMonitor;

    public NotificationsIconButton(int x, int y, int unread, boolean tabletMonitor) {
        super(x, y, 20, 20, Component.empty(), b -> {}, DEFAULT_NARRATION);
        this.unread = unread;
        this.tabletMonitor = tabletMonitor;
        setTooltip(Tooltip.create(Component.translatable("ui.aerocouriers.notifications.tooltip")));
    }

    @Override
    public void onPress() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            minecraft.setScreen(new CourierNotificationsScreen(minecraft.screen));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        if (!tabletMonitor || CouriersClientConfig.useVanillaSprites()) {
            WidgetSprites sprites = AerocouriersGuiSprites.buttonSprites();
            ResourceLocation sprite = sprites.get(active, isHoveredOrFocused());
            graphics.blitSprite(sprite, getX(), getY(), width, height);
            graphics.blitSprite(AerocouriersGuiSprites.BELL, getX() + 2, getY() + 2, 16, 16);
        } else {
            WidgetSprites sprites = AerocouriersGuiSprites.tabletButtonSprites();
            ResourceLocation sprite = sprites.get(active, isHoveredOrFocused());
            graphics.blitSprite(sprite, getX(), getY(), width, height);
            graphics.blitSprite(AerocouriersGuiSprites.BELL_TABLET, getX() + 2, getY() + 2, 16, 16);
        }
        if (unread > 0) {
            String label = unread > 99 ? "99+" : Integer.toString(unread);
            var font = Minecraft.getInstance().font;
            int tw = font.width(label);
            int bx = getX() + width - Math.min(tw + 4, 10);
            int by = getY() - 1;
            int badgeBg = tabletMonitor ? 0xFF431A0F : 0xFF4A2D31;
            int badgeText = tabletMonitor ? TabletGui.TEXT_BRIGHT : 0xFFF8F8EC;
            graphics.fill(bx - 1, by - 1, bx + tw + 2, by + 9, badgeBg);
            graphics.drawString(font, label, bx, by, badgeText, false);
        }
    }
}
