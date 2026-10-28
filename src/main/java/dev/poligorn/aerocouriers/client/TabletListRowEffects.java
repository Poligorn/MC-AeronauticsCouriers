package dev.poligorn.aerocouriers.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;

/** Hover fade and staggered row entrance for tablet lists. */
public final class TabletListRowEffects {
    private static final long HOVER_MS = 100L;
    private static final long ROW_ENTER_MS = 120L;
    private static final long ROW_STAGGER_MS = 20L;
    private static final long ROW_STAGGER_CAP_MS = 200L;
    private static final float ROW_SLIDE_PX = 4.0F;

    private String populationKey = "";
    private long populationAt;
    private int populationRows;
    private final Map<Integer, HoverChannel> hoverByRow = new HashMap<>();

    public void onListPopulated(String key, int visibleRows) {
        if (key == null) {
            key = "";
        }
        if (!key.equals(populationKey)) {
            populationKey = key;
            populationAt = Util.getMillis();
            populationRows = visibleRows;
            hoverByRow.clear();
        }
    }

    public float hoverStrength(int rowIndex, boolean hovered) {
        if (!TabletAnim.enabled()) {
            return hovered ? 1.0F : 0.0F;
        }
        HoverChannel channel = hoverByRow.computeIfAbsent(rowIndex, ignored -> new HoverChannel());
        return channel.sample(hovered);
    }

    public float rowSlideX(int rowIndex) {
        if (!TabletAnim.enabled()) {
            return 0.0F;
        }
        float p = rowEnterProgress(rowIndex);
        return ROW_SLIDE_PX * (1.0F - p);
    }

    public float rowAlpha(int rowIndex) {
        if (!TabletAnim.enabled()) {
            return 1.0F;
        }
        return rowEnterProgress(rowIndex);
    }

    private float rowEnterProgress(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= populationRows) {
            return 1.0F;
        }
        long delay = Math.min(rowIndex * ROW_STAGGER_MS, ROW_STAGGER_CAP_MS);
        return TabletAnim.progress(populationAt + delay, ROW_ENTER_MS);
    }

    private static final class HoverChannel {
        private boolean target;
        private float displayed;
        private float from;
        private long changedAt = Util.getMillis();

        float sample(boolean hovered) {
            if (hovered != target) {
                target = hovered;
                from = displayed;
                changedAt = Util.getMillis();
            }
            float goal = target ? 1.0F : 0.0F;
            float p = TabletAnim.progress(changedAt, HOVER_MS);
            displayed = TabletAnim.lerp(from, goal, p);
            return displayed;
        }
    }
}
