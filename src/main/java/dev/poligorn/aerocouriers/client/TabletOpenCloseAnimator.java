package dev.poligorn.aerocouriers.client;

import net.minecraft.Util;
import net.minecraft.util.Mth;

/** Slide open/close for brass tablet screens (full-height entry from below the screen). */
public final class TabletOpenCloseAnimator {
    public static final long OPEN_MS = 250L;
    public static final long CLOSE_MS = 180L;
    private static final float OVERSHOOT_PX = 4.0F;
    private static final float HIDDEN_MARGIN_PX = 6.0F;

    private final boolean skipOpen;
    private final long openedAt = Util.getMillis();
    private boolean closing;
    private long closingSince;
    private Runnable pendingClose;

    public TabletOpenCloseAnimator(boolean skipOpen) {
        this.skipOpen = skipOpen;
    }

    public float translateY(float partialTick, int screenHeight, int frameTop, int frameHeight) {
        if (!TabletAnim.enabled()) {
            return 0.0F;
        }
        float hidden = hiddenOffset(screenHeight, frameTop, frameHeight);
        float p = rawProgress(partialTick);
        if (closing) {
            float eased = TabletAnim.easeInCubic(p);
            return hidden * eased;
        }
        if (skipOpen) {
            return 0.0F;
        }
        float eased = TabletAnim.easeOutCubic(p);
        float base = hidden * (1.0F - eased);
        float overshoot = p >= 1.0F ? 0.0F : -OVERSHOOT_PX * Mth.sin(p * (float) Math.PI);
        return base + overshoot;
    }

    public float backdropAlpha(float partialTick) {
        if (!TabletAnim.enabled()) {
            return 1.0F;
        }
        float p = rawProgress(partialTick);
        if (closing) {
            return 1.0F - TabletAnim.easeInCubic(p);
        }
        if (skipOpen) {
            return 1.0F;
        }
        return TabletAnim.easeOutCubic(p);
    }

    public boolean isAnimating(float partialTick) {
        if (!TabletAnim.enabled() || skipOpen) {
            return closing;
        }
        return closing || rawProgress(partialTick) < 1.0F;
    }

    /** Slide-in from below after the screen opens (not tab switches). */
    public boolean isOpenSlideActive(float partialTick) {
        if (!TabletAnim.enabled() || skipOpen || closing) {
            return false;
        }
        return rawProgress(partialTick) < 1.0F;
    }

    private float rawProgress(float partialTick) {
        if (closing) {
            float elapsed = elapsedMs(closingSince, partialTick);
            return Mth.clamp(elapsed / CLOSE_MS, 0.0F, 1.0F);
        }
        if (skipOpen) {
            return 1.0F;
        }
        float elapsed = elapsedMs(openedAt, partialTick);
        return Mth.clamp(elapsed / OPEN_MS, 0.0F, 1.0F);
    }

    private static float elapsedMs(long since, float partialTick) {
        return Util.getMillis() - since + partialTick;
    }

    private static float hiddenOffset(int screenHeight, int frameTop, int frameHeight) {
        int blockTop = frameTop - TabletGui.antennaOverhangTop();
        return screenHeight - blockTop + HIDDEN_MARGIN_PX;
    }

    /**
     * @return {@code true} if {@code closeAction} ran immediately; {@code false} if a close tween was started.
     */
    public boolean requestClose(Runnable closeAction) {
        if (!TabletAnim.enabled()) {
            closeAction.run();
            return true;
        }
        if (closing) {
            return false;
        }
        closing = true;
        closingSince = Util.getMillis();
        pendingClose = closeAction;
        return false;
    }

    public void tickClose() {
        if (!closing || pendingClose == null) {
            return;
        }
        if (Util.getMillis() - closingSince >= CLOSE_MS) {
            Runnable action = pendingClose;
            pendingClose = null;
            action.run();
        }
    }

    public boolean isClosing() {
        return closing;
    }
}
