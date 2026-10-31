package dev.poligorn.aerocouriers.client;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tooltip entry that does not call {@code GuiGraphics.renderTooltip(Font, List, Optional, int, int)}
 * with an empty text list. NeoForge 21.1 inserts the image component at index 1
 * ({@code ClientHooks.gatherTooltipComponents}) and throws {@link IndexOutOfBoundsException}
 * when that list is empty. The breakdown and cargo images are drawn entirely inside
 * their {@link TooltipComponent}, so they are passed as elements instead.
 */
public final class TooltipDraw {
    private TooltipDraw() {
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            @Nullable List<Component> lines,
            Optional<TooltipComponent> image,
            int mouseX,
            int mouseY
    ) {
        List<Component> text = new ArrayList<>();
        if (lines != null) {
            for (Component line : lines) {
                if (line != null && !line.getString().isBlank()) {
                    text.add(line);
                }
            }
        }
        if (image.isPresent()) {
            graphics.renderComponentTooltipFromElements(
                    font, elements(text, image.get()), mouseX, mouseY, ItemStack.EMPTY);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        graphics.renderTooltip(font, text, Optional.empty(), mouseX, mouseY);
    }

    /**
     * Image after the first text line, matching NeoForge's insert-at-1 order.
     * No text: the image is the only element.
     */
    static List<Either<FormattedText, TooltipComponent>> elements(List<Component> text, TooltipComponent image) {
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>();
        if (text.isEmpty()) {
            elements.add(Either.right(image));
            return elements;
        }
        elements.add(Either.left(text.get(0)));
        elements.add(Either.right(image));
        for (int i = 1; i < text.size(); i++) {
            elements.add(Either.left(text.get(i)));
        }
        return elements;
    }
}
