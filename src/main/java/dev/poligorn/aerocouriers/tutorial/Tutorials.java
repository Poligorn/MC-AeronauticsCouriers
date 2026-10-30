package dev.poligorn.aerocouriers.tutorial;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersConfigEditor;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.NoticeKind;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.task.TaskSync;
import dev.poligorn.aerocouriers.task.TaskView;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Onboarding (one safe order: reward 0, collateral 0, no deadline, +XP once). Only the courier side needs it: the
 * waybill works without license or tutorial. All state lives in {@link TutorialData}; the order is a regular
 * {@link dev.poligorn.aerocouriers.contract.ContractType#TUTORIAL} contract so container, beams and delivery checks
 * are the same as for real orders.
 */
public final class Tutorials {
    /** Issuer of tutorial orders («Courier service»): not a player and never paid. */
    public static final UUID SERVICE = UUID.nameUUIDFromBytes("aerocouriers:courier_service".getBytes());
    public static final int ZONE_B_RADIUS = 8;
    private static final int TEMP_A_RADIUS = 6;
    private static final int ACTION_BAR_INTERVAL = 20;

    private Tutorials() {
    }

    public static boolean enabled() {
        return !CouriersServerConfig.SPEC.isLoaded() || CouriersServerConfig.TUTORIAL_ENABLED.get();
    }

    public static TutorialData.Entry entry(ServerPlayer player) {
        return TutorialData.get(player.server).get(player.getUUID());
    }

    private static void put(ServerPlayer player, TutorialData.Entry entry) {
        TutorialData.get(player.server).put(player.getUUID(), entry);
        TaskSync.sync(player);
    }

    /** First tablet open without any license: offer the welcome window once. */
    public static void onBoardOpened(ServerPlayer player) {
        if (!enabled()) {
            return;
        }
        TutorialData.Entry e = entry(player);
        if (e.state() == TutorialData.State.NOT_STARTED && Licenses.profile(player).licensesIssued() <= 0
                && !Licenses.holdsPilotLicense(player)) {
            put(player, e.withState(TutorialData.State.OFFERED));
        }
    }

    public static Component start(ServerPlayer player) {
        if (!enabled()) {
            return Component.translatable("tutorial.aerocouriers.disabled");
        }
        TutorialData.Entry e = entry(player);
        if (e.state().inProgress()) {
            return Component.translatable("tutorial.aerocouriers.already");
        }
        e = e.withState(TutorialData.State.STARTED).withCompletionPending(false);
        put(player, e);
        if (Licenses.isLicensedCourier(player)) {
            issueOrder(player);
        }
        BoardPackets.syncSnapshot(player);
        return Component.translatable("tutorial.aerocouriers.started");
    }

    public static void skip(ServerPlayer player) {
        TutorialData.Entry e = entry(player);
        e.orderId().ifPresent(id -> ContractManager.get(player.server).removeTutorialOrder(player.server, id));
        put(player, e.withState(TutorialData.State.SKIPPED).withOrder(Optional.empty(), false)
                .withCompletionPending(false));
        BoardPackets.syncSnapshot(player);
    }

    public static void ackCompletion(ServerPlayer player) {
        TutorialData.Entry e = entry(player);
        if (e.completionPending()) {
            put(player, e.withCompletionPending(false));
        }
        BoardPackets.syncSnapshot(player);
    }

    public static void hintSeen(ServerPlayer player, int bit) {
        TutorialData.Entry e = entry(player);
        if ((e.hintsSeen() & bit) == 0) {
            put(player, e.withHint(bit));
        }
    }

    /** Free trainee license was just issued. */
    public static void onLicenseClaimed(ServerPlayer player) {
        CouriersNotifications.notify(player.server, player.getUUID(),
                Component.translatable("license.aerocouriers.granted"), Optional.empty(), NoticeKind.MONEY);
        TutorialData.Entry e = entry(player);
        if (enabled() && e.state() == TutorialData.State.STARTED && e.orderId().isEmpty()) {
            issueOrder(player);
        }
    }

    private static void issueOrder(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        Optional<StationAtlas.Station> station = nearestStation(player);
        BlockPos a = station.map(StationAtlas.Station::pos).orElseGet(() -> nearPlayer(level, player, random));
        int aRadius = station.isPresent() ? StationContractSource.ZONE_RADIUS : TEMP_A_RADIUS;
        BlockPos b = pointB(level, a, random);
        Destination origin = new Destination(level.dimension(), a.immutable(), aRadius);
        Destination destination = new Destination(level.dimension(), b.immutable(), ZONE_B_RADIUS);
        Contract order = ContractManager.get(player.server).publishTutorial(player, origin, destination);
        TutorialData.Entry e = entry(player);
        put(player, e.withOrder(Optional.of(order.id()), station.isEmpty()));
        CouriersNotifications.notify(player.server, player.getUUID(),
                Component.translatable("tutorial.aerocouriers.mail.order_ready", order.shortId()),
                Optional.of(order.id()), NoticeKind.TUTORIAL);
        AeronauticsCouriers.LOGGER.info("[tutorial] {} order {}: A={} (station={}), B={}",
                player.getGameProfile().getName(), order.shortId(), a.toShortString(), station.isPresent(),
                b.toShortString());
    }

    public static void onAccepted(ServerPlayer player) {
        TutorialData.Entry e = entry(player);
        put(player, e.withState(TutorialData.State.ACCEPTED));
    }

    public static void onAbandoned(MinecraftServer server, UUID player) {
        TutorialData data = TutorialData.get(server);
        TutorialData.Entry e = data.get(player);
        data.put(player, e.withState(TutorialData.State.STARTED).nextAttempt());
        TaskSync.syncLater(player);
    }

    public static void onLost(MinecraftServer server, UUID player) {
        TutorialData data = TutorialData.get(server);
        data.put(player, data.get(player).withState(TutorialData.State.ACCEPTED).nextAttempt());
        TaskSync.syncLater(player);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.displayClientMessage(Component.translatable("tutorial.aerocouriers.bar.lost"), true);
        }
    }

    /** Order removed by an operator: same as switching the tutorial off for this player. */
    public static void onRemovedByAdmin(MinecraftServer server, UUID player) {
        TutorialData data = TutorialData.get(server);
        data.put(player, data.get(player).withState(TutorialData.State.DISABLED_REMOVED)
                .withOrder(Optional.empty(), false));
        TaskSync.syncLater(player);
    }

    public static void onDelivered(ServerPlayer player) {
        TutorialData.Entry e = entry(player);
        int xp = CouriersServerConfig.TUTORIAL_XP.get();
        if (!e.xpAwarded() && xp > 0) {
            CourierProfile profile = Licenses.profile(player);
            Licenses.setProfile(player, profile.withXp(profile.xp() + xp));
            e = e.withXpAwarded();
        }
        put(player, e.withState(TutorialData.State.DONE).withOrder(Optional.empty(), e.noStation())
                .withCompletionPending(true));
        CouriersNotifications.notify(player.server, player.getUUID(),
                Component.translatable("tutorial.aerocouriers.mail.done", xp), Optional.empty(), NoticeKind.TUTORIAL);
    }

    /** Admin toggle and command: same value as {@code tutorial.tutorial_enabled}, applies at once. */
    public static Component setEnabled(MinecraftServer server, boolean on) {
        CouriersConfigEditor.setTutorialEnabled(on);
        TutorialData data = TutorialData.get(server);
        int removed = 0;
        for (Map.Entry<UUID, TutorialData.Entry> row : data.all().entrySet()) {
            TutorialData.Entry e = row.getValue();
            if (!on && (e.state().inProgress() || e.state() == TutorialData.State.OFFERED)) {
                e.orderId().ifPresent(id -> ContractManager.get(server).removeTutorialOrder(server, id));
                data.put(row.getKey(), e.withState(e.state() == TutorialData.State.OFFERED
                        ? TutorialData.State.NOT_STARTED : TutorialData.State.DISABLED_REMOVED)
                        .withOrder(Optional.empty(), false));
                removed++;
            } else if (on && e.state() == TutorialData.State.DISABLED_REMOVED) {
                data.put(row.getKey(), e.withState(TutorialData.State.NOT_STARTED));
            }
            TaskSync.syncLater(row.getKey());
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BoardPackets.syncSnapshot(player);
        }
        AeronauticsCouriers.LOGGER.info("[tutorial] enabled={} (removed {} active tutorial orders)", on, removed);
        return Component.translatable(on ? "admin.aerocouriers.tutorial.on" : "admin.aerocouriers.tutorial.off",
                removed);
    }

    /** Checklist «Training N/4» as a task in the shared panel. */
    public static Optional<TaskView> task(ServerPlayer player) {
        TutorialData.Entry e = entry(player);
        if (!enabled() || !e.state().inProgress()) {
            return Optional.empty();
        }
        ContractManager manager = ContractManager.get(player.server);
        Optional<Contract> order = e.orderId().flatMap(manager::byId);
        int current;
        if (!Licenses.isLicensedCourier(player)) {
            current = 0;
        } else if (order.isEmpty() || order.get().state() == ContractState.OPEN) {
            current = 1;
        } else if (!manager.cargoPickedUp(order.get().id())) {
            current = 2;
        } else {
            current = 3;
        }
        return Optional.of(TutorialTask.of(current, order.map(Contract::id)));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % ACTION_BAR_INTERVAL != 0 || !enabled()) {
            return;
        }
        TutorialData data = TutorialData.get(server);
        ContractManager manager = ContractManager.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TutorialData.Entry e = data.get(player.getUUID());
            if (e.orderId().isEmpty() || (e.state() != TutorialData.State.ACCEPTED
                    && e.state() != TutorialData.State.IN_TRANSIT)) {
                continue;
            }
            Optional<Contract> found = manager.byId(e.orderId().get());
            if (found.isEmpty() || !found.get().state().isActive()) {
                continue;
            }
            Contract order = found.get();
            if (order.state() == ContractState.IN_TRANSIT && e.state() == TutorialData.State.ACCEPTED) {
                data.put(player.getUUID(), e.withState(TutorialData.State.IN_TRANSIT));
                TaskSync.sync(player);
            }
            actionBar(player, order, manager).ifPresent(text -> player.displayClientMessage(text, true));
        }
    }

    private static Optional<Component> actionBar(ServerPlayer player, Contract order, ContractManager manager) {
        if (!player.level().dimension().equals(order.origin().dimension())) {
            return Optional.of(Component.translatable("tutorial.aerocouriers.bar.wrong_dimension"));
        }
        if (order.state() == ContractState.ACCEPTED) {
            int toA = (int) Math.sqrt(player.blockPosition().distSqr(order.origin().pos()));
            return Optional.of(Component.translatable("tutorial.aerocouriers.bar.go_a", toA));
        }
        Optional<CargoTracker.Snapshot> crate = CargoTracker.get(order.id());
        if (!manager.cargoPickedUp(order.id())) {
            return Optional.of(Component.translatable("tutorial.aerocouriers.bar.at_a"));
        }
        if (crate.isPresent()) {
            Vec3 at = new Vec3(crate.get().position().x(), crate.get().position().y(), crate.get().position().z());
            if (order.destination().contains(crate.get().dimension(), at)) {
                return Optional.of(Component.translatable("tutorial.aerocouriers.bar.in_b"));
            }
            int toB = (int) Math.sqrt(at.distanceToSqr(Vec3.atCenterOf(order.destination().pos())));
            return Optional.of(Component.translatable("tutorial.aerocouriers.bar.carry", toB));
        }
        return Optional.of(Component.translatable("tutorial.aerocouriers.bar.carry_unknown"));
    }

    private static Optional<StationAtlas.Station> nearestStation(ServerPlayer player) {
        int radius = CouriersServerConfig.TUTORIAL_STATION_RADIUS.get();
        BlockPos at = player.blockPosition();
        return StationAtlas.get(player.server).all().stream()
                .filter(s -> s.dimension().equals(player.level().dimension()))
                .filter(s -> s.pos().distSqr(at) <= (double) radius * radius)
                .min(Comparator.comparingDouble(s -> s.pos().distSqr(at)));
    }

    private static BlockPos nearPlayer(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos base = player.blockPosition();
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 6 + random.nextInt(5);
            Optional<BlockPos> spot = surface(level, base.getX() + (int) Math.round(Math.cos(angle) * dist),
                    base.getZ() + (int) Math.round(Math.sin(angle) * dist), base.getY(), 6);
            if (spot.isPresent()) {
                return spot.get();
            }
        }
        return base;
    }

    /** Safe surface 60–120 blocks from A: loaded chunk, solid ground, no fluid, no big drop from A's height. */
    private static BlockPos pointB(ServerLevel level, BlockPos a, RandomSource random) {
        for (int maxDy : List.of(16, 48)) {
            for (int i = 0; i < 32; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                int dist = 60 + random.nextInt(61);
                Optional<BlockPos> spot = surface(level, a.getX() + (int) Math.round(Math.cos(angle) * dist),
                        a.getZ() + (int) Math.round(Math.sin(angle) * dist), a.getY(), maxDy);
                if (spot.isPresent()) {
                    return spot.get();
                }
            }
        }
        return a.offset(80, 0, 0);
    }

    private static Optional<BlockPos> surface(ServerLevel level, int x, int z, int refY, int maxDy) {
        BlockPos column = new BlockPos(x, refY, z);
        if (!level.isLoaded(column)) {
            return Optional.empty();
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1 || Mth.abs(y - refY) > maxDy) {
            return Optional.empty();
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos below = pos.below();
        boolean dry = level.getFluidState(pos).isEmpty() && level.getFluidState(below).isEmpty();
        boolean solid = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        return dry && solid ? Optional.of(pos) : Optional.empty();
    }
}
