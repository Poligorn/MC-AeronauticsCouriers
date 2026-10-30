package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: onboarding and license intents from the tablet; {@code arg} is the coach-mark bit for {@link Op#HINT}. */
public record TutorialIntentPayload(Op op, int arg) implements CustomPacketPayload {
    public static final Type<TutorialIntentPayload> TYPE = new Type<>(AeronauticsCouriers.id("tutorial_intent"));

    public enum Op {
        START, SKIP, ACK_COMPLETE, HINT, RESTORE_LICENSE;

        static Op byOrdinal(int i) {
            Op[] all = values();
            return i >= 0 && i < all.length ? all[i] : HINT;
        }
    }

    public static final StreamCodec<ByteBuf, TutorialIntentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(Op::byOrdinal, Op::ordinal), TutorialIntentPayload::op,
            ByteBufCodecs.VAR_INT, TutorialIntentPayload::arg,
            TutorialIntentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
