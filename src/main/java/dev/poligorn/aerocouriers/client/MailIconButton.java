package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.net.ClaimMailPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** 20×20 envelope opener with unread-mail badge. */
public final class MailIconButton extends Button {
    private final int unread;
    private final boolean tabletMonitor;

    public MailIconButton(int x, int y, int unread) {
        this(x, y, unread, false);
    }

    public MailIconButton(int x, int y, int unread, boolean tabletMonitor) {
        super(x, y, 20, 20, Component.empty(), b -> PacketDistributor.sendToServer(ClaimMailPayload.INSTANCE),
                DEFAULT_NARRATION);
        this.unread = unread;
        this.tabletMonitor = tabletMonitor;
        setTooltip(Tooltip.create(Component.translatable("ui.aerocouriers.mail.tooltip")));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        if (!tabletMonitor || CouriersClientConfig.useVanillaSprites()) {
            WidgetSprites sprites = AerocouriersGuiSprites.buttonSprites();
            ResourceLocation sprite = sprites.get(active, isHoveredOrFocused());
            graphics.blitSprite(sprite, getX(), getY(), width, height);
            graphics.blitSprite(AerocouriersGuiSprites.ENVELOPE, getX() + 2, getY() + 2, 16, 16);
        } else {
            WidgetSprites sprites = AerocouriersGuiSprites.tabletButtonSprites();
            ResourceLocation sprite = sprites.get(active, isHoveredOrFocused());
            graphics.blitSprite(sprite, getX(), getY(), width, height);
            graphics.blitSprite(AerocouriersGuiSprites.ENVELOPE_TABLET, getX() + 2, getY() + 2, 16, 16);
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
