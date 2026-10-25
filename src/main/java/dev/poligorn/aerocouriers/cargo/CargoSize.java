package dev.poligorn.aerocouriers.cargo;

import com.mojang.serialization.Codec;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * Container sizes (GDD §8.1). L is a multi-block container and out of MVP scope (CLARIFICATIONS); M has no block
 * yet, so only sizes with {@link #hasBlock()} are offered to contracts.
 */
public enum CargoSize implements StringRepresentable {
    S(9, 256, true),
    M(18, 768, false),
    L(27, 2048, false);

    public static final Codec<CargoSize> CODEC = StringRepresentable.fromEnum(CargoSize::values);

    private final int slots;
    private final int maxWeight;
    private final boolean hasBlock;

    CargoSize(int slots, int maxWeight, boolean hasBlock) {
        this.slots = slots;
        this.maxWeight = maxWeight;
        this.hasBlock = hasBlock;
    }

    public boolean hasBlock() {
        return hasBlock;
    }

    public int slots() {
        return slots;
    }

    public int maxWeight() {
        return maxWeight;
    }

    /** Smallest container that holds {@code stacks} stacks of total {@code weight}. */
    public static Optional<CargoSize> smallestFor(int stacks, double weight) {
        for (CargoSize size : values()) {
            if (size.hasBlock && stacks <= size.slots && weight <= size.maxWeight) {
                return Optional.of(size);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
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
