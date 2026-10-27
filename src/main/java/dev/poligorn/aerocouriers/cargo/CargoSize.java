package dev.poligorn.aerocouriers.cargo;

import com.mojang.serialization.Codec;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/** Container sizes (GDD §8.1). */
public enum CargoSize implements StringRepresentable {
    S,
    M,
    L,
    XL;

    public static final Codec<CargoSize> CODEC = StringRepresentable.fromEnum(CargoSize::values);

    public int slots() {
        return CouriersServerConfig.containerSlots(this);
    }

    public int maxWeight() {
        return CouriersServerConfig.containerMaxWeight(this);
    }

    public int blockCount() {
        return switch (this) {
            case S -> 1;
            case M -> 2;
            case L -> 8;
            case XL -> 12;
        };
    }

    /** Smallest container that holds {@code stacks} stacks of total {@code weight}. */
    public static Optional<CargoSize> smallestFor(int stacks, double weight) {
        for (CargoSize size : values()) {
            if (stacks <= size.slots() && weight <= size.maxWeight()) {
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
        int fill = (int) Math.round(weight / maxWeight() * CargoContainerBlock.MAX_FILL);
        return Math.max(1, Math.min(CargoContainerBlock.MAX_FILL, fill));
    }
}
