package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientStation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: cork-board station window (name, reputation, NPC notes, optional admin panel). */
public record StationSnapshotPayload(
        BlockPos stationPos,
        String name,
        int reputation,
        int bonusPercent,
        int reputationTier,
        int reputationNextThreshold,
        boolean canRename,
        List<BoardSnapshotPayload.BoardEntry> orders,
        long nextVillageRefreshGameTime,
        long snapshotOverworldGameTime,
        View view
) implements CustomPacketPayload {
    public static final Type<StationSnapshotPayload> TYPE = new Type<>(AeronauticsCouriers.id("station_snapshot"));
    public static final int MAX_ORDERS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, StationSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(StationSnapshotPayload::write, StationSnapshotPayload::read);

    public record View(
            boolean openAdmin,
            boolean canAdmin,
            boolean hasTablet,
            boolean requireTablet,
            int playerRankLevel,
            boolean allowExpensive,
            boolean allowRare,
            int allowedSizesMask,
            int refreshIntervalMin,
            int completed,
            int active,
            int onBoard,
            int expensiveCount,
            int rareCount,
            int boardMax,
            List<String> courierNames,
            List<String> otherVillageNames
    ) {
        public static final View EMPTY = new View(false, false, false, true, 1,
                true, true, 0b1111, 0, 0, 0, 0, 0, 0, 12, List.of(), List.of());

        static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC =
                StreamCodec.of(View::write, View::read);

        public String courierName(int index) {
            return index >= 0 && index < courierNames.size() ? courierNames.get(index) : "";
        }

        public boolean nameTaken(String name) {
            String want = name == null ? "" : name.strip();
            if (want.isEmpty()) {
                return false;
            }
            return otherVillageNames.stream().anyMatch(n -> n.equalsIgnoreCase(want));
        }

        private static void write(RegistryFriendlyByteBuf buf, View v) {
            buf.writeBoolean(v.openAdmin);
            buf.writeBoolean(v.canAdmin);
            buf.writeBoolean(v.hasTablet);
            buf.writeBoolean(v.requireTablet);
            ByteBufCodecs.VAR_INT.encode(buf, v.playerRankLevel);
            buf.writeBoolean(v.allowExpensive);
            buf.writeBoolean(v.allowRare);
            ByteBufCodecs.VAR_INT.encode(buf, v.allowedSizesMask);
            ByteBufCodecs.VAR_INT.encode(buf, v.refreshIntervalMin);
            ByteBufCodecs.VAR_INT.encode(buf, v.completed);
            ByteBufCodecs.VAR_INT.encode(buf, v.active);
            ByteBufCodecs.VAR_INT.encode(buf, v.onBoard);
            ByteBufCodecs.VAR_INT.encode(buf, v.expensiveCount);
            ByteBufCodecs.VAR_INT.encode(buf, v.rareCount);
            ByteBufCodecs.VAR_INT.encode(buf, v.boardMax);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ORDERS)).encode(buf, v.courierNames);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ORDERS)).encode(buf, v.otherVillageNames);
        }

        private static View read(RegistryFriendlyByteBuf buf) {
            return new View(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ORDERS)).decode(buf),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ORDERS)).decode(buf));
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, StationSnapshotPayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.stationPos());
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
        ByteBufCodecs.VAR_INT.encode(buf, payload.reputation());
        ByteBufCodecs.VAR_INT.encode(buf, payload.bonusPercent());
        ByteBufCodecs.VAR_INT.encode(buf, payload.reputationTier());
        ByteBufCodecs.VAR_INT.encode(buf, payload.reputationNextThreshold());
        buf.writeBoolean(payload.canRename());
        BoardSnapshotPayload.BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)).encode(buf, payload.orders());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.nextVillageRefreshGameTime());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.snapshotOverworldGameTime());
        View.STREAM_CODEC.encode(buf, payload.view());
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
                BoardSnapshotPayload.BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)).decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf),
                View.STREAM_CODEC.decode(buf));
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
