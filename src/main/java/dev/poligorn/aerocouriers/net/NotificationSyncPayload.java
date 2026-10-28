package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientNotifications;
import dev.poligorn.aerocouriers.contract.CourierInbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: full notification log for the viewer (newest first in {@link Line} order). */
public record NotificationSyncPayload(long worldTime, List<Line> entries) implements CustomPacketPayload {
    public static final Type<NotificationSyncPayload> TYPE = new Type<>(AeronauticsCouriers.id("notification_sync"));

    public record Line(
            long id,
            long at,
            Component text,
            Optional<UUID> contractId,
            boolean read,
            boolean hasItems,
            int itemCount
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.of(
                (buf, line) -> {
                    ByteBufCodecs.VAR_LONG.encode(buf, line.id());
                    ByteBufCodecs.VAR_LONG.encode(buf, line.at());
                    ComponentSerialization.STREAM_CODEC.encode(buf, line.text());
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, line.contractId());
                    ByteBufCodecs.BOOL.encode(buf, line.read());
                    ByteBufCodecs.BOOL.encode(buf, line.hasItems());
                    ByteBufCodecs.VAR_INT.encode(buf, line.itemCount());
                },
                buf -> new Line(
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NotificationSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, NotificationSyncPayload::worldTime,
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list(CourierInbox.MAX_ENTRIES)), NotificationSyncPayload::entries,
                    NotificationSyncPayload::new);

    public static void handle(NotificationSyncPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientNotifications.apply(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
