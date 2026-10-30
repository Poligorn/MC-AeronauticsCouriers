package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * S2C: the pickup/delivery zones of the receiving courier's active contracts (GDD §10.3), replacing whatever the
 * client had. Sent on login and on every state change of one of the courier's contracts.
 */
public record ZoneSyncPayload(List<Zone> zones) implements CustomPacketPayload {
    public static final Type<ZoneSyncPayload> TYPE = new Type<>(AeronauticsCouriers.id("zone_sync"));

    /**
     * @param delivery  {@code false} = pickup zone (contract accepted), {@code true} = destination (in transit)
     * @param label     station name or coordinates of this zone
     * @param ticksLeft server ticks until the deadline at send time; negative once late
     * @param next      destination zone while this is the pickup zone (same dimension only)
     */
    public record Zone(UUID orderId, String contract, ResourceKey<Level> dimension, BlockPos pos, int radius,
                       boolean delivery, String label, long ticksLeft, Optional<Next> next) {
        public static final StreamCodec<ByteBuf, Zone> STREAM_CODEC = StreamCodec.of(Zone::write, Zone::read);

        private static void write(ByteBuf buf, Zone zone) {
            UUIDUtil.STREAM_CODEC.encode(buf, zone.orderId);
            ByteBufCodecs.STRING_UTF8.encode(buf, zone.contract);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, zone.dimension);
            BlockPos.STREAM_CODEC.encode(buf, zone.pos);
            ByteBufCodecs.VAR_INT.encode(buf, zone.radius);
            ByteBufCodecs.BOOL.encode(buf, zone.delivery);
            ByteBufCodecs.STRING_UTF8.encode(buf, zone.label);
            ByteBufCodecs.VAR_LONG.encode(buf, zone.ticksLeft);
            Next.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, zone.next);
        }

        private static Zone read(ByteBuf buf) {
            return new Zone(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    Next.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buf));
        }
    }

    /** The leg after pickup, so the client can preview A→B before the cargo is taken. */
    public record Next(BlockPos pos, int radius, String label) {
        public static final StreamCodec<ByteBuf, Next> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Next::pos,
                ByteBufCodecs.VAR_INT, Next::radius,
                ByteBufCodecs.STRING_UTF8, Next::label,
                Next::new);
    }

    public static final StreamCodec<ByteBuf, ZoneSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Zone.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ZoneSyncPayload::zones,
            ZoneSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
