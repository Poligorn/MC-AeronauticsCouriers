package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.contract.CourierInbox;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InboxPackets {
    private InboxPackets() {
    }

    public static void handleDismiss(ServerPlayer player, InboxDismissPayload payload) {
        if (!OrderTablets.canAccessBoard(player)) {
            BoardPackets.denyTablet(player);
            return;
        }
        CourierInbox inbox = CourierInbox.get(player.server);
        if (inbox.dismiss(player.getUUID(), payload.messageId())) {
            CouriersNotifications.sync(player);
            BoardPackets.syncSnapshot(player);
        }
    }

    public static void handleClaim(ServerPlayer player, InboxClaimPayload payload) {
        if (!OrderTablets.canAccessBoard(player)) {
            BoardPackets.denyTablet(player);
            return;
        }
        CourierInbox inbox = CourierInbox.get(player.server);
        int moved = inbox.claimMessage(player.getUUID(), payload.messageId(), player.getInventory());
        int left = inbox.allItemStacks(player.getUUID()).size();
        if (moved > 0) {
            player.displayClientMessage(
                    Component.translatable("contract.aerocouriers.mail.claimed", moved, left), true);
            CouriersNotifications.sync(player);
            BoardPackets.syncSnapshot(player);
        }
    }

    public static void handleClearPlain(ServerPlayer player) {
        if (!OrderTablets.canAccessBoard(player)) {
            BoardPackets.denyTablet(player);
            return;
        }
        CourierInbox inbox = CourierInbox.get(player.server);
        int removed = inbox.clearPlain(player.getUUID());
        if (removed > 0) {
            CouriersNotifications.sync(player);
            BoardPackets.syncSnapshot(player);
        }
    }
}
