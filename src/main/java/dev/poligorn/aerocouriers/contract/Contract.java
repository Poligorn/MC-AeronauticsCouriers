package dev.poligorn.aerocouriers.contract;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.PairMapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.WaybillFacing;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;

/**
 * Immutable contract (GDD §5.1). The manifest lives in {@link Escrow}, never here or in the container block.
 * Times are overworld game ticks, so contracts do not tick down while the server is stopped.
 *
 * @param issuer    player UUID for {@link ContractType#PLAYER}; station UUID for {@link ContractType#VILLAGE}
 * @param recipient player who gets delivery notices (defaults to the issuer); village orders use the destination station id
 * @param weight    total cargo weight (GDD §8.2), the {@code W} of the XP formula
 * @param preplaced {@code true} when the cargo container already stands at the pickup point (stamp orders)
 * @param originSnapshot block(s) that held the cargo at creation; restored at P2P delivery
 * @param waybillFace world-horizontal side the paper waybill faces; frozen at publish, never recomputed
 */
public record Contract(UUID id, ContractType type, UUID issuer, UUID recipient, Optional<UUID> courier,
                       Destination origin, Destination destination, CargoSize size, double weight,
                       long reward, long collateral, LicenseRank requiredRank, Timing timing, ContractState state,
                       boolean preplaced, Optional<OriginContainerSnapshot> originSnapshot, Direction waybillFace) {

    public Contract {
        if (waybillFace == null || !waybillFace.getAxis().isHorizontal()) {
            waybillFace = WaybillFacing.DEFAULT_FACE;
        }
    }

    /** DFU {@code group} maxes out at 16 fields; the sheet face is appended with {@link MapCodec#and}. */
    private static final MapCodec<Contract> BASE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
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
            ContractState.CODEC.fieldOf("state").forGetter(Contract::state),
            Codec.BOOL.optionalFieldOf("preplaced", false).forGetter(Contract::preplaced),
            OriginContainerSnapshot.CODEC.optionalFieldOf("origin_snapshot").forGetter(Contract::originSnapshot)
    ).apply(instance, Contract::fromBase));

    public static final Codec<Contract> CODEC = new PairMapCodec<>(
                    BASE_CODEC, Direction.CODEC.optionalFieldOf("waybill_face", WaybillFacing.DEFAULT_FACE))
            .xmap(pair -> pair.getFirst().withWaybillFace(pair.getSecond()),
                    contract -> Pair.of(contract, contract.waybillFace()))
            .codec();

    private static Contract fromBase(UUID id, ContractType type, UUID issuer, UUID recipient, Optional<UUID> courier,
                                     Destination origin, Destination destination, CargoSize size, double weight,
                                     long reward, long collateral, LicenseRank requiredRank, Timing timing,
                                     ContractState state, boolean preplaced,
                                     Optional<OriginContainerSnapshot> originSnapshot) {
        return new Contract(id, type, issuer, recipient, courier, origin, destination, size, weight, reward,
                collateral, requiredRank, timing, state, preplaced, originSnapshot, WaybillFacing.DEFAULT_FACE);
    }

    /**
     * @param deadline   time allowed after acceptance, ticks
     * @param acceptedAt game time of acceptance, {@code -1} while open
     * @param paused     ticks the deadline was on hold because the pickup point was blocked (GDD §12)
     */
    public record Timing(long createdAt, long expiresAt, long deadline, long acceptedAt, long paused) {
        public static final Codec<Timing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("created_at").forGetter(Timing::createdAt),
                Codec.LONG.fieldOf("expires_at").forGetter(Timing::expiresAt),
                Codec.LONG.fieldOf("deadline").forGetter(Timing::deadline),
                Codec.LONG.optionalFieldOf("accepted_at", -1L).forGetter(Timing::acceptedAt),
                Codec.LONG.optionalFieldOf("paused", 0L).forGetter(Timing::paused)
        ).apply(instance, Timing::new));

        public long dueAt() {
            return acceptedAt + paused + deadline;
        }

        /** End of the late window (GDD §9.6); after it the contract fails. */
        public long lateUntil(double lateWindow) {
            return dueAt() + (long) Math.floor(deadline * lateWindow);
        }
    }

    public double distance() {
        return ContractDistances.pricingBlocks(origin, destination);
    }

    public boolean crossDimension() {
        return ContractDistances.isCrossDimension(origin, destination);
    }

    public boolean isCourier(UUID player) {
        return courier.filter(player::equals).isPresent();
    }

    /**
     * Board / list role. A self-accepted job (issuer == courier) is shown as {@code courier} while the
     * deadline is running so the slot can be abandoned; an unpublished own order stays {@code issuer}.
     */
    public String roleFor(UUID viewer) {
        if (isCourier(viewer) && state.isActive()) {
            return "courier";
        }
        if (issuer.equals(viewer)) {
            return "issuer";
        }
        return isCourier(viewer) ? "courier" : "board";
    }

    /** Whether board packets may include pickup coordinates for this viewer (anti-pirate until accept). */
    public boolean revealsPickupTo(UUID viewer) {
        if (issuer.equals(viewer) || (type.isTutorial() && recipient.equals(viewer))) {
            return true;
        }
        return isCourier(viewer) && state != ContractState.OPEN;
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    /** Block {@code facing} that puts the paper on {@link #waybillFace()}. */
    public Direction containerFacing() {
        return WaybillFacing.toContainerFacing(waybillFace);
    }

    Contract withState(ContractState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Contract " + id + ": " + state + " -> " + next);
        }
        return copy(courier, timing, next);
    }

    Contract accepted(UUID courierId, long now) {
        if (!state.canTransitionTo(ContractState.ACCEPTED)) {
            throw new IllegalStateException("Contract " + id + ": " + state + " -> " + ContractState.ACCEPTED);
        }
        return copy(Optional.of(courierId),
                new Timing(timing.createdAt(), timing.expiresAt(), timing.deadline(), now, 0),
                ContractState.ACCEPTED);
    }

    Contract withCollateral(long nextCollateral, LicenseRank nextRank) {
        return new Contract(id, type, issuer, recipient, courier, origin, destination, size, weight, reward,
                nextCollateral, nextRank, timing, state, preplaced, originSnapshot, waybillFace);
    }

    /** Tutorial only: back to OPEN (abandon → reissue) or ACCEPTED (lost container → respawn at A). */
    Contract tutorialRewind(ContractState next, Optional<UUID> nextCourier, long acceptedAt) {
        return copy(nextCourier, new Timing(timing.createdAt(), timing.expiresAt(), timing.deadline(),
                acceptedAt, 0), next);
    }

    Contract withPause(long extraTicks) {
        return copy(courier, new Timing(timing.createdAt(), timing.expiresAt(), timing.deadline(),
                timing.acceptedAt(), timing.paused() + extraTicks), state);
    }

    Contract withTiming(Timing next) {
        return copy(courier, next, state);
    }

    Contract withWaybillFace(Direction nextFace) {
        return new Contract(id, type, issuer, recipient, courier, origin, destination, size, weight, reward,
                collateral, requiredRank, timing, state, preplaced, originSnapshot, nextFace);
    }

    private Contract copy(Optional<UUID> nextCourier, Timing nextTiming, ContractState nextState) {
        return new Contract(id, type, issuer, recipient, nextCourier, origin, destination, size, weight, reward,
                collateral, requiredRank, nextTiming, nextState, preplaced, originSnapshot, waybillFace);
    }
}
