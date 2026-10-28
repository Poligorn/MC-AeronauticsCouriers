package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InboxClaimPayload(long messageId) implements CustomPacketPayload {
    public static final Type<InboxClaimPayload> TYPE = new Type<>(AeronauticsCouriers.id("inbox_claim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InboxClaimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, InboxClaimPayload::messageId,
            InboxClaimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
