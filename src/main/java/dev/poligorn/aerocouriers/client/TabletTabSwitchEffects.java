package dev.poligorn.aerocouriers.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** Tab highlight slide and content fade on the order board. */
public final class TabletTabSwitchEffects {
    private static final long HIGHLIGHT_MS = 150L;
    private static final long CONTENT_MS = 120L;
    private static final int HIGHLIGHT_COLOR = 0xFFFFAF55;

    private long switchAt = Long.MIN_VALUE;
    private float fromX;
    private float fromW;
    private float toX;
    private float toW;
    private boolean hasFrom;

    public void onTabSwitch(AbstractWidget previous, AbstractWidget next) {
        if (previous != null) {
            fromX = previous.getX();
            fromW = previous.getWidth();
            hasFrom = true;
        } else {
            hasFrom = false;
        }
        toX = next.getX();
        toW = next.getWidth();
        switchAt = Util.getMillis();
    }

    public float contentAlpha() {
        if (switchAt == Long.MIN_VALUE) {
            return 1.0F;
        }
        return TabletAnim.progress(switchAt, CONTENT_MS);
    }

    public void drawHighlight(GuiGraphics graphics, int tabTop, int tabHeight) {
        if (!TabletAnim.enabled() || switchAt == Long.MIN_VALUE) {
            return;
        }
        float p = TabletAnim.progress(switchAt, HIGHLIGHT_MS);
        float x = hasFrom ? TabletAnim.lerp(fromX, toX, p) : toX;
        float w = hasFrom ? TabletAnim.lerp(fromW, toW, p) : toW;
        if (w <= 0.0F) {
            return;
        }
        int y = tabTop + tabHeight - 2;
        graphics.pose().pushPose();
        graphics.pose().translate(x, 0.0F, 0.0F);
        graphics.fill(0, y, (int) w, y + 2, HIGHLIGHT_COLOR);
        graphics.pose().popPose();
    }
}
