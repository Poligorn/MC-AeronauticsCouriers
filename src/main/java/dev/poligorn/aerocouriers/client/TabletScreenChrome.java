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

    public boolean showLoadingGear(boolean awaitingServerData, boolean tabSwitchLoading, float partialTick) {
        if (openClose.isClosing()) {
            return false;
        }
        if (awaitingServerData) {
            return true;
        }
        if (tabSwitchLoading) {
            return true;
        }
        return openClose.isOpenSlideActive(partialTick);
    }

    public boolean blocksInput(boolean awaitingServerData, float partialTick) {
        if (openClose.isClosing()) {
            return true;
        }
        return showLoadingGear(awaitingServerData, false, partialTick);
    }

    /** Blocks tab-panel widgets but not main tab buttons (see {@link #blocksInput}). */
    public boolean blocksTabContentInput(boolean awaitingServerData, boolean tabSwitchLoading, float partialTick) {
        if (blocksInput(awaitingServerData, partialTick)) {
            return true;
        }
        return tabSwitchLoading;
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
                                boolean awaitingServerData, boolean tabSwitchLoading, float partialTick) {
        boolean show = showLoadingGear(awaitingServerData, tabSwitchLoading, partialTick);
        boolean blocked = show && (awaitingServerData || tabSwitchLoading || openClose.isOpenSlideActive(partialTick));
        float alpha = loadingGear.alpha(blocked);
        loadingGear.render(graphics, cavityX, cavityY, cavityW, cavityH, alpha, partialTick);
    }

    /**
     * Opacity for cavity chrome during open / first snapshot wait. Tab-switch loading keeps {@code 1} so main tabs
     * stay visible; panel content uses {@link TabletTabSwitchEffects#contentAlpha()}.
     */
    public float cavityContentAlpha(boolean awaitingServerData, boolean tabSwitchLoading, float partialTick) {
        if (!TabletAnim.enabled()) {
            return 1.0F;
        }
        if (tabSwitchLoading) {
            return 1.0F;
        }
        if (awaitingServerData || openClose.isOpenSlideActive(partialTick)) {
            return 0.0F;
        }
        return 1.0F;
    }

    public void drawCavityBackground(GuiGraphics graphics, int x, int y, int w, int h, float partialTick) {
        drawTabletBody(graphics, partialTick, () -> TabletGui.drawScreenBackground(graphics, x, y, w, h, partialTick));
    }
}
