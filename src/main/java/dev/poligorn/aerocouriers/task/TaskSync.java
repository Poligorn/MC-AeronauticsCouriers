package dev.poligorn.aerocouriers.task;

import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.net.CouriersNetwork;
import dev.poligorn.aerocouriers.net.TaskPrefPayload;
import dev.poligorn.aerocouriers.net.TaskSyncPayload;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends {@link TaskSyncPayload} on state changes only. Changes in one tick are coalesced and flushed at the end of
 * the server tick, so a delivery that touches several systems still costs one packet.
 */
public final class TaskSync {
    private static final Set<UUID> PENDING = new LinkedHashSet<>();

    private TaskSync() {
    }

    public static synchronized void syncLater(UUID player) {
        PENDING.add(player);
    }

    public static void sync(ServerPlayer player) {
        syncLater(player.getUUID());
    }

    /** Sends immediately. A deferred flush never runs while the integrated server is not ticking. */
    public static void sendNow(ServerPlayer player) {
        synchronized (TaskSync.class) {
            PENDING.remove(player.getUUID());
        }
        send(player);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        List<UUID> batch;
        synchronized (TaskSync.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        MinecraftServer server = event.getServer();
        for (UUID id : batch) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                send(player);
            }
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncLater(player.getUUID());
        }
    }

    public static void send(ServerPlayer player) {
        if (!CouriersNetwork.canSend(player, TaskSyncPayload.TYPE)) {
            return;
        }
        List<TaskView> tasks = Tasks.compute(player);
        boolean autoTrack = TaskPrefs.get(player.server).autoTrack(player.getUUID());
        PacketDistributor.sendToPlayer(player, new TaskSyncPayload(ContractManager.now(player.server), tasks, autoTrack));
    }

    public static void handlePref(ServerPlayer player, TaskPrefPayload payload) {
        TaskPrefs prefs = TaskPrefs.get(player.server);
        UUID me = player.getUUID();
        switch (payload.op()) {
            case AUTO_TRACK -> prefs.setAutoTrack(me, !prefs.autoTrack(me));
            case TRACK, PIN -> {
                Optional<TaskView> task = Tasks.compute(player).stream()
                        .filter(t -> t.id().equals(payload.taskId()) && !t.done())
                        .findFirst();
                if (task.isEmpty()) {
                    break;
                }
                if (payload.op() == TaskPrefPayload.Op.TRACK) {
                    prefs.toggleTracked(me, payload.taskId(), task.get().tracked());
                } else {
                    prefs.togglePinned(me, payload.taskId());
                }
            }
        }
        send(player);
    }
}
