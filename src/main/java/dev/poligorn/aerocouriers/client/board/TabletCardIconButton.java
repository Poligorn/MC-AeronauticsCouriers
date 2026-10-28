package dev.poligorn.aerocouriers.client.board;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 17×17 icon button on a board card (mockup {@code btn_}): customer profile or cargo/details. */
public final class TabletCardIconButton extends Button {
    public static final int SIZE = 17;

    public enum Kind {
        PROFILE(PixelGlyphs.PERS),
        CARGO(PixelGlyphs.GRID);

        private final String[] glyph;

        Kind(String[] glyph) {
            this.glyph = glyph;
        }
    }

    private final UUID orderId;
    private final Kind kind;
    private final boolean dimmed;

    public TabletCardIconButton(int x, int y, UUID orderId, Kind kind, boolean dimmed, Consumer<UUID> onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), b -> onPress.accept(orderId), DEFAULT_NARRATION);
        this.orderId = orderId;
        this.kind = kind;
        this.dimmed = dimmed;
    }

    public UUID orderId() {
        return orderId;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHovered();
        int x = getX();
        int y = getY();
        int border = dimmed ? TabletPalette.BRASS_DIM : hover ? TabletPalette.GOLD : TabletPalette.BRASS_LIGHT;
        int fill = hover ? TabletPalette.hoverBrighten(TabletPalette.CHIP_BG) : TabletPalette.CHIP_BG;
        TabletDraw.box(graphics, x, y, x + SIZE - 1, y + SIZE - 1, fill, border);
        int color = dimmed ? TabletPalette.LOCKC : hover ? TabletPalette.TXT : TabletPalette.SEC;
        String[] glyph = kind.glyph;
        PixelGlyphs.draw(graphics, x + 8 - PixelGlyphs.width(glyph) / 2, y + 8 - PixelGlyphs.height(glyph) / 2,
                glyph, color);
    }
}
