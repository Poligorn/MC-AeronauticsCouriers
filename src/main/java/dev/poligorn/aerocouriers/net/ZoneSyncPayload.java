package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import java.util.List;
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

    /** @param delivery {@code false} = pickup zone (contract accepted), {@code true} = destination (in transit) */
    public record Zone(String contract, ResourceKey<Level> dimension, BlockPos pos, int radius, boolean delivery) {
        public static final StreamCodec<ByteBuf, Zone> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Zone::contract,
                ResourceKey.streamCodec(Registries.DIMENSION), Zone::dimension,
                BlockPos.STREAM_CODEC, Zone::pos,
                ByteBufCodecs.VAR_INT, Zone::radius,
                ByteBufCodecs.BOOL, Zone::delivery,
                Zone::new);
    }

    public static final StreamCodec<ByteBuf, ZoneSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Zone.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ZoneSyncPayload::zones,
            ZoneSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
