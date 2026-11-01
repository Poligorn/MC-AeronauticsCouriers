package dev.poligorn.aerocouriers.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Per-station NPC board rules. Missing NBT fields load as {@link #DEFAULT} so old worlds keep working.
 * Village rank (from reputation) is unchanged; a future village <em>level</em> is a separate feature.
 */
public record VillageBoardPolicy(
        boolean allowExpensive,
        boolean allowRare,
        int allowedSizesMask,
        int refreshIntervalMin
) {
    public static final int ALL_SIZES = 0b1111;
    public static final int DEFAULT_INTERVAL = 0;
    public static final long EXPENSIVE_REWARD = 1000L;
    public static final int NAME_MAX = 24;

    /** Letters (incl. Cyrillic), digits, space, hyphen — the charset the naming dialog promises. */
    public static boolean isAllowedNameChar(int codePoint) {
        return Character.isLetter(codePoint) || Character.isDigit(codePoint)
                || codePoint == ' ' || codePoint == '-';
    }

    public static boolean hasValidNameChars(String name) {
        return name != null && !name.isEmpty() && name.codePoints().allMatch(VillageBoardPolicy::isAllowedNameChar);
    }
    public static final int REPUTATION_MIN = 0;
    public static final int REPUTATION_MAX = 50;
    public static final VillageBoardPolicy DEFAULT = new VillageBoardPolicy(true, true, ALL_SIZES, DEFAULT_INTERVAL);

    public static final Codec<VillageBoardPolicy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("allow_expensive", true).forGetter(VillageBoardPolicy::allowExpensive),
            Codec.BOOL.optionalFieldOf("allow_rare", true).forGetter(VillageBoardPolicy::allowRare),
            Codec.INT.optionalFieldOf("allowed_sizes", ALL_SIZES).forGetter(VillageBoardPolicy::allowedSizesMask),
            Codec.INT.optionalFieldOf("refresh_interval_min", DEFAULT_INTERVAL).forGetter(VillageBoardPolicy::refreshIntervalMin)
    ).apply(instance, VillageBoardPolicy::new));

    public static int maskOf(CargoSize size) {
        return 1 << size.ordinal();
    }

    public static int clampReputation(int value) {
        return Math.max(REPUTATION_MIN, Math.min(REPUTATION_MAX, value));
    }

    public boolean allowsSize(CargoSize size) {
        return (allowedSizesMask & maskOf(size)) != 0;
    }

    public VillageBoardPolicy withExpensive(boolean value) {
        return new VillageBoardPolicy(value, allowRare, allowedSizesMask, refreshIntervalMin);
    }

    public VillageBoardPolicy withRare(boolean value) {
        return new VillageBoardPolicy(allowExpensive, value, allowedSizesMask, refreshIntervalMin);
    }

    public VillageBoardPolicy withSize(CargoSize size, boolean allowed) {
        int bit = maskOf(size);
        int next = allowed ? (allowedSizesMask | bit) : (allowedSizesMask & ~bit);
        if (next == 0) {
            next = bit;
        }
        return new VillageBoardPolicy(allowExpensive, allowRare, next, refreshIntervalMin);
    }

    public VillageBoardPolicy withSizes(int mask) {
        int next = mask & ALL_SIZES;
        return new VillageBoardPolicy(allowExpensive, allowRare, next == 0 ? ALL_SIZES : next, refreshIntervalMin);
    }

    public VillageBoardPolicy withInterval(int minutes) {
        return new VillageBoardPolicy(allowExpensive, allowRare, allowedSizesMask, Math.max(0, Math.min(1440, minutes)));
    }

    public EnumSet<CargoSize> sizes() {
        EnumSet<CargoSize> out = EnumSet.noneOf(CargoSize.class);
        for (CargoSize size : CargoSize.values()) {
            if (allowsSize(size)) {
                out.add(size);
            }
        }
        return out;
    }

    public static int maskOf(Set<CargoSize> sizes) {
        int mask = 0;
        for (CargoSize size : sizes) {
            mask |= maskOf(size);
        }
        return mask == 0 ? ALL_SIZES : mask;
    }

    public static EnumSet<CargoSize> parseSizes(String raw) {
        EnumSet<CargoSize> out = EnumSet.noneOf(CargoSize.class);
        for (String part : raw.split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            out.add(CargoSize.valueOf(part.strip().toUpperCase(Locale.ROOT)));
        }
        return out.isEmpty() ? EnumSet.allOf(CargoSize.class) : out;
    }
}
