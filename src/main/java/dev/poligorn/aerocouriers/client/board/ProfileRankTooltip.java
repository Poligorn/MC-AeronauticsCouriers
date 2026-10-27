package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.contract.RankProgress;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Tooltip lines for what the next rank unlocks (same limits as {@link dev.poligorn.aerocouriers.contract.AcceptEligibility}). */
public final class ProfileRankTooltip {
    private ProfileRankTooltip() {
    }

    public static List<Component> lines(LicenseRank current) {
        if (current.isAdmin() || current.level() >= 5) {
            return List.of();
        }
        LicenseRank next = RankProgress.nextRank(current);
        RankRules.Limits limits = RankRules.limits(next);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ui.aerocouriers.profile.next_rank.title", next.displayName()));
        lines.add(Component.translatable(
                "ui.aerocouriers.profile.next_rank.collateral", ProfileRankBar.formatCollateral(limits.maxCollateral())));
        lines.add(Component.translatable(
                "ui.aerocouriers.profile.next_rank.size", AcceptEligibility.sizeLabel(limits.maxSize())));
        lines.add(Component.translatable(
                "ui.aerocouriers.profile.next_rank.distance", AcceptEligibility.formatDistance(limits.maxDistance())));
        lines.add(Component.translatable("ui.aerocouriers.profile.next_rank.active", limits.maxActive()));
        return lines;
    }
}
