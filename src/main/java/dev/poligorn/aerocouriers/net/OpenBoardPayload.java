package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: request a board snapshot (and open the screen). {@code page} is the open-orders page, 20 per page. */
public record OpenBoardPayload(int page) implements CustomPacketPayload {
    public static final Type<OpenBoardPayload> TYPE = new Type<>(AeronauticsCouriers.id("open_board"));
    public static final StreamCodec<ByteBuf, OpenBoardPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(OpenBoardPayload::new, OpenBoardPayload::page);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
