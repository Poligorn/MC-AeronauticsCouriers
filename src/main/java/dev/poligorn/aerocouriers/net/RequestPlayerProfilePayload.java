package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import io.netty.buffer.ByteBuf;

/** C2S: open another player's public profile on the board. */
public record RequestPlayerProfilePayload(UUID targetId) implements CustomPacketPayload {
    public static final Type<RequestPlayerProfilePayload> TYPE =
            new Type<>(AeronauticsCouriers.id("request_player_profile"));

    public static final StreamCodec<ByteBuf, RequestPlayerProfilePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RequestPlayerProfilePayload::targetId,
            RequestPlayerProfilePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
