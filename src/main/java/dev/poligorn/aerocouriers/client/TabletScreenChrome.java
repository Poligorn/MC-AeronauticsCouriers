package dev.poligorn.aerocouriers.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared open/close transform wrapper for brass tablet screens. */
public final class TabletScreenChrome {
    private final TabletOpenCloseAnimator openClose;

    public TabletScreenChrome(boolean skipOpenAnimation) {
        this.openClose = new TabletOpenCloseAnimator(skipOpenAnimation);
    }

    public TabletOpenCloseAnimator openClose() {
        return openClose;
    }

    public void tick() {
        openClose.tickClose();
    }

    public boolean requestClose(Runnable closeAction) {
        return openClose.requestClose(closeAction);
    }

    public void drawTabletBody(GuiGraphics graphics, Runnable draw) {
        TabletAnim.withTransform(graphics, 0.0F, openClose.translateY(), openClose.alpha(), draw);
    }

    public void drawCavityBackground(GuiGraphics graphics, int x, int y, int w, int h, float partialTick) {
        drawTabletBody(graphics, () -> TabletGui.drawScreenBackground(graphics, x, y, w, h, partialTick));
    }
}
