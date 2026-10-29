package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProfilePackets {
    private ProfilePackets() {
    }

    public static void handleRequest(ServerPlayer viewer, RequestPlayerProfilePayload payload) {
        if (!OrderTablets.canAccessBoard(viewer)) {
            BoardPackets.denyTablet(viewer);
            return;
        }
        UUID targetId = payload.targetId();
        if (targetId.equals(viewer.getUUID())) {
            PacketDistributor.sendToPlayer(viewer,
                    new PlayerProfilePayload(targetId, ProfileSnapshots.forPlayer(viewer)));
            return;
        }
        ServerPlayer target = viewer.server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            viewer.displayClientMessage(Component.translatable("ui.aerocouriers.profile.offline"), true);
            return;
        }
        PacketDistributor.sendToPlayer(viewer, new PlayerProfilePayload(targetId, ProfileSnapshots.forPlayer(target)));
    }

    public static void handleClaimTrainee(ServerPlayer player) {
        if (!OrderTablets.canAccessBoard(player)) {
            BoardPackets.denyTablet(player);
            return;
        }
        if (Licenses.holdsPilotLicense(player) || Licenses.isLicensedCourier(player)) {
            player.displayClientMessage(Component.translatable("ui.aerocouriers.profile.license.already"), true);
            return;
        }
        if (Licenses.profile(player).licensesIssued() > 0) {
            player.displayClientMessage(Component.translatable("ui.aerocouriers.profile.license.reissue_hint"), true);
            return;
        }
        Licenses.deliver(player, Licenses.issueLicense(player, LicenseRank.RANK1, true));
        player.displayClientMessage(Component.translatable("ui.aerocouriers.profile.license.claimed"), true);
        BoardPackets.syncSnapshot(player);
    }
}
