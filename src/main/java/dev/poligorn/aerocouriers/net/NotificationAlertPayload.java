package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientNotifications;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: play the notification sound on the client (respects client config). */
public record NotificationAlertPayload() implements CustomPacketPayload {
    public static final Type<NotificationAlertPayload> TYPE = new Type<>(AeronauticsCouriers.id("notification_alert"));
    public static final NotificationAlertPayload INSTANCE = new NotificationAlertPayload();
    public static final StreamCodec<ByteBuf, NotificationAlertPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(NotificationAlertPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(ClientNotifications::playAlertSound);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
