package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: publish a P2P contract. Same arguments as {@code /couriers contract create}; cargo is taken from the
 * container at {@code from}, the player must stand within {@link dev.poligorn.aerocouriers.contract.ContractManager#ISSUER_REACH}.
 */
public record CreateContractPayload(BlockPos from, BlockPos to, long reward, long collateral, int minutes, int radius)
        implements CustomPacketPayload {
    public static final Type<CreateContractPayload> TYPE = new Type<>(AeronauticsCouriers.id("create_contract"));
    public static final long DEFAULT_REWARD = 10;
    public static final long DEFAULT_COLLATERAL = 0;
    public static final int DEFAULT_RADIUS = 8;

    public static final StreamCodec<ByteBuf, CreateContractPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CreateContractPayload::from,
            BlockPos.STREAM_CODEC, CreateContractPayload::to,
            ByteBufCodecs.VAR_LONG, CreateContractPayload::reward,
            ByteBufCodecs.VAR_LONG, CreateContractPayload::collateral,
            ByteBufCodecs.VAR_INT, CreateContractPayload::minutes,
            ByteBufCodecs.VAR_INT, CreateContractPayload::radius,
            CreateContractPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
