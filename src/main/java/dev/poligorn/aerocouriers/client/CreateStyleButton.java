package dev.poligorn.aerocouriers.client;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * Text / icon button painted with Create {@code widgets.png} (9-sliced) and optional
 * Create icons-atlas slice or an ItemStack. Same click contract as vanilla {@link Button}.
 */
public final class CreateStyleButton extends Button {
    private @Nullable CreateGuiTextures atlasIcon;
    private ItemStack itemIcon = ItemStack.EMPTY;
    private boolean selected;
    private boolean green;
    private @Nullable Component hint;

    private CreateStyleButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    public CreateStyleButton icon(CreateGuiTextures icon) {
        this.atlasIcon = icon;
        return this;
    }

    public CreateStyleButton item(ItemLike item) {
        this.itemIcon = new ItemStack(item);
        return this;
    }

    public CreateStyleButton item(ItemStack stack) {
        this.itemIcon = stack;
        return this;
    }

    public CreateStyleButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public CreateStyleButton green(boolean green) {
        this.green = green;
        return this;
    }

    public CreateStyleButton hint(Component hint) {
        this.hint = hint;
        return this;
    }

    @Nullable
    Component hint() {
        return hint;
    }

    public static CreateStyleButton create(int x, int y, int w, int h, Component message, OnPress onPress) {
        return new CreateStyleButton(x, y, w, h, message, onPress);
    }

    public static CreateStyleButton cycle(int x, int y, int w, int h, Component prefix,
                                         java.util.List<Component> labels, Consumer<Integer> onChange) {
        int[] index = {0};
        CreateStyleButton button = new CreateStyleButton(x, y, w, h, option(prefix, labels.getFirst()), b -> {
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
        boolean pressed = (selected && !active) || (isHovered && Minecraft.getInstance().mouseHandler.isLeftPressed());
        CreateGui.drawButton(graphics, getX(), getY(), getWidth(), getHeight(),
                active || selected, isHoveredOrFocused(), pressed, green && active);
        Font font = Minecraft.getInstance().font;
        int textX = getX() + 4;
        int iconY = getY() + (getHeight() - 16) / 2;
        if (!itemIcon.isEmpty()) {
            graphics.renderFakeItem(itemIcon, getX() + 2, iconY);
            textX = getX() + 20;
        } else if (atlasIcon != null) {
            atlasIcon.blit(graphics, getX() + 1, iconY);
            textX = getX() + 18;
        }
        int color = active || selected ? CreateGui.TEXT_TITLE : CreateGui.TEXT_DISABLED;
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
