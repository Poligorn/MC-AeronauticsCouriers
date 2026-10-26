package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientStation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: post station window (name, reputation, NPC orders at this station). */
public record StationSnapshotPayload(
        BlockPos stationPos,
        String name,
        int reputation,
        int bonusPercent,
        int reputationTier,
        int reputationNextThreshold,
        boolean canRename,
        List<BoardSnapshotPayload.BoardEntry> orders
) implements CustomPacketPayload {
    public static final Type<StationSnapshotPayload> TYPE = new Type<>(AeronauticsCouriers.id("station_snapshot"));
    public static final int MAX_ORDERS = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, StationSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(StationSnapshotPayload::write, StationSnapshotPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, StationSnapshotPayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.stationPos());
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
        ByteBufCodecs.VAR_INT.encode(buf, payload.reputation());
        ByteBufCodecs.VAR_INT.encode(buf, payload.bonusPercent());
        ByteBufCodecs.VAR_INT.encode(buf, payload.reputationTier());
        ByteBufCodecs.VAR_INT.encode(buf, payload.reputationNextThreshold());
        buf.writeBoolean(payload.canRename());
        BoardSnapshotPayload.BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)).encode(buf, payload.orders());
    }

    private static StationSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        return new StationSnapshotPayload(
                BlockPos.STREAM_CODEC.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(),
                BoardSnapshotPayload.BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)).decode(buf));
    }

    public static void handle(StationSnapshotPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientStation.apply(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
