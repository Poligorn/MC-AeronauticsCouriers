package dev.poligorn.aerocouriers.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Shrinks a screen that was laid out for 640×360 (GUI scale 3 at 1920×1080) so the whole window, frame included,
 * fits. Larger screens keep their own size: {@link #scale} stays 1 and mouse coordinates are unchanged.
 */
public final class ScreenFit {
    public static final int DESIGN_W = 640;
    public static final int DESIGN_H = 360;

    public final int layoutW;
    public final int layoutH;
    public final double scale;
    public final double fitX;
    public final double fitY;

    private ScreenFit(int layoutW, int layoutH, double scale, double fitX, double fitY) {
        this.layoutW = layoutW;
        this.layoutH = layoutH;
        this.scale = scale;
        this.fitX = fitX;
        this.fitY = fitY;
    }

    public static ScreenFit of(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) {
            return new ScreenFit(Math.max(1, screenW), Math.max(1, screenH), 1.0, 0.0, 0.0);
        }
        double fit = Math.min(screenW / (double) DESIGN_W, screenH / (double) DESIGN_H);
        if (fit >= 1.0) {
            return new ScreenFit(screenW, screenH, 1.0, 0.0, 0.0);
        }
        return new ScreenFit(DESIGN_W, DESIGN_H, fit,
                (screenW - DESIGN_W * fit) / 2.0,
                (screenH - DESIGN_H * fit) / 2.0);
    }

    public boolean shrinking() {
        return scale < 1.0;
    }

    public void apply(GuiGraphics graphics) {
        if (!shrinking()) {
            return;
        }
        float s = (float) scale;
        graphics.pose().translate(fitX, fitY, 0.0);
        graphics.pose().scale(s, s, 1.0F);
    }

    public double localX(double screenX) {
        return shrinking() ? (screenX - fitX) / scale : screenX;
    }

    public double localY(double screenY) {
        return shrinking() ? (screenY - fitY) / scale : screenY;
    }

    public int localXi(double screenX) {
        return (int) Math.floor(localX(screenX));
    }

    public int localYi(double screenY) {
        return (int) Math.floor(localY(screenY));
    }
}
