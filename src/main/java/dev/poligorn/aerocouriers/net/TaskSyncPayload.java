package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientTasks;
import dev.poligorn.aerocouriers.task.TaskView;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: the viewer's whole task list plus server game time, sent only when something changes. */
public record TaskSyncPayload(long worldTime, List<TaskView> tasks, boolean autoTrack) implements CustomPacketPayload {
    public static final Type<TaskSyncPayload> TYPE = new Type<>(AeronauticsCouriers.id("task_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaskSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TaskSyncPayload::worldTime,
            TaskView.STREAM_CODEC.apply(ByteBufCodecs.list(TaskView.MAX_TASKS)), TaskSyncPayload::tasks,
            ByteBufCodecs.BOOL, TaskSyncPayload::autoTrack,
            TaskSyncPayload::new);

    public static void handle(TaskSyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientTasks.apply(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
