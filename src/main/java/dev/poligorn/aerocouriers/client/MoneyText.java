package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The only drawer for strings that contain a money amount from {@link MoneyFormat}.
 * Numismatics: the currency word is painted one letter at a time with {@link #UNIT_GAP} between letters.
 * Emeralds: the currency word is replaced by a vanilla {@code minecraft:emerald} item, {@link #ICON} px,
 * {@link #UNIT_GAP} after the number. Every other word uses the vanilla advance.
 * Layout must use {@link #width}, not {@code Font#width}.
 * Bold is per letter: a second copy one pixel to the right must not fill the tracking gap,
 * which a whole-string offset does when the gap is only one pixel. The item icon is not smeared.
 */
public final class MoneyText {
    /** Extra GUI pixels between letters of the currency word, and between a number and the emerald icon. */
    public static final int UNIT_GAP = 2;
    /** Vanilla emerald item, same height as tablet body text. */
    public static final int ICON = 8;

    public enum Face {
        DEFAULT(false, false),
        PAPER(true, false),
        BOLD(false, true),
        PAPER_BOLD(true, true);

        private final boolean paper;
        private final boolean bold;

        Face(boolean paper, boolean bold) {
            this.paper = paper;
            this.bold = bold;
        }

        public boolean paper() {
            return paper;
        }

        public boolean bold() {
            return bold;
        }
    }

    private MoneyText() {
    }

    /** Without Numismatics the GUI paints an emerald item instead of the currency word. */
    public static boolean iconUnits() {
        return !MoneyScale.numismaticsBackend();
    }

    /** {@code 1} when a bold smear extends the last glyph; {@code 0} when the string ends on an item icon. */
    public static int boldExtra(String s) {
        return trailingIcon(s) ? 0 : 1;
    }

    public static int width(Font font, String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (Span span : spans(s)) {
            width += spanWidth(font, span);
        }
        return width;
    }

    public static void draw(GuiGraphics graphics, Font font, String s, int x, int y, int color, Face face) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int cursor = x;
        for (Span span : spans(s)) {
            if (!span.unit()) {
                blit(graphics, font, span.text(), cursor, y, color, face);
                cursor += font.width(span.text());
                continue;
            }
            if (iconUnits()) {
                if (span.eatSpace()) {
                    cursor += UNIT_GAP;
                }
                SpurCoinGraphics.drawEmerald(graphics, cursor, y, ICON);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                cursor += ICON;
                continue;
            }
            String text = span.text();
            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1);
                blit(graphics, font, ch, cursor, y, color, face);
                cursor += font.width(ch);
                if (i + 1 < text.length()) {
                    cursor += UNIT_GAP;
                }
            }
        }
    }

    public static String trunc(Font font, String s, int maxWidth) {
        if (s == null || s.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (width(font, s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int room = maxWidth - font.width(ellipsis);
        if (room <= 0) {
            return "";
        }
        String prefix = iconUnits() && containsUnit(s)
                ? fittingSpans(font, s, room)
                : s.substring(0, fittingPrefix(font, s, room));
        return prefix.stripTrailing() + ellipsis;
    }

    public static boolean containsUnit(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (Span span : spans(s)) {
            if (span.unit()) {
                return true;
            }
        }
        return false;
    }

    public static int lineCount(Font font, Component text, int maxWidth) {
        String plain = text.getString();
        if (!containsUnit(plain)) {
            return Math.max(1, font.split(text, Math.max(1, maxWidth)).size());
        }
        return wrap(font, plain, maxWidth).size();
    }

    /** Wrapped lines. Currency words keep their tracking or icon; other text uses {@code font.split}. */
    public static int drawWrapped(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth,
                                  int color, int lineStep) {
        String plain = text.getString();
        if (!containsUnit(plain)) {
            int drawY = y;
            for (FormattedCharSequence row : font.split(text, Math.max(1, maxWidth))) {
                graphics.drawString(font, row, x, drawY, color, false);
                drawY += lineStep;
            }
            return drawY;
        }
        int drawY = y;
        for (String row : wrap(font, plain, maxWidth)) {
            draw(graphics, font, row, x, drawY, color, Face.DEFAULT);
            drawY += lineStep;
        }
        return drawY;
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        if (iconUnits() && containsUnit(text)) {
            return wrapIcons(font, text, maxWidth);
        }
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int limit = Math.max(1, maxWidth);
        String rest = text;
        while (!rest.isEmpty()) {
            if (width(font, rest) <= limit) {
                lines.add(rest);
                break;
            }
            int cut = fittingPrefix(font, rest, limit);
            if (cut <= 0) {
                lines.add(rest.substring(0, 1));
                rest = rest.substring(1).stripLeading();
                continue;
            }
            int space = rest.lastIndexOf(' ', cut);
            if (space > 0) {
                cut = space;
            }
            String line = rest.substring(0, cut).stripTrailing();
            if (line.isEmpty()) {
                line = rest.substring(0, 1);
                rest = rest.substring(1);
            } else {
                rest = rest.substring(cut).stripLeading();
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * Icon width is not monotonic in prefix length (a complete unit is shorter than a partial word),
     * so wrap walks spans instead of cutting by character index.
     */
    private static List<String> wrapIcons(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int limit = Math.max(1, maxWidth);
        StringBuilder line = new StringBuilder();
        int lineW = 0;
        for (Span span : spans(text)) {
            if (span.unit()) {
                int w = spanWidth(font, span);
                if (lineW + w > limit && lineW > 0) {
                    lines.add(line.toString().stripTrailing());
                    line.setLength(0);
                    lineW = 0;
                }
                if (span.eatSpace() && !line.isEmpty() && line.charAt(line.length() - 1) != ' ') {
                    line.append(' ');
                }
                line.append(span.text());
                lineW += w;
                continue;
            }
            String rest = span.text();
            while (!rest.isEmpty()) {
                if (lineW + font.width(rest) <= limit) {
                    line.append(rest);
                    lineW += font.width(rest);
                    break;
                }
                int room = Math.max(0, limit - lineW);
                int cut = longestFontPrefix(font, rest, room);
                if (cut <= 0) {
                    if (lineW > 0) {
                        lines.add(line.toString().stripTrailing());
                        line.setLength(0);
                        lineW = 0;
                        continue;
                    }
                    line.append(rest.charAt(0));
                    rest = rest.substring(1);
                    lines.add(line.toString());
                    line.setLength(0);
                    lineW = 0;
                    continue;
                }
                int space = rest.lastIndexOf(' ', cut);
                if (space > 0) {
                    cut = space;
                }
                line.append(rest.substring(0, cut).stripTrailing());
                rest = rest.substring(cut).stripLeading();
                lines.add(line.toString());
                line.setLength(0);
                lineW = 0;
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String fittingSpans(Font font, String s, int maxWidth) {
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (Span span : spans(s)) {
            int w = spanWidth(font, span);
            if (used + w <= maxWidth) {
                if (span.unit() && span.eatSpace() && !out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append(span.text());
                used += w;
                continue;
            }
            if (span.unit()) {
                break;
            }
            int room = Math.max(0, maxWidth - used);
            int cut = longestFontPrefix(font, span.text(), room);
            if (cut > 0) {
                out.append(span.text(), 0, cut);
            }
            break;
        }
        return out.toString();
    }

    private static int fittingPrefix(Font font, String s, int maxWidth) {
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (width(font, s.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static int longestFontPrefix(Font font, String s, int maxWidth) {
        if (maxWidth <= 0 || s.isEmpty()) {
            return 0;
        }
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(s.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static void blit(GuiGraphics graphics, Font font, String text, int x, int y, int color, Face face) {
        Component shown = face.paper() ? WaybillFonts.ink(text) : Component.literal(text);
        graphics.drawString(font, shown, x, y, color, false);
        if (face.bold()) {
            graphics.drawString(font, shown, x + 1, y, color, false);
        }
    }

    private record Span(String text, boolean unit, boolean eatSpace) {
    }

    private static int spanWidth(Font font, Span span) {
        if (!span.unit()) {
            return font.width(span.text());
        }
        if (iconUnits()) {
            return (span.eatSpace() ? UNIT_GAP : 0) + ICON;
        }
        String text = span.text();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += font.width(text.substring(i, i + 1));
            if (i + 1 < text.length()) {
                width += UNIT_GAP;
            }
        }
        return width;
    }

    private static boolean trailingIcon(String s) {
        if (!iconUnits() || s == null || s.isEmpty()) {
            return false;
        }
        List<Span> spans = spans(s);
        return !spans.isEmpty() && spans.get(spans.size() - 1).unit();
    }

    private static List<Span> spans(String s) {
        List<String> units = units();
        List<Span> out = new ArrayList<>();
        int i = 0;
        int run = 0;
        boolean icons = iconUnits();
        while (i < s.length()) {
            String hit = matchUnit(s, i, units);
            if (hit != null) {
                boolean eatSpace = icons && i > 0 && s.charAt(i - 1) == ' ';
                int plainEnd = eatSpace ? i - 1 : i;
                if (plainEnd > run) {
                    out.add(new Span(s.substring(run, plainEnd), false, false));
                }
                out.add(new Span(hit, true, eatSpace));
                i += hit.length();
                run = i;
            } else {
                i++;
            }
        }
        if (run < s.length()) {
            out.add(new Span(s.substring(run), false, false));
        }
        return out;
    }

    /** Longest currency word first, so {@code Spurs} is not split into {@code Spur}+{@code s}. */
    private static List<String> units() {
        List<String> units = new ArrayList<>(4);
        addUnit(units, MoneyFormat.unit(2).getString());
        addUnit(units, MoneyFormat.unit(1).getString());
        addUnit(units, MoneyFormat.compactUnit().getString());
        units.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return units;
    }

    private static void addUnit(List<String> units, String unit) {
        if (unit != null && !unit.isEmpty() && !units.contains(unit)) {
            units.add(unit);
        }
    }

    /** Whole word only: {@code em} must not match inside {@code emeralds}, {@code шп} not inside {@code шпор}. */
    private static String matchUnit(String s, int index, List<String> units) {
        if (!boundary(s, index - 1)) {
            return null;
        }
        for (String unit : units) {
            int end = index + unit.length();
            if (end > s.length() || !boundary(s, end)) {
                continue;
            }
            if (s.regionMatches(true, index, unit, 0, unit.length())) {
                return s.substring(index, end);
            }
        }
        return null;
    }

    private static boolean boundary(String s, int index) {
        if (index < 0 || index >= s.length()) {
            return true;
        }
        return !Character.isLetter(s.charAt(index));
    }
}
