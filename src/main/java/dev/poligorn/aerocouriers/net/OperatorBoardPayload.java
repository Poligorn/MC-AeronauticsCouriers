package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: operator-only admin panel actions. Permission is checked on the server ({@code LEVEL_GAMEMASTERS}); the client
 * only hides the tab.
 */
public record OperatorBoardPayload(
        Action action,
        Optional<UUID> contractId,
        Optional<UUID> targetPlayer,
        Optional<String> arg1,
        Optional<String> arg2,
        long longArg,
        boolean confirmed
) implements CustomPacketPayload {
    public static final Type<OperatorBoardPayload> TYPE = new Type<>(AeronauticsCouriers.id("operator_board"));

    public enum Action {
        GIVE_LICENSE,
        ADD_MONEY,
        GIVE_WAYBILL,
        FORCE_SPAWN,
        FORCE_DELIVER,
        FORCE_FAIL,
        ADMIN_CANCEL,
        EXTEND_DEADLINE,
        TELEPORT_TO_CONTRACT,
        PLAYER_RANK,
        PLAYER_XP_ADD,
        PLAYER_XP_SET,
        PLAYER_REP,
        PLAYER_LICENSE,
        PLAYER_WAYBILL,
        PLAYER_MONEY,
        ECONOMY_RELOAD,
        ECONOMY_SHOW_VALUE,
        ECONOMY_SET_VALUE,
        ECONOMY_SHOW_WEIGHT,
        ECONOMY_SET_WEIGHT,
        SERVER_REGEN,
        CONFIG_SET,
        DEBUG_TOGGLE_SELF_ACCEPT,
        PLAYER_RESET_LICENSE,
        ECONOMY_SET_COLLATERAL_PROFILE,
        SERVER_TUTORIAL_TOGGLE
    }

    public static final long TEST_MONEY = 500;

    public static OperatorBoardPayload simple(Action action, Optional<UUID> contractId) {
        return new OperatorBoardPayload(action, contractId, Optional.empty(), Optional.empty(), Optional.empty(), 0L,
                false);
    }

    public static final StreamCodec<ByteBuf, OperatorBoardPayload> STREAM_CODEC = StreamCodec.of(
            OperatorBoardPayload::write,
            OperatorBoardPayload::read);

    private static void write(ByteBuf buf, OperatorBoardPayload payload) {
        VarInt.write(buf, payload.action().ordinal());
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, payload.contractId());
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, payload.targetPlayer());
        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, payload.arg1());
        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, payload.arg2());
        VarLong.write(buf, payload.longArg());
        buf.writeBoolean(payload.confirmed());
    }

    private static OperatorBoardPayload read(ByteBuf buf) {
        Action action = Action.values()[VarInt.read(buf)];
        Optional<UUID> contractId = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf);
        Optional<UUID> targetPlayer = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf);
        Optional<String> arg1 = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
        Optional<String> arg2 = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
        long longArg = VarLong.read(buf);
        boolean confirmed = buf.readBoolean();
        return new OperatorBoardPayload(action, contractId, targetPlayer, arg1, arg2, longArg, confirmed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
