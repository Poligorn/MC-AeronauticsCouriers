package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.license.LicenseRank;
import net.minecraft.Util;

/** Smooth XP fill and rank-up flash for the profile tab. */
public final class ProfileXpBarEffects {
    private static final long XP_MS = 600L;
    private static final long FLASH_MS = 300L;

    private boolean initialized;
    private float displayedFraction;
    private float animFrom;
    private float animTo;
    private long animStart = Long.MIN_VALUE;
    private LicenseRank lastRank = LicenseRank.RANK1;
    private long rankFlashStart = Long.MIN_VALUE;

    public void onProfile(LicenseRank rank, float targetFraction) {
        if (!initialized) {
            initialized = true;
            displayedFraction = targetFraction;
            lastRank = rank;
            return;
        }
        if (rank.level() > lastRank.level()) {
            rankFlashStart = Util.getMillis();
        }
        lastRank = rank;
        if (animStart != Long.MIN_VALUE && TabletAnim.progress(animStart, XP_MS) < 1.0F) {
            animTo = targetFraction;
            return;
        }
        if (Math.abs(targetFraction - displayedFraction) < 0.0001F) {
            displayedFraction = targetFraction;
            return;
        }
        animFrom = displayedFraction;
        animTo = targetFraction;
        animStart = Util.getMillis();
    }

    public float displayedFraction(float targetFraction) {
        if (!TabletAnim.enabled()) {
            return targetFraction;
        }
        if (animStart == Long.MIN_VALUE) {
            return displayedFraction;
        }
        float p = TabletAnim.progress(animStart, XP_MS);
        float value = TabletAnim.lerp(animFrom, animTo, p);
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
}
