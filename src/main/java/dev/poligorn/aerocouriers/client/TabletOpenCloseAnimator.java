package dev.poligorn.aerocouriers.client;

import net.minecraft.Util;

/** Slide/fade open and close for brass tablet screens. */
public final class TabletOpenCloseAnimator {
    public static final long OPEN_MS = 150L;
    public static final long CLOSE_MS = 120L;
    public static final float SLIDE_PX = 8.0F;

    private final boolean skipOpen;
    private final long openedAt = Util.getMillis();
    private boolean closing;
    private long closingSince;
    private boolean forceImmediate;
    private Runnable pendingClose;

    public TabletOpenCloseAnimator(boolean skipOpen) {
        this.skipOpen = skipOpen;
    }

    public float translateY() {
        if (!TabletAnim.enabled()) {
            return 0.0F;
        }
        float p = openness();
        return SLIDE_PX * (1.0F - p);
    }

    public float alpha() {
        if (!TabletAnim.enabled()) {
            return 1.0F;
        }
        return openness();
    }

    private float openness() {
        if (closing) {
            float p = TabletAnim.progress(closingSince, CLOSE_MS);
            return 1.0F - p;
        }
        if (skipOpen) {
            return 1.0F;
        }
        return TabletAnim.progress(openedAt, OPEN_MS);
    }

    /**
     * @return {@code true} if {@code closeAction} ran immediately; {@code false} if a close tween was started.
     */
    public boolean requestClose(Runnable closeAction) {
        if (!TabletAnim.enabled() || forceImmediate) {
            closeAction.run();
            return true;
        }
        if (!closing) {
            closing = true;
            closingSince = Util.getMillis();
            pendingClose = closeAction;
            return false;
        }
        forceImmediate = true;
        pendingClose = null;
        closeAction.run();
        return true;
    }

    public void tickClose() {
        if (!closing || pendingClose == null || forceImmediate) {
            return;
        }
        if (Util.getMillis() - closingSince >= CLOSE_MS) {
            Runnable action = pendingClose;
            pendingClose = null;
            action.run();
        }
    }

    public boolean isClosing() {
        return closing && pendingClose != null;
    }
}
