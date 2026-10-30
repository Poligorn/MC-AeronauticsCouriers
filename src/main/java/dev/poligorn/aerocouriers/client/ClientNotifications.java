package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.NoticeChannel;
import dev.poligorn.aerocouriers.net.NotificationAlertPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client cache of the server notification log (badges, Mail list) and the per-category alert router. */
public final class ClientNotifications {
    private static long worldTime;
    private static long awaySince = -1;
    private static long loginAt = -1;
    private static List<NotificationSyncPayload.Line> entries = List.of();

    private ClientNotifications() {
    }

    public static void apply(NotificationSyncPayload payload) {
        worldTime = payload.worldTime();
        awaySince = payload.awaySince();
        loginAt = payload.loginAt();
        entries = List.copyOf(payload.entries());
        if (Minecraft.getInstance().screen instanceof CourierBoardScreen screen) {
            screen.onNotificationsChanged();
        }
    }

    /** One server event: the log is already written; toast, sound and chat follow the player's settings. */
    public static void onAlert(NotificationAlertPayload payload) {
        NotificationSyncPayload.Line line = payload.line();
        NoticeChannel.Row row = NoticeChannel.Row.of(line.kind());
        boolean urgent = row == NoticeChannel.Row.CRITICAL;
        boolean quiet = CouriersClientConfig.get(CouriersClientConfig.DO_NOT_DISTURB, false) && !urgent;
        if (!quiet && CouriersClientConfig.channelEnabled(row, NoticeChannel.TOAST)) {
            ClientToasts.push(row, line.text());
        }
        if (!quiet && payload.awayCount() <= 0 && CouriersClientConfig.channelEnabled(row, NoticeChannel.SOUND)) {
            if (urgent) {
                ClientCouriersSounds.playUrgent();
            } else {
                ClientCouriersSounds.playNotification();
            }
        }
        if (payload.awayCount() <= 0) {
            TabletAntennaSignal.triggerReceive();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && payload.awayCount() <= 0 && CouriersClientConfig.channelEnabled(row, NoticeChannel.CHAT)) {
            mc.player.displayClientMessage(Component.translatable("ui.aerocouriers.notify.chat_prefix")
                    .append(line.text()), false);
        }
    }

    public static long worldTime() {
        return worldTime;
    }

    public static long awaySince() {
        return awaySince;
    }

    public static long loginAt() {
        return loginAt;
    }

    public static List<NotificationSyncPayload.Line> entries() {
        return entries;
    }

    public static int unreadCount() {
        int count = 0;
        for (NotificationSyncPayload.Line line : entries) {
            if (!line.read()) {
                count++;
            }
        }
        return count;
    }

    public enum MailFilter {
        ALL, ORDERS, MONEY, CARGO, URGENT;

        public String key() {
            return "ui.aerocouriers.inbox.filter." + name().toLowerCase(java.util.Locale.ROOT);
        }

        boolean accepts(NotificationSyncPayload.Line line) {
            NoticeChannel.Row row = NoticeChannel.Row.of(line.kind());
            return switch (this) {
                case ALL -> true;
                case ORDERS -> row == NoticeChannel.Row.ORDERS || row == NoticeChannel.Row.TUTORIAL
                        || row == NoticeChannel.Row.ADMIN || row == NoticeChannel.Row.BOARD;
                case MONEY -> row == NoticeChannel.Row.MONEY;
                case CARGO -> row == NoticeChannel.Row.CARGO || line.hasItems();
                case URGENT -> row == NoticeChannel.Row.CRITICAL;
            };
        }
    }

    public enum MailGroup {
        SESSION, AWAY, EARLIER
    }

    private static MailFilter mailFilter = MailFilter.ALL;

    public static MailFilter mailFilter() {
        return mailFilter;
    }

    public static void setMailFilter(MailFilter filter) {
        mailFilter = filter;
    }

    public static List<NotificationSyncPayload.Line> filtered() {
        if (mailFilter == MailFilter.ALL) {
            return entries;
        }
        return entries.stream().filter(mailFilter::accepts).toList();
    }

    /** Current session on top (no header), then events from the offline window, then everything older. */
    public static MailGroup group(NotificationSyncPayload.Line line) {
        if (loginAt < 0 || line.at() >= loginAt) {
            return MailGroup.SESSION;
        }
        return awaySince >= 0 && line.at() >= awaySince ? MailGroup.AWAY : MailGroup.EARLIER;
    }

    public static boolean unreadUrgent() {
        for (NotificationSyncPayload.Line line : entries) {
            if (!line.read() && line.urgent()) {
                return true;
            }
        }
        return false;
    }

    public static void playAlertSound() {
        ClientCouriersSounds.playNotification();
        TabletAntennaSignal.triggerReceive();
    }
}
