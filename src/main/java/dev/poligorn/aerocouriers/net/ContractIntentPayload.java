package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: accept / abandon / cancel. The server runs the matching {@code ContractManager} method. */
public record ContractIntentPayload(Kind kind, UUID id) implements CustomPacketPayload {
    public static final Type<ContractIntentPayload> TYPE = new Type<>(AeronauticsCouriers.id("contract_intent"));

    public enum Kind {
        ACCEPT, ABANDON, CANCEL
    }

    public static final StreamCodec<ByteBuf, ContractIntentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), ContractIntentPayload::kind,
            UUIDUtil.STREAM_CODEC, ContractIntentPayload::id,
            ContractIntentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
