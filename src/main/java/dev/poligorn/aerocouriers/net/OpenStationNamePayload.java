package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: open the station-name dialog for the player who just placed the block. */
public record OpenStationNamePayload(BlockPos pos, List<String> takenNames) implements CustomPacketPayload {
    public static final Type<OpenStationNamePayload> TYPE = new Type<>(AeronauticsCouriers.id("open_station_name"));
    public static final int MAX_NAMES = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStationNamePayload> STREAM_CODEC =
            StreamCodec.of(OpenStationNamePayload::write, OpenStationNamePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OpenStationNamePayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.pos);
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_NAMES)).encode(buf, payload.takenNames);
    }

    private static OpenStationNamePayload read(RegistryFriendlyByteBuf buf) {
        return new OpenStationNamePayload(BlockPos.STREAM_CODEC.decode(buf),
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_NAMES)).decode(buf));
    }

    public boolean nameTaken(String name) {
        String want = name == null ? "" : name.strip();
        if (want.isEmpty()) {
            return false;
        }
        return takenNames.stream().anyMatch(n -> n.equalsIgnoreCase(want));
    }

    public static void handle(OpenStationNamePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> dev.poligorn.aerocouriers.client.ClientStation.openName(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
