package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S village admin intent. The server re-checks operator / singleplayer-creative permission. */
public record VillageAdminPayload(
        BlockPos stationPos,
        Action action,
        String text,
        int amount,
        boolean flag
) implements CustomPacketPayload {
    public static final Type<VillageAdminPayload> TYPE = new Type<>(AeronauticsCouriers.id("village_admin"));

    public enum Action {
        OPEN,
        RENAME,
        REP_ADD,
        REP_SET,
        REFRESH,
        REGENERATE,
        CLEAR,
        INTERVAL,
        TOGGLE_EXPENSIVE,
        TOGGLE_RARE,
        TOGGLE_SIZE
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageAdminPayload> STREAM_CODEC =
            StreamCodec.of(VillageAdminPayload::write, VillageAdminPayload::read);

    public static VillageAdminPayload open(BlockPos pos) {
        return new VillageAdminPayload(pos, Action.OPEN, "", 0, false);
    }

    public static VillageAdminPayload of(BlockPos pos, Action action) {
        return new VillageAdminPayload(pos, action, "", 0, false);
    }

    public static VillageAdminPayload text(BlockPos pos, Action action, String text) {
        return new VillageAdminPayload(pos, action, text, 0, false);
    }

    public static VillageAdminPayload amount(BlockPos pos, Action action, int amount) {
        return new VillageAdminPayload(pos, action, "", amount, false);
    }

    public static VillageAdminPayload flag(BlockPos pos, Action action, boolean flag) {
        return new VillageAdminPayload(pos, action, "", 0, flag);
    }

    public static VillageAdminPayload size(BlockPos pos, int sizeOrdinal, boolean allowed) {
        return new VillageAdminPayload(pos, Action.TOGGLE_SIZE, "", sizeOrdinal, allowed);
    }

    private static void write(RegistryFriendlyByteBuf buf, VillageAdminPayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.stationPos);
        ByteBufCodecs.VAR_INT.encode(buf, payload.action.ordinal());
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.text);
        ByteBufCodecs.VAR_INT.encode(buf, payload.amount);
        buf.writeBoolean(payload.flag);
    }

    private static VillageAdminPayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
        int ord = ByteBufCodecs.VAR_INT.decode(buf);
        Action[] values = Action.values();
        Action action = ord >= 0 && ord < values.length ? values[ord] : Action.OPEN;
        return new VillageAdminPayload(pos, action, ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
