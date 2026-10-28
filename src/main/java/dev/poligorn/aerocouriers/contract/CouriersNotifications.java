package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.net.NotificationAlertPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side order notifications: persisted log, sync, toast, and client sound trigger. */
public final class CouriersNotifications {
    private CouriersNotifications() {
    }

    /** Log, optional chat, sync, toast, and alert sound for online players. */
    public static void notifyOrder(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId,
                                   boolean chat) {
        CourierInbox.get(server).pushText(server, player, message, contractId);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) {
            return;
        }
        if (chat) {
            online.sendSystemMessage(message);
        }
        deliverAlert(online, message);
        sync(online);
    }

    public static void notifyOrder(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId) {
        notifyOrder(server, player, message, contractId, true);
    }

    /** Persist and sync without chat, toast, or sound. */
    public static void pushSilent(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId) {
        CourierInbox.get(server).pushText(server, player, message, contractId);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            sync(online);
        }
    }

    public static void sync(ServerPlayer player) {
        if (!player.connection.hasChannel(NotificationSyncPayload.TYPE)) {
            return;
        }
        MinecraftServer server = player.server;
        List<NotificationSyncPayload.Line> lines = CourierInbox.get(server).messages(player.getUUID()).stream()
                .map(m -> new NotificationSyncPayload.Line(
                        m.id(),
                        m.at(),
                        m.text(),
                        m.contractId(),
                        m.read(),
                        m.hasItems(),
                        CourierInbox.Message.totalItemCount(m.items())))
                .toList();
        PacketDistributor.sendToPlayer(player, new NotificationSyncPayload(ContractManager.now(server), lines));
    }

    public static void markRead(ServerPlayer player) {
        CourierInbox.get(player.server).markAllRead(player.getUUID());
        sync(player);
    }

    private static void deliverAlert(ServerPlayer player, Component message) {
        Component toast = Component.translatable("ui.aerocouriers.notification.toast", shorten(message));
        player.displayClientMessage(toast, true);
        PacketDistributor.sendToPlayer(player, new NotificationAlertPayload());
    }

    private static Component shorten(Component message) {
        String plain = message.getString();
        if (plain.length() <= 80) {
            return Component.literal(plain);
        }
        return Component.literal(plain.substring(0, 77) + "…");
    }
}
