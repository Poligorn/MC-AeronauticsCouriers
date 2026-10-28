package dev.poligorn.aerocouriers.economy;

import java.util.ArrayList;
import java.util.List;

/** Greedy decomposition of a spur total into Numismatics denominations. */
public record SpurBreakdown(long total, List<Line> lines) {
    public record Line(SpurDenomination denomination, int count) {
    }

    public static SpurBreakdown greedy(long spurs) {
        long clamped = Math.max(0, spurs);
        if (clamped > Integer.MAX_VALUE) {
            clamped = Integer.MAX_VALUE;
        }
        int remaining = (int) clamped;
        List<Line> lines = new ArrayList<>();
        for (SpurDenomination denom : SpurDenomination.HIGH_TO_LOW) {
            if (remaining < denom.value()) {
                continue;
            }
            int count = remaining / denom.value();
            if (count <= 0) {
                continue;
            }
            remaining -= count * denom.value();
            lines.add(new Line(denom, count));
        }
        return new SpurBreakdown(clamped, List.copyOf(lines));
    }
}
