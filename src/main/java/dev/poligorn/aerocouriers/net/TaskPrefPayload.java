package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: toggle «show on screen» / «pin» of one task, or the auto-track switch ({@code taskId} ignored). */
public record TaskPrefPayload(String taskId, Op op) implements CustomPacketPayload {
    public enum Op { TRACK, PIN, AUTO_TRACK }

    public static final Type<TaskPrefPayload> TYPE = new Type<>(AeronauticsCouriers.id("task_pref"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaskPrefPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), TaskPrefPayload::taskId,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            TaskPrefPayload::op,
            TaskPrefPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
