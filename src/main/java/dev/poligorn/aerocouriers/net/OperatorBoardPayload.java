package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: operator-only test actions. Permission is checked on the server ({@code LEVEL_GAMEMASTERS}); the client
 * only hides the tab.
 */
public record OperatorBoardPayload(Action action, Optional<UUID> contractId) implements CustomPacketPayload {
    public static final Type<OperatorBoardPayload> TYPE = new Type<>(AeronauticsCouriers.id("operator_board"));

    public enum Action {
        GIVE_LICENSE, ADD_MONEY, FORCE_SPAWN, FORCE_DELIVER
    }

    public static final long TEST_MONEY = 500;

    public static final StreamCodec<ByteBuf, OperatorBoardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), OperatorBoardPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), OperatorBoardPayload::contractId,
            OperatorBoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
