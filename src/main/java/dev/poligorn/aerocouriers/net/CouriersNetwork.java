package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends payloads after a dry-run encode so a codec bug never disconnects the player. */
public final class CouriersNetwork {
    private CouriersNetwork() {
    }

    public static void sendRegistryPayload(ServerPlayer player, CustomPacketPayload payload,
                                           StreamCodec<RegistryFriendlyByteBuf, ?> codec) {
        if (player.connection == null) {
            return;
        }
        try {
            RegistryFriendlyByteBuf probe = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            try {
                encode(codec, probe, payload);
            } finally {
                probe.release();
            }
        } catch (RuntimeException e) {
            AeronauticsCouriers.LOGGER.warn("Skipping payload {} to {}: {}", payload.type().id(),
                    player.getGameProfile().getName(), e.toString(), e);
            Component message = Component.translatable("ui.aerocouriers.error.packet_encode_failed");
            player.sendSystemMessage(message);
            player.displayClientMessage(message, true);
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    @SuppressWarnings("unchecked")
    private static void encode(StreamCodec<RegistryFriendlyByteBuf, ?> codec, RegistryFriendlyByteBuf buf,
                               CustomPacketPayload payload) {
        ((StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) codec).encode(buf, payload);
    }
}
