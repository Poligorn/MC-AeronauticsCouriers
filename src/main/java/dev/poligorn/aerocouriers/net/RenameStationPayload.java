package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: operator rename of a post station (GDD §6.0). */
public record RenameStationPayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<RenameStationPayload> TYPE = new Type<>(AeronauticsCouriers.id("rename_station"));
    public static final StreamCodec<ByteBuf, RenameStationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.map(BlockPos::of, BlockPos::asLong), RenameStationPayload::pos,
            ByteBufCodecs.STRING_UTF8, RenameStationPayload::name,
            RenameStationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
