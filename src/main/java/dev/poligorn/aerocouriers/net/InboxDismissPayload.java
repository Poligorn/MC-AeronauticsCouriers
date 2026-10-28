package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InboxDismissPayload(long messageId) implements CustomPacketPayload {
    public static final Type<InboxDismissPayload> TYPE = new Type<>(AeronauticsCouriers.id("inbox_dismiss"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InboxDismissPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, InboxDismissPayload::messageId,
            InboxDismissPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
