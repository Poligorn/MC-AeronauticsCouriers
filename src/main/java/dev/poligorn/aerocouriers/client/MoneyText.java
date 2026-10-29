package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.economy.MoneyFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The only drawer for strings that contain a money amount from {@link MoneyFormat}.
 * The currency word is painted one letter at a time with {@link #UNIT_GAP} between letters.
 * Every other word uses the vanilla advance. Layout must use {@link #width}, not {@code Font#width}.
 * Bold is per letter: a second copy one pixel to the right must not fill the tracking gap,
 * which a whole-string offset does when the gap is only one pixel.
 */
public final class MoneyText {
    /** Extra GUI pixels between letters of the currency word. Two, so a 1 px bold smear still leaves a gap. */
    public static final int UNIT_GAP = 2;

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

    public static int width(Font font, String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (Span span : spans(s)) {
            if (!span.unit()) {
                width += font.width(span.text());
                continue;
            }
            String text = span.text();
            for (int i = 0; i < text.length(); i++) {
                width += font.width(text.substring(i, i + 1));
                if (i + 1 < text.length()) {
                    width += UNIT_GAP;
                }
            }
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
        return s.substring(0, fittingPrefix(font, s, room)).stripTrailing() + ellipsis;
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

    /** Wrapped lines. Currency words keep their tracking; other text uses {@code font.split}. */
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

    private static void blit(GuiGraphics graphics, Font font, String text, int x, int y, int color, Face face) {
        Component shown = face.paper() ? WaybillFonts.ink(text) : Component.literal(text);
        graphics.drawString(font, shown, x, y, color, false);
        if (face.bold()) {
            graphics.drawString(font, shown, x + 1, y, color, false);
        }
    }

    private record Span(String text, boolean unit) {
    }

    private static List<Span> spans(String s) {
        List<String> units = units();
        List<Span> out = new ArrayList<>();
        int i = 0;
        int run = 0;
        while (i < s.length()) {
            String hit = matchUnit(s, i, units);
            if (hit != null) {
                if (i > run) {
                    out.add(new Span(s.substring(run, i), false));
                }
                out.add(new Span(hit, true));
                i += hit.length();
                run = i;
            } else {
                i++;
            }
        }
        if (run < s.length()) {
            out.add(new Span(s.substring(run), false));
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
