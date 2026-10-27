package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientCouriersSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: play a tablet UI sound for the local player (respects client sound config). */
public record CouriersUiSoundPayload(Kind kind) implements CustomPacketPayload {
    public static final Type<CouriersUiSoundPayload> TYPE = new Type<>(AeronauticsCouriers.id("couriers_ui_sound"));

    public enum Kind {
        ORDER_ACCEPT,
        ORDER_DENY
    }

    public static final StreamCodec<ByteBuf, CouriersUiSoundPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), CouriersUiSoundPayload::kind,
            CouriersUiSoundPayload::new);

    public static void handle(CouriersUiSoundPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.kind()) {
                case ORDER_ACCEPT -> ClientCouriersSounds.playOrderAccept();
                case ORDER_DENY -> ClientCouriersSounds.playOrderDeny();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
