package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.TaskPrefPayload;
import dev.poligorn.aerocouriers.net.TaskSyncPayload;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import dev.poligorn.aerocouriers.task.TaskView;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client copy of the server task list; timers and distances are derived locally between syncs. */
public final class ClientTasks {
    public static final int PANEL_ROWS = 4;
    private static List<TaskView> tasks = List.of();
    private static boolean autoTrack = true;
    private static long serverTime;
    private static long clientTimeAtSync;
    private static List<TaskView> liveSource;
    private static List<TaskView> live = List.of();
    private static List<TaskView> done = List.of();
    private static List<TaskView> onScreenCache = List.of();
    private static List<TaskView> activeCache = List.of();
    private static long sortedAtSecond = Long.MIN_VALUE;

    private ClientTasks() {
    }

    public static void apply(TaskSyncPayload payload) {
        tasks = List.copyOf(payload.tasks());
        autoTrack = payload.autoTrack();
        serverTime = payload.worldTime();
        liveSource = null;
        Minecraft mc = Minecraft.getInstance();
        clientTimeAtSync = mc.level != null ? mc.level.getGameTime() : 0L;
        if (mc.screen instanceof CourierBoardScreen screen) {
            screen.onTasksChanged();
        }
    }

    public static void clear() {
        tasks = List.of();
        liveSource = null;
    }

    public static List<TaskView> all() {
        return tasks;
    }

    public static boolean autoTrack() {
        return autoTrack;
    }

    public static Optional<TaskView> byId(String id) {
        return tasks.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public static long now() {
        Minecraft mc = Minecraft.getInstance();
        long elapsed = mc.level != null ? mc.level.getGameTime() - clientTimeAtSync : 0L;
        return serverTime + Math.max(0L, elapsed);
    }

    /** Ticks left until {@code deadline}, or {@code Long.MAX_VALUE} without one. */
    public static long ticksLeft(TaskView task) {
        return task.deadline() <= 0 ? Long.MAX_VALUE : task.deadline() - now();
    }

    public static boolean urgent(TaskView task) {
        return task.urgent() || ticksLeft(task) <= 5L * 60 * 20;
    }

    private static final Comparator<TaskView> ORDER = Comparator
            .comparing((TaskView t) -> !urgent(t))
            .thenComparing(t -> !t.pinned())
            .thenComparingLong(TaskView::createdAt);

    public static List<TaskView> active() {
        refreshDerived();
        return activeCache;
    }

    /** Finished tasks in packet order. */
    public static List<TaskView> done() {
        refreshDerived();
        return done;
    }

    /** Tracked, unfinished tasks in panel order: urgent, pinned, then by appearance. */
    public static List<TaskView> onScreen() {
        refreshDerived();
        return onScreenCache;
    }

    private static void refreshDerived() {
        if (liveSource != tasks) {
            live = tasks.stream().filter(t -> !t.done()).toList();
            done = tasks.stream().filter(TaskView::done).toList();
            liveSource = tasks;
            sortedAtSecond = Long.MIN_VALUE;
        }
        long second = now() / 20;
        if (second == sortedAtSecond) {
            return;
        }
        sortedAtSecond = second;
        activeCache = live.stream().sorted(ORDER).toList();
        onScreenCache = activeCache.stream().filter(TaskView::tracked).toList();
    }

    public static Optional<ZoneSyncPayload.Zone> zone(TaskView task, int which) {
        if (task.contractId().isEmpty() || which == 0) {
            return Optional.empty();
        }
        UUID id = task.contractId().get();
        return ClientZones.get().stream()
                .filter(z -> z.orderId().equals(id) && z.delivery() == (which == 2))
                .findFirst();
    }

    /** Blocks from the player to the zone of {@code step}, or -1 when unknown (other dimension, no zone yet). */
    public static int distance(TaskView task, TaskView.Step step) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        return zone(task, step.zone())
                .filter(z -> z.dimension().equals(mc.player.level().dimension()))
                .map(z -> (int) Math.sqrt(mc.player.blockPosition().distSqr(z.pos())))
                .orElse(-1);
    }

    public static void toggleTracked(String id) {
        PacketDistributor.sendToServer(new TaskPrefPayload(id, TaskPrefPayload.Op.TRACK));
    }

    public static void togglePinned(String id) {
        PacketDistributor.sendToServer(new TaskPrefPayload(id, TaskPrefPayload.Op.PIN));
    }

    public static void toggleAutoTrack() {
        PacketDistributor.sendToServer(new TaskPrefPayload("", TaskPrefPayload.Op.AUTO_TRACK));
    }
}
