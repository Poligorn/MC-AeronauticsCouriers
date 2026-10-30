package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import dev.poligorn.aerocouriers.contract.NoticeKind;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import dev.poligorn.aerocouriers.tutorial.Tutorials;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProfilePackets {
    private ProfilePackets() {
    }

    public static void handleTutorial(ServerPlayer player, TutorialIntentPayload payload) {
        if (!OrderTablets.canAccessBoard(player)) {
            BoardPackets.denyTablet(player);
            return;
        }
        switch (payload.op()) {
            case START -> player.displayClientMessage(Tutorials.start(player), true);
            case SKIP -> Tutorials.skip(player);
            case ACK_COMPLETE -> Tutorials.ackCompletion(player);
            case HINT -> Tutorials.hintSeen(player, payload.arg() & 0xFFFF);
            case RESTORE_LICENSE -> restoreLicense(player);
        }
    }

    /** Paid replacement of a lost license: new number, the old one stops working everywhere. */
    private static void restoreLicense(ServerPlayer player) {
        if (!Licenses.canRestore(player)) {
            player.displayClientMessage(Component.translatable("license.aerocouriers.restore.not_needed"), true);
            return;
        }
        long price = MoneyScale.fromSpurs(CouriersServerConfig.LICENSE_RESTORE_PRICE.get());
        Economy economy = Economies.get(player.server);
        String reason = "license_restore:" + player.getUUID() + ":" + player.server.overworld().getGameTime();
        if (price > 0 && !economy.withdraw(player.getUUID(), price, reason)) {
            player.displayClientMessage(Component.translatable("contract.aerocouriers.error.not_enough_money",
                    MoneyFormat.component(price), MoneyFormat.component(economy.balance(player.getUUID()))), true);
            return;
        }
        Licenses.restore(player);
        CouriersNotifications.notify(player.server, player.getUUID(),
                Component.translatable("license.aerocouriers.restored", MoneyFormat.component(price)),
                java.util.Optional.empty(), NoticeKind.MONEY);
        BoardPackets.syncSnapshot(player);
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
        dev.poligorn.aerocouriers.tutorial.Tutorials.onLicenseClaimed(player);
        BoardPackets.syncSnapshot(player);
    }
}
