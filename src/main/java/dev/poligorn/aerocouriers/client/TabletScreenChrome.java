package dev.poligorn.aerocouriers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Shared open/close transform wrapper for brass tablet screens. */
public final class TabletScreenChrome {
    private final TabletOpenCloseAnimator openClose;
    private final TabletLoadingGear loadingGear = new TabletLoadingGear();
    private int lastScreenH;
    private int lastFrameTop;
    private int lastFrameH;

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

    public void layout(int screenHeight, int frameTop, int frameHeight) {
        lastScreenH = screenHeight;
        lastFrameTop = frameTop;
        lastFrameH = frameHeight;
    }

    public void renderBackground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(screen, graphics, mouseX, mouseY, partialTick, openClose.backdropAlpha(partialTick));
    }

    public boolean showLoadingGear(boolean awaitingServerData, float partialTick) {
        if (openClose.isClosing()) {
            return false;
        }
        if (awaitingServerData) {
            return true;
        }
        return openClose.isOpenSlideActive(partialTick);
    }

    public boolean blocksInput(boolean awaitingServerData, float partialTick) {
        if (openClose.isClosing()) {
            return true;
        }
        return showLoadingGear(awaitingServerData, partialTick);
    }

    public float bodyTranslateY(float partialTick) {
        return openClose.translateY(partialTick, lastScreenH, lastFrameTop, lastFrameH);
    }

    public double adjustMouseY(double mouseY, float partialTick) {
        return mouseY - bodyTranslateY(partialTick);
    }

    public void drawTabletBody(GuiGraphics graphics, float partialTick, Runnable draw) {
        float translateY = bodyTranslateY(partialTick);
        TabletAnim.withTransform(graphics, 0.0F, translateY, 1.0F, draw);
    }

    public void drawLoadingGear(GuiGraphics graphics, int cavityX, int cavityY, int cavityW, int cavityH,
                                boolean awaitingServerData, float partialTick) {
        boolean show = showLoadingGear(awaitingServerData, partialTick);
        float alpha = loadingGear.alpha(show);
        loadingGear.render(graphics, cavityX, cavityY, cavityW, cavityH, alpha, partialTick);
    }

    /**
     * Opacity for cavity content (tabs, lists, text). Hidden only while awaiting the first snapshot or during the
     * open slide-in; tab switches keep {@code 1} and use {@link TabletTabSwitchEffects} only.
     */
    public float cavityContentAlpha(boolean awaitingServerData, float partialTick) {
        if (!TabletAnim.enabled()) {
            return 1.0F;
        }
        return showLoadingGear(awaitingServerData, partialTick) ? 0.0F : 1.0F;
    }

    public void drawCavityBackground(GuiGraphics graphics, int x, int y, int w, int h, float partialTick) {
        drawTabletBody(graphics, partialTick, () -> TabletGui.drawScreenBackground(graphics, x, y, w, h, partialTick));
    }
}
