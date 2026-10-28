package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: open the post station window for the block the player clicked. */
public record OpenStationPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenStationPayload> TYPE = new Type<>(AeronauticsCouriers.id("open_station"));
    public static final StreamCodec<ByteBuf, OpenStationPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(OpenStationPayload::fromLong, OpenStationPayload::toLong);

    private static OpenStationPayload fromLong(long packed) {
        return new OpenStationPayload(BlockPos.of(packed));
    }

    private static long toLong(OpenStationPayload payload) {
        return payload.pos.asLong();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
