package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
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

/**
 * Button painted with mod CSI-inspired sprites (or vanilla {@code widget/*} when configured).
 */
public final class CourierButton extends Button {
    private final WidgetSprites sprites;
    private ItemStack itemIcon = ItemStack.EMPTY;
    private @Nullable ResourceLocation atlasIcon;
    private @Nullable Component hint;

    private CourierButton(int x, int y, int w, int h, Component message, OnPress onPress, WidgetSprites sprites) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.sprites = sprites;
    }

    public CourierButton icon(ResourceLocation sprite) {
        this.atlasIcon = sprite;
        return this;
    }

    public CourierButton item(ItemLike item) {
        this.itemIcon = new ItemStack(item);
        return this;
    }

    public CourierButton item(ItemStack stack) {
        this.itemIcon = stack;
        return this;
    }

    public CourierButton hint(Component hint) {
        this.hint = hint;
        return this;
    }

    @Nullable
    Component hint() {
        return hint;
    }

    public static CourierButton create(int x, int y, int w, int h, Component message, OnPress onPress) {
        return new CourierButton(x, y, w, h, message, onPress, AerocouriersGuiSprites.buttonSprites());
    }

    public static CourierButton cycle(int x, int y, int w, int h, Component prefix,
                                      java.util.List<Component> labels, Consumer<Integer> onChange) {
        int[] index = {0};
        CourierButton button = new CourierButton(x, y, w, h, option(prefix, labels.getFirst()), b -> {
            index[0] = (index[0] + 1) % labels.size();
            b.setMessage(option(prefix, labels.get(index[0])));
            onChange.accept(index[0]);
        }, AerocouriersGuiSprites.buttonSprites());
        return button;
    }

    private static Component option(Component prefix, Component value) {
        return Component.translatable("options.generic_value", prefix, value);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
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
        int color = active ? CreateGui.TEXT_BUTTON : CreateGui.TEXT_BUTTON_DISABLED;
        int textW = Math.max(8, getX() + getWidth() - 4 - textX);
        String raw = getMessage().getString();
        String shown = MoneyText.trunc(font, raw, textW);
        int textY = getY() + (getHeight() - 8) / 2;
        MoneyText.draw(graphics, font, shown, textX, textY, color, MoneyText.Face.DEFAULT);
    }
}
