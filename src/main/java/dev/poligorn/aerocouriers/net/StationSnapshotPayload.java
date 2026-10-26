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
        boolean canRename,
        List<BoardSnapshotPayload.BoardEntry> orders
) implements CustomPacketPayload {
    public static final Type<StationSnapshotPayload> TYPE = new Type<>(AeronauticsCouriers.id("station_snapshot"));
    public static final int MAX_ORDERS = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, StationSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationSnapshotPayload::stationPos,
                    ByteBufCodecs.STRING_UTF8, StationSnapshotPayload::name,
                    ByteBufCodecs.VAR_INT, StationSnapshotPayload::reputation,
                    ByteBufCodecs.VAR_INT, StationSnapshotPayload::bonusPercent,
                    ByteBufCodecs.BOOL, StationSnapshotPayload::canRename,
                    BoardSnapshotPayload.BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)),
                    StationSnapshotPayload::orders,
                    StationSnapshotPayload::new);

    public static void handle(StationSnapshotPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientStation.apply(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
