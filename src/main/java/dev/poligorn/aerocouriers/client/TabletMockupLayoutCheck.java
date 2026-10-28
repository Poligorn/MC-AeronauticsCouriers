package dev.poligorn.aerocouriers.client;

import java.util.ArrayList;
import java.util.List;

/** Offline assert: mockup panel3()/board() rectangles (build.py) must not overlap. */
public final class TabletMockupLayoutCheck {
    private record Rect(String name, int x0, int y0, int x1, int y1) {
        boolean overlaps(Rect other) {
            return x0 < other.x1 && x1 > other.x0 && y0 < other.y1 && y1 > other.y0;
        }
    }

    private TabletMockupLayoutCheck() {
    }

    public static void verify() {
        verifyPanel3();
        verifyBoardVertical();
    }

    private static void verifyPanel3() {
        int x = 50;
        int y = 72;
        int w = 540;
        int h = 248;
        List<Rect> rects = new ArrayList<>();
        rects.add(new Rect("header", x + 1, y + 1, x + w - 2, y + 15));
        rects.add(new Rect("cargo", x + 10, y + 22, x + 10 + 180, y + 22 + 176));
        rects.add(new Rect("divider", x + 196, y + 22, x + 197, y + 198));
        int lx = x + 202;
        int vx = lx + 66;
        rects.add(new Rect("details-labels", lx, y + 22, vx - 4, y + 198));
        rects.add(new Rect("details-values", vx, y + 22, x + w - 12, y + 198));
        int closeX = x + w - 17;
        rects.add(new Rect("close", closeX, y + 2, closeX + 12, y + 13));
        rects.add(new Rect("id", closeX - 80, y + 2, closeX - 8, y + 13));
        int by = y + h - 42;
        rects.add(new Rect("map-btn", x + 10, by, x + 10 + 150, by + 20));
        rects.add(new Rect("accept-btn", x + w - 10 - 70 - 8 - 160, by, x + w - 10 - 70 - 8, by + 20));
        rects.add(new Rect("back-btn", x + w - 10 - 70, by, x + w - 10, by + 20));

        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rect a = rects.get(i);
                Rect b = rects.get(j);
                if (a.name.equals("divider") || b.name.equals("divider")) {
                    continue;
                }
                if (a.name.equals("header") || b.name.equals("header")) {
                    continue;
                }
                if ((a.name.equals("id") && b.name.equals("close")) || (a.name.equals("close") && b.name.equals("id"))) {
                    continue;
                }
                if (a.overlaps(b)) {
                    throw new IllegalStateException("Layout overlap: " + a.name + " vs " + b.name);
                }
            }
        }
        if (lx < x + 196 + 6) {
            throw new IllegalStateException("Details labels too close to divider");
        }
        if (vx < lx + 60) {
            throw new IllegalStateException("Value column too close to labels");
        }
    }

    private static void verifyBoardVertical() {
        int cy0 = 14;
        int tabH = 19;
        int statusY = cy0 + tabH + 4;
        int filterY = statusY + 9 + 11;
        int headerY = filterY + 19;
        int listY = headerY + 11;
        int footerY = listY + 3 + 28 * 6 + 4;
        if (!(statusY < filterY && filterY < headerY && headerY < listY && listY < footerY && footerY < 360 - 14)) {
            throw new IllegalStateException("Board vertical layout not monotonic");
        }
    }

    public static void main(String[] args) {
        verify();
        System.out.println("TabletMockupLayoutCheck OK");
    }
}
