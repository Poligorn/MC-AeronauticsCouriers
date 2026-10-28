package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: claim everything that fits from the mailbox (same as {@code /couriers mail claim}). */
public record ClaimMailPayload() implements CustomPacketPayload {
    public static final Type<ClaimMailPayload> TYPE = new Type<>(AeronauticsCouriers.id("claim_mail"));
    public static final ClaimMailPayload INSTANCE = new ClaimMailPayload();
    public static final StreamCodec<ByteBuf, ClaimMailPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
