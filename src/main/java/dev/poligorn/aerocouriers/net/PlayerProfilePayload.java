package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientBoard;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: public profile for the board tab (nickname, rank, license, issuer stats). */
public record PlayerProfilePayload(UUID targetId, BoardSnapshotPayload.BoardProfile profile)
        implements CustomPacketPayload {
    public static final Type<PlayerProfilePayload> TYPE = new Type<>(AeronauticsCouriers.id("player_profile"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerProfilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, PlayerProfilePayload::targetId,
                    BoardSnapshotPayload.BoardProfile.STREAM_CODEC, PlayerProfilePayload::profile,
                    PlayerProfilePayload::new);

    public static void handle(PlayerProfilePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientBoard.applyPlayerProfile(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
