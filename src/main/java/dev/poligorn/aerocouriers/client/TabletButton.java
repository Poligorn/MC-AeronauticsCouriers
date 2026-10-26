package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/** Board-screen button: tablet sprites or vanilla widgets when configured. */
public final class TabletButton extends Button {
    private final WidgetSprites sprites;
    private final boolean tabletChrome;
    private ItemStack itemIcon = ItemStack.EMPTY;
    private @Nullable ResourceLocation atlasIcon;
    private @Nullable Component hint;

    private TabletButton(int x, int y, int w, int h, Component message, OnPress onPress, WidgetSprites sprites,
                         boolean tabletChrome) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.sprites = sprites;
        this.tabletChrome = tabletChrome;
    }

    public TabletButton icon(ResourceLocation sprite) {
        this.atlasIcon = sprite;
        return this;
    }

    public TabletButton item(ItemLike item) {
        this.itemIcon = new ItemStack(item);
        return this;
    }

    public TabletButton hint(Component hint) {
        this.hint = hint;
        return this;
    }

    @Nullable
    Component hint() {
        return hint;
    }

    public static TabletButton create(int x, int y, int w, int h, Component message, OnPress onPress) {
        boolean vanilla = CouriersClientConfig.useVanillaSprites();
        WidgetSprites sprites = vanilla ? AerocouriersGuiSprites.buttonSprites() : AerocouriersGuiSprites.tabletButtonSprites();
        return new TabletButton(x, y, w, h, message, onPress, sprites, !vanilla);
    }

    public static TabletButton cycle(int x, int y, int w, int h, Component prefix,
                                     java.util.List<Component> labels, Consumer<Integer> onChange) {
        int[] index = {0};
        TabletButton button = create(x, y, w, h, option(prefix, labels.getFirst()), b -> {
            index[0] = (index[0] + 1) % labels.size();
            b.setMessage(option(prefix, labels.get(index[0])));
            onChange.accept(index[0]);
        });
        return button;
    }

    private static Component option(Component prefix, Component value) {
        return Component.translatable("options.generic_value", prefix, value);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        ResourceLocation sprite = sprites.get(active, isHoveredOrFocused());
        graphics.blitSprite(sprite, getX(), getY(), getWidth(), getHeight());
        Font font = Minecraft.getInstance().font;
        int textX = getX() + 4;
        int iconY = getY() + (getHeight() - 16) / 2;
        if (!itemIcon.isEmpty()) {
            graphics.renderFakeItem(itemIcon, getX() + 2, iconY);
            textX = getX() + 20;
        } else if (atlasIcon != null) {
            graphics.blitSprite(atlasIcon, getX() + 2, iconY, 16, 16);
            textX = getX() + 20;
        }
        int color;
        if (!active) {
            color = tabletChrome ? TabletGui.TEXT_BUTTON_DISABLED : CreateGui.TEXT_BUTTON_DISABLED;
        } else if (isHoveredOrFocused()) {
            color = tabletChrome ? TabletGui.TEXT_BUTTON_HOVER : CreateGui.TEXT_BUTTON;
        } else {
            color = tabletChrome ? TabletGui.TEXT_BUTTON : CreateGui.TEXT_BUTTON;
        }
        int textW = Math.max(8, getX() + getWidth() - 4 - textX);
        String raw = getMessage().getString();
        String shown = font.plainSubstrByWidth(raw, textW);
        if (!shown.equals(raw)) {
            shown = font.plainSubstrByWidth(raw, Math.max(4, textW - font.width("…"))) + "…";
        }
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, shown, textX, textY, color, false);
    }
}
