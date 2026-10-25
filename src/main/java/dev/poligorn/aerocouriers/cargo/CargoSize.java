package dev.poligorn.aerocouriers.cargo;

/**
 * Container sizes (GDD §8.1). L is a multi-block container and out of MVP scope (CLARIFICATIONS).
 */
public enum CargoSize {
    S(9, 256),
    M(18, 768),
    L(27, 2048);

    private final int slots;
    private final int maxWeight;

    CargoSize(int slots, int maxWeight) {
        this.slots = slots;
        this.maxWeight = maxWeight;
    }

    public int slots() {
        return slots;
    }

    public int maxWeight() {
        return maxWeight;
    }

    /** {@code fill = round(weight / maxWeight * 8)}, clamped to 0..8; any non-zero cargo gives at least 1. */
    public int fillFor(double weight) {
        if (weight <= 0) {
            return 0;
        }
        int fill = (int) Math.round(weight / maxWeight * CargoContainerBlock.MAX_FILL);
        return Math.max(1, Math.min(CargoContainerBlock.MAX_FILL, fill));
    }
}
