package dev.poligorn.aerocouriers.license;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import java.util.Optional;

/** Aggregated issuer (customer) stats; updated when a player creates a P2P order. */
public record IssuerStats(
        int orders,
        long rewardSum,
        long weightTenths,
        int countS,
        int countM,
        int countL,
        int countXl
) {
    public static final IssuerStats EMPTY = new IssuerStats(0, 0L, 0L, 0, 0, 0, 0);

    public static final Codec<IssuerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("orders", 0).forGetter(IssuerStats::orders),
            Codec.LONG.optionalFieldOf("reward_sum", 0L).forGetter(IssuerStats::rewardSum),
            Codec.LONG.optionalFieldOf("weight_tenths", 0L).forGetter(IssuerStats::weightTenths),
            Codec.INT.optionalFieldOf("count_s", 0).forGetter(IssuerStats::countS),
            Codec.INT.optionalFieldOf("count_m", 0).forGetter(IssuerStats::countM),
            Codec.INT.optionalFieldOf("count_l", 0).forGetter(IssuerStats::countL),
            Codec.INT.optionalFieldOf("count_xl", 0).forGetter(IssuerStats::countXl)
    ).apply(instance, IssuerStats::new));

    public IssuerStats withOrder(CargoSize size, long reward, double weight) {
        long wt = Math.max(0L, Math.round(weight * 10.0));
        return new IssuerStats(
                orders + 1,
                rewardSum + Math.max(0L, reward),
                weightTenths + wt,
                countS + (size == CargoSize.S ? 1 : 0),
                countM + (size == CargoSize.M ? 1 : 0),
                countL + (size == CargoSize.L ? 1 : 0),
                countXl + (size == CargoSize.XL ? 1 : 0));
    }

    public int averageReward() {
        return orders <= 0 ? 0 : (int) (rewardSum / orders);
    }

    public int averageWeight() {
        return orders <= 0 ? 0 : (int) (weightTenths / orders / 10L);
    }

    public Optional<CargoSize> mostCommonSize() {
        if (orders <= 0) {
            return Optional.empty();
        }
        int best = countS;
        CargoSize size = CargoSize.S;
        if (countM > best) {
            best = countM;
            size = CargoSize.M;
        }
        if (countL > best) {
            best = countL;
            size = CargoSize.L;
        }
        if (countXl > best) {
            size = CargoSize.XL;
        }
        return Optional.of(size);
    }
}
