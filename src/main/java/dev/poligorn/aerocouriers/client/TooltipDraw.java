package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import dev.poligorn.aerocouriers.client.board.TabletDraw;
import dev.poligorn.aerocouriers.client.board.TabletPalette;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
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
        // Vanilla renderTooltip fills at z=400 and submits text at the same z; the fill wins
        // the depth test (empty purple box — same bug as CorkNoteTooltip).
        renderPlain(graphics, font, text, mouseX, mouseY);
    }

    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    private static final int LINE = 10;
    private static final int TEXT_Z = 410;
    private static int lastX;
    private static int lastY;
    private static int lastW;
    private static int lastH;

    public static int lastX() {
        return lastX;
    }

    public static int lastY() {
        return lastY;
    }

    public static int lastW() {
        return lastW;
    }

    public static int lastH() {
        return lastH;
    }

    private static void renderPlain(GuiGraphics graphics, Font font, List<Component> text, int mouseX, int mouseY) {
        int textW = 0;
        List<String> lines = new ArrayList<>(text.size());
        for (Component line : text) {
            String s = line.getString();
            lines.add(s);
            textW = Math.max(textW, MoneyText.iconUnits() && MoneyText.containsUnit(s)
                    ? MoneyText.width(font, s)
                    : font.width(s));
        }
        int w = textW + PAD_X * 2;
        int h = PAD_Y * 2 + lines.size() * LINE - 1;
        Minecraft minecraft = Minecraft.getInstance();
        int sw = minecraft.getWindow().getGuiScaledWidth();
        int sh = minecraft.getWindow().getGuiScaledHeight();
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + w > sw - 4) {
            x = mouseX - 12 - w;
        }
        x = Mth.clamp(x, 4, Math.max(4, sw - w - 4));
        y = Mth.clamp(y, 4, Math.max(4, sh - h - 4));
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        graphics.flush();
        TabletDraw.box(graphics, x, y, x + w - 1, y + h - 1, TabletPalette.TOOLTIP_BG, TabletPalette.TOOLTIP_BORDER);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TEXT_Z);
        RenderSystem.disableDepthTest();
        int ty = y + PAD_Y;
        for (String line : lines) {
            if (MoneyText.iconUnits() && MoneyText.containsUnit(line)) {
                MoneyText.draw(graphics, font, line, x + PAD_X, ty, TabletPalette.TXT, MoneyText.Face.DEFAULT);
            } else {
                graphics.drawString(font, line, x + PAD_X, ty, TabletPalette.TXT, false);
            }
            ty += LINE;
        }
        graphics.flush();
        graphics.pose().popPose();
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
