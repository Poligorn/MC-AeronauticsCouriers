package dev.poligorn.aerocouriers.client.board;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Drawing primitives matching the approved pixel mockup (1 GUI px = 1 mockup px at GUI scale 3). */
public final class TabletDraw {
    public static final int BUTTON_H = 20;

    public enum ButtonKind {
        PRIMARY,
        MAP,
        SECONDARY,
        DANGER,
        DISABLED
    }

    private TabletDraw() {
    }

    public static void text(GuiGraphics graphics, Font font, String s, int x, int y, int color) {
        graphics.drawString(font, s, x, y, color, false);
    }

    /** Mockup bold: the same string drawn twice, one pixel apart. */
    public static void bold(GuiGraphics graphics, Font font, String s, int x, int y, int color) {
        graphics.drawString(font, s, x, y, color, false);
        graphics.drawString(font, s, x + 1, y, color, false);
    }

    public static int boldWidth(Font font, String s) {
        return font.width(s) + 1;
    }

    /** Right-aligned text ending at {@code right}; returns its left x. */
    public static int textRight(GuiGraphics graphics, Font font, String s, int right, int y, int color, boolean bold) {
        int x = right - (bold ? boldWidth(font, s) : font.width(s));
        if (bold) {
            bold(graphics, font, s, x, y, color);
        } else {
            text(graphics, font, s, x, y, color);
        }
        return x;
    }

    public static String trunc(Font font, String s, int width) {
        if (font.width(s) <= width) {
            return s;
        }
        String ellipsis = "…";
        String cut = s;
        while (!cut.isEmpty() && font.width(cut + ellipsis) > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + ellipsis;
    }

    public static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1 + 1, y0 + 1, color);
        graphics.fill(x0, y1, x1 + 1, y1 + 1, color);
        graphics.fill(x0, y0, x0 + 1, y1 + 1, color);
        graphics.fill(x1, y0, x1 + 1, y1 + 1, color);
    }

    /** Filled rectangle with a 1 px outline; corners are inclusive like PIL {@code rectangle}. */
    public static void box(GuiGraphics graphics, int x0, int y0, int x1, int y1, int fill, int border) {
        graphics.fill(x0, y0, x1 + 1, y1 + 1, fill);
        outline(graphics, x0, y0, x1, y1, border);
    }

    public static void hline(GuiGraphics graphics, int x0, int x1, int y, int color) {
        graphics.fill(x0, y, x1 + 1, y + 1, color);
    }

    public static void vline(GuiGraphics graphics, int x, int y0, int y1, int color) {
        graphics.fill(x, y0, x + 1, y1 + 1, color);
    }

    /** Overlay window: dark outline, body, 1 px bevel and header strip of {@code headerH} rows. */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h, int headerH) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, TabletPalette.OUTLINE);
        graphics.fill(x, y, x + w, y + h, TabletPalette.PANEL_BG);
        hline(graphics, x, x + w - 1, y, TabletPalette.BEVEL_LIGHT);
        vline(graphics, x, y, y + h - 1, TabletPalette.BEVEL_LIGHT);
        hline(graphics, x, x + w - 1, y + h - 1, TabletPalette.BEVEL_DARK);
        vline(graphics, x + w - 1, y, y + h - 1, TabletPalette.BEVEL_DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 1 + headerH, TabletPalette.PANEL_HEADER);
    }

    /** Mockup {@code btn3}: 20 px tall, centred label, optional leading glyph. */
    public static void button(GuiGraphics graphics, Font font, int x, int y, int w, String label, ButtonKind kind,
                              String[] icon, boolean hovered) {
        int textColor;
        switch (kind) {
            case PRIMARY -> {
                box(graphics, x, y, x + w - 1, y + BUTTON_H - 1,
                        hovered ? TabletPalette.hoverBrighten(TabletPalette.PRIMARY_BG) : TabletPalette.PRIMARY_BG,
                        hovered ? TabletPalette.TXT : TabletPalette.TEAL);
                hline(graphics, x + 1, x + w - 2, y + 1, TabletPalette.TEAL);
                textColor = TabletPalette.PRIMARY_TEXT;
            }
            case MAP -> {
                box(graphics, x, y, x + w - 1, y + BUTTON_H - 1,
                        hovered ? TabletPalette.hoverBrighten(TabletPalette.MAP_BG) : TabletPalette.MAP_BG,
                        hovered ? TabletPalette.TXT : TabletPalette.BLUE);
                textColor = TabletPalette.BLUE;
            }
            case DANGER -> {
                box(graphics, x, y, x + w - 1, y + BUTTON_H - 1,
                        hovered ? TabletPalette.hoverBrighten(TabletPalette.WARN_BG) : TabletPalette.WARN_BG,
                        hovered ? TabletPalette.TXT : TabletPalette.CORAL);
                textColor = hovered ? TabletPalette.TXT : TabletPalette.CORAL;
            }
            case DISABLED -> {
                box(graphics, x, y, x + w - 1, y + BUTTON_H - 1, TabletPalette.DISABLED_BG,
                        TabletPalette.DISABLED_BORDER);
                textColor = TabletPalette.LOCKC;
            }
            default -> {
                box(graphics, x, y, x + w - 1, y + BUTTON_H - 1,
                        hovered ? TabletPalette.hoverBrighten(TabletPalette.CHIP_BG) : TabletPalette.CHIP_BG,
                        hovered ? TabletPalette.GOLD : TabletPalette.BRASS);
                textColor = hovered ? TabletPalette.TXT : TabletPalette.SEC;
            }
        }
        int iconW = icon != null ? PixelGlyphs.width(icon) + 4 : 0;
        String shown = trunc(font, label, w - 8 - iconW);
        int labelW = kind == ButtonKind.PRIMARY ? boldWidth(font, shown) : font.width(shown);
        int tx = x + (w - labelW - iconW) / 2;
        if (icon != null) {
            PixelGlyphs.draw(graphics, tx, y + 6, icon, textColor);
            tx += iconW;
        }
        if (kind == ButtonKind.PRIMARY) {
            bold(graphics, font, shown, tx, y + 6, textColor);
        } else {
            text(graphics, font, shown, tx, y + 6, textColor);
        }
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** {@code 1820 -> "1 820"} (space-grouped, as on the mockup). */
    public static String num(long value) {
        String raw = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        int lead = raw.length() % 3;
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) {
                out.append(' ');
            }
            out.append(raw.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }

    /** Weight without a trailing {@code .0}: {@code 24.0 -> "24"}, {@code 2.5 -> "2.5"}. */
    public static String weight(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return num((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** Plural form key suffix {@code one|few|many}: East Slavic rules for ru/uk/be, otherwise English. */
    public static Component plural(String baseKey, long count, Object... args) {
        long n = Math.abs(count) % 100;
        long n1 = n % 10;
        String lang = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        boolean slavic = lang.startsWith("ru") || lang.startsWith("uk") || lang.startsWith("be");
        String form;
        if (!slavic) {
            form = Math.abs(count) == 1 ? "one" : "many";
        } else if (n > 10 && n < 20) {
            form = "many";
        } else if (n1 == 1) {
            form = "one";
        } else if (n1 >= 2 && n1 <= 4) {
            form = "few";
        } else {
            form = "many";
        }
        return Component.translatable(baseKey + "." + form, args);
    }
}
