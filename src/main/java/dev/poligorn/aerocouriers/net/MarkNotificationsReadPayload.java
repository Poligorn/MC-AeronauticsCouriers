package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** C2S: opening the notification list marks every entry as read. */
public record MarkNotificationsReadPayload() implements CustomPacketPayload {
    public static final Type<MarkNotificationsReadPayload> TYPE =
            new Type<>(AeronauticsCouriers.id("mark_notifications_read"));
    public static final MarkNotificationsReadPayload INSTANCE = new MarkNotificationsReadPayload();
    public static final StreamCodec<ByteBuf, MarkNotificationsReadPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(MarkNotificationsReadPayload payload, ServerPlayer player) {
        CouriersNotifications.markRead(player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
