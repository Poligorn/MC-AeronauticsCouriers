package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientBoard;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * S2C: open the stamp create screen for the container at {@code origin}. Cargo is a snapshot; the server
 * re-reads the block on confirm.
 */
public record OpenStampPayload(
        BlockPos origin,
        List<ItemStack> cargo,
        double weight,
        long balance,
        List<BoardSnapshotPayload.StationHint> stations
) implements CustomPacketPayload {
    public static final Type<OpenStampPayload> TYPE = new Type<>(AeronauticsCouriers.id("open_stamp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStampPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenStampPayload::origin,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(27)), OpenStampPayload::cargo,
                    ByteBufCodecs.DOUBLE, OpenStampPayload::weight,
                    ByteBufCodecs.VAR_LONG, OpenStampPayload::balance,
                    BoardSnapshotPayload.StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(64)),
                    OpenStampPayload::stations,
                    OpenStampPayload::new);

    public static void handle(OpenStampPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientBoard.openStamp(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
