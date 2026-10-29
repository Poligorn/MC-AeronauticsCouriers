package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Client cache of the server notification log (badge + list screen). */
public final class ClientNotifications {
    private static long worldTime;
    private static List<NotificationSyncPayload.Line> entries = List.of();

    private ClientNotifications() {
    }

    public static void apply(NotificationSyncPayload payload) {
        worldTime = payload.worldTime();
        entries = List.copyOf(payload.entries());
        if (Minecraft.getInstance().screen instanceof CourierBoardScreen screen) {
            screen.onNotificationsChanged();
        }
    }

    public static long worldTime() {
        return worldTime;
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

    public static void playAlertSound() {
        ClientCouriersSounds.playNotification();
        TabletAntennaSignal.triggerReceive();
    }
}
