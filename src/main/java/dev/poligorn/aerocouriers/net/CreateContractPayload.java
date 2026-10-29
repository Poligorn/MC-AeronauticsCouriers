package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * C2S: publish a P2P contract. Same arguments as {@code /couriers contract create}; cargo is taken from the
 * container at {@code from}, the player must stand within {@link dev.poligorn.aerocouriers.contract.ContractManager#ISSUER_REACH}.
 */
public record CreateContractPayload(
        BlockPos from,
        BlockPos to,
        ResourceKey<Level> destinationDimension,
        long reward,
        long collateral,
        int minutes,
        int radius,
        int minRankLevel
) implements CustomPacketPayload {
    public static final Type<CreateContractPayload> TYPE = new Type<>(AeronauticsCouriers.id("create_contract"));
    public static final long DEFAULT_REWARD = 10;
    public static final long DEFAULT_COLLATERAL = 0;
    public static final int DEFAULT_RADIUS = 8;

    public static final StreamCodec<ByteBuf, CreateContractPayload> STREAM_CODEC = StreamCodec.of(
            CreateContractPayload::write,
            CreateContractPayload::read);

    private static void write(ByteBuf buf, CreateContractPayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.from);
        BlockPos.STREAM_CODEC.encode(buf, payload.to);
        ResourceLocation.STREAM_CODEC.encode(buf, payload.destinationDimension.location());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.reward);
        ByteBufCodecs.VAR_LONG.encode(buf, payload.collateral);
        ByteBufCodecs.VAR_INT.encode(buf, payload.minutes);
        ByteBufCodecs.VAR_INT.encode(buf, payload.radius);
        ByteBufCodecs.VAR_INT.encode(buf, payload.minRankLevel);
    }

    private static CreateContractPayload read(ByteBuf buf) {
        BlockPos from = BlockPos.STREAM_CODEC.decode(buf);
        BlockPos to = BlockPos.STREAM_CODEC.decode(buf);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.STREAM_CODEC.decode(buf));
        long reward = ByteBufCodecs.VAR_LONG.decode(buf);
        long collateral = ByteBufCodecs.VAR_LONG.decode(buf);
        int minutes = ByteBufCodecs.VAR_INT.decode(buf);
        int radius = ByteBufCodecs.VAR_INT.decode(buf);
        int minRankLevel = ByteBufCodecs.VAR_INT.decode(buf);
        return new CreateContractPayload(from, to, dimension, reward, collateral, minutes, radius, minRankLevel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
