package dev.poligorn.aerocouriers.client;

import net.minecraft.util.Mth;

/**
 * Layout for the profile-tab player preview (inventory entity renderer).
 * Feet sit {@link #FEET_MARGIN_FRAC} above the panel bottom; body height is {@link #BODY_HEIGHT_FRAC} of the panel.
 */
final class ProfileSkinPreviewLayout {
    /** Full on-screen height of an upright player at scale 1 (blocks; slightly above {@code bbHeight}). */
    static final float PLAYER_VISUAL_HEIGHT_BLOCKS = 2.05F;
    static final float BODY_HEIGHT_FRAC = 0.85F;
    static final float FEET_MARGIN_FRAC = 0.06F;
    static final float HEAD_MARGIN_FRAC = 0.07F;
    private static final int MIN_SCALE = 28;
    private static final int MAX_SCALE = 220;

    record Layout(int scale, float screenX, float screenY, int estimatedHeadTop, int estimatedFeetBottom) {
    }

    private ProfileSkinPreviewLayout() {
    }

    static Layout forPanel(int panelLeft, int panelTop, int panelWidth, int panelHeight, float entityScale) {
        int boxH = Math.max(48, panelHeight);
        float targetBodyPx = boxH * BODY_HEIGHT_FRAC;
        int scale = Mth.clamp(Math.round(targetBodyPx / PLAYER_VISUAL_HEIGHT_BLOCKS), MIN_SCALE, MAX_SCALE);
        float renderScale = scale / entityScale;
        float cx = panelLeft + panelWidth / 2.0F;
        float feetY = panelTop + boxH * (1.0F - FEET_MARGIN_FRAC);
        int headTop = Math.round(feetY - renderScale * PLAYER_VISUAL_HEIGHT_BLOCKS);
        int feetBottom = Math.round(feetY);
        return new Layout(scale, cx, feetY, headTop, feetBottom);
    }

    static boolean marginsOk(int panelTop, int panelHeight, Layout layout) {
        int boxH = Math.max(48, panelHeight);
        int headMargin = layout.estimatedHeadTop() - panelTop;
        int feetMargin = panelTop + boxH - layout.estimatedFeetBottom();
        return headMargin >= Math.round(boxH * HEAD_MARGIN_FRAC)
                && feetMargin >= Math.round(boxH * FEET_MARGIN_FRAC);
    }
}
