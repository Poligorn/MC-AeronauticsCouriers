package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseOwner;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.registry.ModComponents;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Builds {@link BoardSnapshotPayload.BoardProfile} from live player data. */
public final class ProfileSnapshots {
    private ProfileSnapshots() {
    }

    public static BoardSnapshotPayload.BoardProfile forPlayer(ServerPlayer player) {
        CourierProfile stats = Licenses.profile(player);
        LicenseRank rank = Licenses.effectiveRank(player);
        List<Integer> rankDefaults = List.of(0, 300, 1500, 5000, 15000);
        List<? extends Integer> configured = CouriersServerConfig.RANK_XP.get();
        int[] thresholds = new int[5];
        for (int i = 0; i < 5; i++) {
            thresholds[i] = i < configured.size() ? configured.get(i) : rankDefaults.get(i);
        }
        int acePercent = (int) Math.round(CouriersServerConfig.ACE_MIN_SUCCESS.get() * 100.0);
        return new BoardSnapshotPayload.BoardProfile(
                player.getGameProfile().getName(),
                rank,
                stats.delivered(),
                stats.failed(),
                stats.distance(),
                stats.rewardSum(),
                stats.villageDelivered(),
                CouriersServerConfig.TRAINEE_FREE_CONTRACTS.get(),
                CouriersServerConfig.TRAINEE_MAX_DISTANCE.get(),
                stats.xp(),
                thresholds[0],
                thresholds[1],
                thresholds[2],
                thresholds[3],
                thresholds[4],
                acePercent,
                Licenses.isLicensedCourier(player),
                stats.licensesIssued(),
                stats.issuerOrders(),
                stats.issuerRewardSum(),
                stats.issuerWeightTenths(),
                stats.issuerCountS(),
                stats.issuerCountM(),
                stats.issuerCountL(),
                stats.issuerCountXl(),
                Licenses.holdsPilotLicense(player));
    }
}
