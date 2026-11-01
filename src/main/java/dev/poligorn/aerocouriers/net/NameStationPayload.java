package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: name a station just placed by this player. Operator rights are not required. */
public record NameStationPayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<NameStationPayload> TYPE = new Type<>(AeronauticsCouriers.id("name_station"));
    public static final StreamCodec<ByteBuf, NameStationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.map(BlockPos::of, BlockPos::asLong), NameStationPayload::pos,
            ByteBufCodecs.STRING_UTF8, NameStationPayload::name,
            NameStationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
