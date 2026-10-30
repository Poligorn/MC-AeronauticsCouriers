package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientNotifications;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: one fresh event. The client routes it to toast / chat / sound by its own settings. {@code awayCount > 0}
 * marks the single login summary («while you were away: N»), which is not a log entry.
 */
public record NotificationAlertPayload(NotificationSyncPayload.Line line, int awayCount) implements CustomPacketPayload {
    public static final Type<NotificationAlertPayload> TYPE = new Type<>(AeronauticsCouriers.id("notification_alert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NotificationAlertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NotificationSyncPayload.Line.STREAM_CODEC, NotificationAlertPayload::line,
                    ByteBufCodecs.VAR_INT, NotificationAlertPayload::awayCount,
                    NotificationAlertPayload::new);

    public static void handle(NotificationAlertPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientNotifications.onAlert(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
