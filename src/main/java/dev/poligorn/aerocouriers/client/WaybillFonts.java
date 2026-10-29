package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Waybill clipboard uses {@code aerocouriers:paper}: vanilla bitmap (incl. Cyrillic in
 * {@code nonlatin_european}) plus Unihex fallback. Do not put ⚠/arrows in strings — draw
 * {@link dev.poligorn.aerocouriers.client.board.PixelGlyphs} instead.
 */
public final class WaybillFonts {
    public static final ResourceLocation PAPER = AeronauticsCouriers.id("paper");

    private WaybillFonts() {
    }

    public static MutableComponent ink(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(PAPER));
    }

    public static MutableComponent ink(Component text) {
        return text.copy().withStyle(style -> {
            Style next = style.withFont(PAPER);
            return next;
        });
    }
}
