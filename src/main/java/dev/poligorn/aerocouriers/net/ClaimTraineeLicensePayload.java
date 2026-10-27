package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: first free trainee pilot license from the profile tab. */
public record ClaimTraineeLicensePayload() implements CustomPacketPayload {
    public static final Type<ClaimTraineeLicensePayload> TYPE =
            new Type<>(AeronauticsCouriers.id("claim_trainee_license"));
    public static final ClaimTraineeLicensePayload INSTANCE = new ClaimTraineeLicensePayload();
    public static final StreamCodec<ByteBuf, ClaimTraineeLicensePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
