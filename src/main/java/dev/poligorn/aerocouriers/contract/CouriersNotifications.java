package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.net.NotificationAlertPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import dev.poligorn.aerocouriers.task.TaskSync;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server half of the event bus: every event is logged in {@link CourierInbox} with a category and priority, then the
 * online player gets one {@link NotificationAlertPayload}. Toast, chat and sound are chosen on the client.
 */
public final class CouriersNotifications {
    /** Login tick and the previous logout tick of the current session, per online player. */
    private static final Map<UUID, long[]> SESSIONS = new HashMap<>();

    private CouriersNotifications() {
    }

    public static void notify(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId,
                              NoticeKind kind) {
        CourierInbox.Message logged = CourierInbox.get(server).pushText(server, player, message, contractId, kind);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) {
            return;
        }
        deliverAlert(online, line(logged), 0);
        sync(online);
    }

    /** Kind derived from the message key; {@code chat} is ignored (chat is a client setting now). */
    public static void notifyOrder(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId,
                                   boolean chat) {
        notify(server, player, message, contractId, NoticeKind.classify(message));
    }

    public static void notifyOrder(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId) {
        notify(server, player, message, contractId, NoticeKind.classify(message));
    }

    /** Persist and sync without an alert (badge and log only). */
    public static void pushSilent(MinecraftServer server, UUID player, Component message, Optional<UUID> contractId) {
        CourierInbox.get(server).pushText(server, player, message, contractId,
                new NoticeKind(NoticeKind.classify(message).category(), NoticeKind.Priority.SILENT));
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            sync(online);
        }
    }

    /** Cargo returned to the inbox: alert and sync like any other event (was log-only before 0.2.0). */
    public static void cargoPosted(MinecraftServer server, UUID player, CourierInbox.Message message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) {
            return;
        }
        deliverAlert(online, line(message), 0);
        sync(online);
        TaskSync.sync(online);
    }

    public static void sync(ServerPlayer player) {
        if (!player.connection.hasChannel(NotificationSyncPayload.TYPE)) {
            return;
        }
        MinecraftServer server = player.server;
        List<NotificationSyncPayload.Line> lines = CourierInbox.get(server).messages(player.getUUID()).stream()
                .map(CouriersNotifications::line)
                .toList();
        long[] session = SESSIONS.getOrDefault(player.getUUID(), new long[] {-1L, -1L});
        PacketDistributor.sendToPlayer(player, new NotificationSyncPayload(ContractManager.now(server), lines,
                session[1], session[0]));
    }

    public static void markRead(ServerPlayer player) {
        CourierInbox.get(player.server).markAllRead(player.getUUID());
        sync(player);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        CourierInbox inbox = CourierInbox.get(server);
        long away = inbox.lastSeen(player.getUUID());
        long now = ContractManager.now(server);
        SESSIONS.put(player.getUUID(), new long[] {now, away});
        sync(player);
        int missed = away < 0 ? 0 : inbox.unreadSince(player.getUUID(), away);
        if (missed > 0 && player.connection.hasChannel(NotificationAlertPayload.TYPE)) {
            NotificationSyncPayload.Line summary = new NotificationSyncPayload.Line(0L, now,
                    Component.translatable("ui.aerocouriers.toast.away", missed), Optional.empty(), false, false, 0,
                    NoticeKind.Category.ORDERS.ordinal(), NoticeKind.Priority.NORMAL.ordinal());
            PacketDistributor.sendToPlayer(player, new NotificationAlertPayload(summary, missed));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player.getUUID());
            CourierInbox.get(player.server).markSeen(player.getUUID(), ContractManager.now(player.server));
        }
    }

    static NotificationSyncPayload.Line line(CourierInbox.Message m) {
        return new NotificationSyncPayload.Line(m.id(), m.at(), m.text(), m.contractId(), m.read(), m.hasItems(),
                CourierInbox.Message.totalItemCount(m.items()), m.category(), m.priority());
    }

    private static void deliverAlert(ServerPlayer player, NotificationSyncPayload.Line line, int awayCount) {
        if (line.priority() == NoticeKind.Priority.SILENT.ordinal()) {
            return;
        }
        if (player.connection.hasChannel(NotificationAlertPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new NotificationAlertPayload(line, awayCount));
        } else {
            player.displayClientMessage(Component.translatable("ui.aerocouriers.notification.toast",
                    shorten(line.text())), true);
        }
    }

    private static Component shorten(Component message) {
        String plain = message.getString();
        if (plain.length() <= 80) {
            return Component.literal(plain);
        }
        return Component.literal(plain.substring(0, 77) + "…");
    }
}
