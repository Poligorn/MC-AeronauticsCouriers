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
        List<BoardSnapshotPayload.StationHint> stations,
        int collateralProfile
) implements CustomPacketPayload {
    public static final Type<OpenWaybillCreatePayload> TYPE = new Type<>(AeronauticsCouriers.id("open_waybill_create"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> CARGO_CODEC =
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.WIRE_ITEM_STACK_LIST));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<BoardSnapshotPayload.StationHint>> STATIONS_CODEC =
            BoardSnapshotPayload.StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(64));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWaybillCreatePayload> STREAM_CODEC =
            StreamCodec.of(OpenWaybillCreatePayload::write, OpenWaybillCreatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OpenWaybillCreatePayload p) {
        BlockPos.STREAM_CODEC.encode(buf, p.origin);
        CARGO_CODEC.encode(buf, p.cargo);
        buf.writeDouble(p.weight);
        buf.writeVarLong(p.balance);
        buf.writeBoolean(p.numismaticsEconomy);
        STATIONS_CODEC.encode(buf, p.stations);
        buf.writeVarInt(p.collateralProfile);
    }

    private static OpenWaybillCreatePayload read(RegistryFriendlyByteBuf buf) {
        return new OpenWaybillCreatePayload(BlockPos.STREAM_CODEC.decode(buf), CARGO_CODEC.decode(buf),
                buf.readDouble(), buf.readVarLong(), buf.readBoolean(), STATIONS_CODEC.decode(buf), buf.readVarInt());
    }

    public dev.poligorn.aerocouriers.contract.CollateralProfile profile() {
        return dev.poligorn.aerocouriers.contract.CollateralProfile.byOrdinal(collateralProfile);
    }

    public static void handle(OpenWaybillCreatePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientBoard.openWaybillCreate(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
