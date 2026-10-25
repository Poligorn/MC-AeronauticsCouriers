package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Immutable contract (GDD §5.1). The manifest lives in {@link Escrow}, never here or in the container block.
 * Times are overworld game ticks, so contracts do not tick down while the server is stopped.
 *
 * @param issuer    for {@link ContractType#PLAYER} the player UUID; village issuers arrive in iteration 5
 * @param recipient who receives the cargo in the mailbox (defaults to the issuer)
 * @param weight    total cargo weight (GDD §8.2), the {@code W} of the XP formula
 */
public record Contract(UUID id, ContractType type, UUID issuer, UUID recipient, Optional<UUID> courier,
                       Destination origin, Destination destination, CargoSize size, double weight,
                       long reward, long collateral, LicenseRank requiredRank, Timing timing, ContractState state) {

    public static final Codec<Contract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Contract::id),
            ContractType.CODEC.fieldOf("type").forGetter(Contract::type),
            UUIDUtil.CODEC.fieldOf("issuer").forGetter(Contract::issuer),
            UUIDUtil.CODEC.fieldOf("recipient").forGetter(Contract::recipient),
            UUIDUtil.CODEC.optionalFieldOf("courier").forGetter(Contract::courier),
            Destination.CODEC.fieldOf("origin").forGetter(Contract::origin),
            Destination.CODEC.fieldOf("destination").forGetter(Contract::destination),
            CargoSize.CODEC.fieldOf("size").forGetter(Contract::size),
            Codec.DOUBLE.fieldOf("weight").forGetter(Contract::weight),
            Codec.LONG.fieldOf("reward").forGetter(Contract::reward),
            Codec.LONG.fieldOf("collateral").forGetter(Contract::collateral),
            LicenseRank.CODEC.fieldOf("required_rank").forGetter(Contract::requiredRank),
            Timing.CODEC.fieldOf("timing").forGetter(Contract::timing),
            ContractState.CODEC.fieldOf("state").forGetter(Contract::state)
    ).apply(instance, Contract::new));

    /**
     * @param deadline   time allowed after acceptance, ticks
     * @param acceptedAt game time of acceptance, {@code -1} while open
     */
    public record Timing(long createdAt, long expiresAt, long deadline, long acceptedAt) {
        public static final Codec<Timing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("created_at").forGetter(Timing::createdAt),
                Codec.LONG.fieldOf("expires_at").forGetter(Timing::expiresAt),
                Codec.LONG.fieldOf("deadline").forGetter(Timing::deadline),
                Codec.LONG.optionalFieldOf("accepted_at", -1L).forGetter(Timing::acceptedAt)
        ).apply(instance, Timing::new));

        public long dueAt() {
            return acceptedAt + deadline;
        }

        /** End of the late window (GDD §9.6); after it the contract fails. */
        public long lateUntil(double lateWindow) {
            return acceptedAt + deadline + (long) Math.floor(deadline * lateWindow);
        }
    }

    public double distance() {
        return origin.distanceTo(destination);
    }

    public boolean isCourier(UUID player) {
        return courier.filter(player::equals).isPresent();
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    Contract withState(ContractState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Contract " + id + ": " + state + " -> " + next);
        }
        return new Contract(id, type, issuer, recipient, courier, origin, destination, size, weight, reward,
                collateral, requiredRank, timing, next);
    }

    Contract accepted(UUID courierId, long now) {
        Contract next = withState(ContractState.ACCEPTED);
        return new Contract(id, type, issuer, recipient, Optional.of(courierId), origin, destination, size, weight,
                reward, collateral, requiredRank,
                new Timing(timing.createdAt(), timing.expiresAt(), timing.deadline(), now), next.state);
    }
}
