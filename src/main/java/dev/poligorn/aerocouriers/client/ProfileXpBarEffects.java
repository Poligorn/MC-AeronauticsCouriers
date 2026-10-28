package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.license.LicenseRank;
import net.minecraft.Util;

/** Smooth XP fill and rank-up flash for the profile tab. */
public final class ProfileXpBarEffects {
    private static final long XP_MS = 200L;
    private static final long FLASH_MS = 300L;
    private static final float EPS = 0.0001F;

    private float displayedFraction;
    private float animFrom;
    private float animTo;
    private long animStart = Long.MIN_VALUE;
    private LicenseRank lastRank = LicenseRank.RANK1;
    private long rankFlashStart = Long.MIN_VALUE;
    private int lastXp = Integer.MIN_VALUE;

    /** Ease-out fill from empty when the profile tab is opened. */
    public void onProfileTabShown(int xp, float targetFraction) {
        lastXp = xp;
        displayedFraction = 0.0F;
        animFrom = 0.0F;
        animTo = targetFraction;
        animStart = TabletAnim.enabled() ? Util.getMillis() : Long.MIN_VALUE;
        if (!TabletAnim.enabled()) {
            displayedFraction = targetFraction;
        }
    }

    public void onProfile(LicenseRank rank, float targetFraction, int xp) {
        if (rank.level() > lastRank.level()) {
            rankFlashStart = Util.getMillis();
        }
        lastRank = rank;
        commitAnimationIfFinished();

        if (xp == lastXp && animStart == Long.MIN_VALUE
                && Math.abs(targetFraction - displayedFraction) < EPS) {
            return;
        }
        lastXp = xp;

        if (animStart != Long.MIN_VALUE) {
            if (Math.abs(targetFraction - animTo) < EPS) {
                return;
            }
            displayedFraction = interpolatedValue();
            animStart = Long.MIN_VALUE;
        }

        if (Math.abs(targetFraction - displayedFraction) < EPS) {
            displayedFraction = targetFraction;
            return;
        }

        animFrom = displayedFraction;
        animTo = targetFraction;
        animStart = TabletAnim.enabled() ? Util.getMillis() : Long.MIN_VALUE;
        if (!TabletAnim.enabled()) {
            displayedFraction = targetFraction;
            animStart = Long.MIN_VALUE;
        }
    }

    public float displayedFraction(float targetFraction) {
        if (!TabletAnim.enabled()) {
            displayedFraction = targetFraction;
            animStart = Long.MIN_VALUE;
            return targetFraction;
        }
        if (animStart == Long.MIN_VALUE) {
            return displayedFraction;
        }
        float value = interpolatedValue();
        float p = TabletAnim.progress(animStart, XP_MS);
        if (p >= 1.0F) {
            displayedFraction = animTo;
            animStart = Long.MIN_VALUE;
            return animTo;
        }
        return value;
    }

    public float rankFlashStrength() {
        if (!TabletAnim.enabled() || rankFlashStart == Long.MIN_VALUE) {
            return 0.0F;
        }
        float p = TabletAnim.progress(rankFlashStart, FLASH_MS);
        if (p >= 1.0F) {
            rankFlashStart = Long.MIN_VALUE;
            return 0.0F;
        }
        return 1.0F - p;
    }

    private void commitAnimationIfFinished() {
        if (animStart == Long.MIN_VALUE) {
            return;
        }
        if (TabletAnim.progress(animStart, XP_MS) >= 1.0F) {
            displayedFraction = animTo;
            animStart = Long.MIN_VALUE;
        }
    }

    private float interpolatedValue() {
        float p = TabletAnim.progress(animStart, XP_MS);
        return TabletAnim.lerp(animFrom, animTo, p);
    }
}
