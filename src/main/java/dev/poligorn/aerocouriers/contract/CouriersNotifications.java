package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.net.NotificationAlertPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import java.util.Comparator;
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
        NotificationLog.get(server).push(server, player, message, contractId);
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

    /** Persist and sync without chat, toast, or sound (e.g. station board footer). */
    public static void pushSilent(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId) {
        NotificationLog.get(server).push(server, player, message, contractId);
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
        List<NotificationSyncPayload.Line> lines = NotificationLog.get(server).entries(player.getUUID()).stream()
                .sorted(Comparator.comparingLong(NotificationLog.Entry::at).reversed())
                .map(e -> new NotificationSyncPayload.Line(e.id(), e.at(), e.text(), e.contractId(), e.read()))
                .toList();
        PacketDistributor.sendToPlayer(player, new NotificationSyncPayload(ContractManager.now(server), lines));
    }

    public static void markRead(ServerPlayer player) {
        NotificationLog.get(player.server).markAllRead(player.getUUID());
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
