package dev.poligorn.aerocouriers.license;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player courier progress (GDD §3.2). Stored as a player Data Attachment with {@code copyOnDeath}, so losing
 * or having the license item stolen never loses progress. All fields are optional in the codec to stay
 * forward-compatible with older saves.
 *
 * @param xp                  courier experience (GDD §4.1)
 * @param delivered           successfully delivered contracts (on time or late)
 * @param failed              failed contracts
 * @param late                deliveries made inside the late window (subset of {@code delivered})
 * @param distance            total straight-line distance of delivered contracts, blocks
 * @param licensesIssued      how many licenses were issued to this player (first one is free, reissue cost doubles)
 * @param migrated            whether a legacy {@code kubejs:pilot_license_*} was already migrated
 * @param villageDelivered    successful NPC / station contracts (GDD §6.5 trainee)
 * @param traineeAdvancePaid  whether the one-time trainee bonus was already granted
 * @param rewardSum           sum of spur rewards paid on successful deliveries (for board profile averages)
 * @param issuerOrders      P2P orders published by this player (updated on order creation)
 * @param issuerRewardSum   sum of rewards on those orders
 * @param issuerWeightTenths sum of cargo weight ×10 on those orders
 * @param issuerCountS      how many of those orders used each container size (for the mode)
 */
public record CourierProfile(int xp, int delivered, int failed, int late, long distance, int licensesIssued,
                             boolean migrated, int villageDelivered, boolean traineeAdvancePaid, long rewardSum,
                             int issuerOrders, long issuerRewardSum, long issuerWeightTenths, int issuerCountS,
                             int issuerCountM, int issuerCountL, int issuerCountXl) {
    public static final CourierProfile EMPTY = new CourierProfile(0, 0, 0, 0, 0L, 0, false, 0, false, 0L,
            0, 0L, 0L, 0, 0, 0, 0);

    public static final Codec<CourierProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("xp", 0).forGetter(CourierProfile::xp),
            Codec.INT.optionalFieldOf("delivered", 0).forGetter(CourierProfile::delivered),
            Codec.INT.optionalFieldOf("failed", 0).forGetter(CourierProfile::failed),
            Codec.INT.optionalFieldOf("late", 0).forGetter(CourierProfile::late),
            Codec.LONG.optionalFieldOf("distance", 0L).forGetter(CourierProfile::distance),
            Codec.INT.optionalFieldOf("licenses_issued", 0).forGetter(CourierProfile::licensesIssued),
            Codec.BOOL.optionalFieldOf("migrated", false).forGetter(CourierProfile::migrated),
            Codec.INT.optionalFieldOf("village_delivered", 0).forGetter(CourierProfile::villageDelivered),
            Codec.BOOL.optionalFieldOf("trainee_advance_paid", false).forGetter(CourierProfile::traineeAdvancePaid),
            Codec.LONG.optionalFieldOf("reward_sum", 0L).forGetter(CourierProfile::rewardSum),
            IssuerStats.CODEC.optionalFieldOf("issuer", IssuerStats.EMPTY).forGetter(CourierProfile::issuerStats)
    ).apply(instance, (xp, delivered, failed, late, distance, licensesIssued, migrated, villageDelivered,
            traineeAdvancePaid, rewardSum, issuer) -> new CourierProfile(xp, delivered, failed, late, distance,
            licensesIssued, migrated, villageDelivered, traineeAdvancePaid, rewardSum, issuer.orders(),
            issuer.rewardSum(), issuer.weightTenths(), issuer.countS(), issuer.countM(), issuer.countL(),
            issuer.countXl())));

    public IssuerStats issuerStats() {
        return new IssuerStats(issuerOrders, issuerRewardSum, issuerWeightTenths,
                issuerCountS, issuerCountM, issuerCountL, issuerCountXl);
    }

    public CourierProfile withIssuerOrder(CargoSize size, long reward, double weight) {
        IssuerStats next = issuerStats().withOrder(size, reward, weight);
        return new CourierProfile(xp, delivered, failed, late, distance, licensesIssued, migrated, villageDelivered,
                traineeAdvancePaid, rewardSum, next.orders(), next.rewardSum(), next.weightTenths(), next.countS(),
                next.countM(), next.countL(), next.countXl());
    }

    public int completed() {
        return delivered + failed;
    }

    /** Share of successful contracts, 1.0 when nothing was completed yet. */
    public double successRate() {
        int total = completed();
        return total == 0 ? 1.0 : (double) delivered / total;
    }

    public CourierProfile withXp(int newXp) {
        return copy(Math.max(0, newXp), delivered, failed, late, distance, licensesIssued, migrated, villageDelivered,
                traineeAdvancePaid, rewardSum, issuerStats());
    }

    public CourierProfile withLicensesIssued(int count) {
        return copy(xp, delivered, failed, late, distance, count, migrated, villageDelivered, traineeAdvancePaid,
                rewardSum, issuerStats());
    }

    public CourierProfile withMigrated(boolean value) {
        return copy(xp, delivered, failed, late, distance, licensesIssued, value, villageDelivered, traineeAdvancePaid,
                rewardSum, issuerStats());
    }

    public CourierProfile withDelivery(int gainedXp, long blocks, boolean wasLate, long rewardPaid) {
        return copy(xp + Math.max(0, gainedXp), delivered + 1, failed, late + (wasLate ? 1 : 0),
                distance + Math.max(0, blocks), licensesIssued, migrated, villageDelivered, traineeAdvancePaid,
                rewardSum + Math.max(0, rewardPaid), issuerStats());
    }

    public CourierProfile withVillageDelivery(int gainedXp, long blocks, boolean wasLate, long rewardPaid) {
        return copy(xp + Math.max(0, gainedXp), delivered + 1, failed, late + (wasLate ? 1 : 0),
                distance + Math.max(0, blocks), licensesIssued, migrated, villageDelivered + 1, traineeAdvancePaid,
                rewardSum + Math.max(0, rewardPaid), issuerStats());
    }

    public CourierProfile withTraineeAdvancePaid() {
        return copy(xp, delivered, failed, late, distance, licensesIssued, migrated, villageDelivered, true, rewardSum,
                issuerStats());
    }

    public CourierProfile withFailure() {
        return copy(xp, delivered, failed + 1, late, distance, licensesIssued, migrated, villageDelivered,
                traineeAdvancePaid, rewardSum, issuerStats());
    }

    private CourierProfile copy(int xp, int delivered, int failed, int late, long distance, int licensesIssued,
                              boolean migrated, int villageDelivered, boolean traineeAdvancePaid, long rewardSum,
                              IssuerStats issuer) {
        return new CourierProfile(xp, delivered, failed, late, distance, licensesIssued, migrated, villageDelivered,
                traineeAdvancePaid, rewardSum, issuer.orders(), issuer.rewardSum(), issuer.weightTenths(),
                issuer.countS(), issuer.countM(), issuer.countL(), issuer.countXl());
    }
}
