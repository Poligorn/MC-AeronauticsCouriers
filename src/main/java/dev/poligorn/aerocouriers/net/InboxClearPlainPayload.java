package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InboxClearPlainPayload() implements CustomPacketPayload {
    public static final Type<InboxClearPlainPayload> TYPE = new Type<>(AeronauticsCouriers.id("inbox_clear_plain"));
    public static final InboxClearPlainPayload INSTANCE = new InboxClearPlainPayload();
    public static final StreamCodec<ByteBuf, InboxClearPlainPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
