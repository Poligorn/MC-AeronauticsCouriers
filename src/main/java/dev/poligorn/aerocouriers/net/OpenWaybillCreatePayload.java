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
 * S2C: open the waybill create screen for the container at {@code origin}. Cargo is a snapshot; the server
 * re-reads the block on confirm.
 */
public record OpenWaybillCreatePayload(
        BlockPos origin,
        List<ItemStack> cargo,
        double weight,
        long balance,
        boolean numismaticsEconomy,
        List<BoardSnapshotPayload.StationHint> stations
) implements CustomPacketPayload {
    public static final Type<OpenWaybillCreatePayload> TYPE = new Type<>(AeronauticsCouriers.id("open_waybill_create"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWaybillCreatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenWaybillCreatePayload::origin,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.WIRE_ITEM_STACK_LIST)),
                    OpenWaybillCreatePayload::cargo,
                    ByteBufCodecs.DOUBLE, OpenWaybillCreatePayload::weight,
                    ByteBufCodecs.VAR_LONG, OpenWaybillCreatePayload::balance,
                    ByteBufCodecs.BOOL, OpenWaybillCreatePayload::numismaticsEconomy,
                    BoardSnapshotPayload.StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(64)),
                    OpenWaybillCreatePayload::stations,
                    OpenWaybillCreatePayload::new);

    public static void handle(OpenWaybillCreatePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientBoard.openWaybillCreate(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
