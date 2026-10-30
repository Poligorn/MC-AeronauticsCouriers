package dev.poligorn.aerocouriers.task;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.CourierInbox;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.RankProgress;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.tutorial.Tutorials;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Builds the task list of one player from existing state (contracts, inbox, profile, tutorial). */
public final class Tasks {
    /** Finished orders stay in the «done today» group for one Minecraft day. */
    private static final long DONE_WINDOW = 24_000L;
    private static final long URGENT_TICKS = 5L * Pricing.TICKS_PER_MINUTE;

    private Tasks() {
    }

    public static List<TaskView> compute(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID me = player.getUUID();
        long now = ContractManager.now(server);
        ContractManager manager = ContractManager.get(server);
        TaskPrefs prefs = TaskPrefs.get(server);
        boolean autoTrack = prefs.autoTrack(me);
        List<TaskView> raw = new ArrayList<>();

        Tutorials.task(player).ifPresent(raw::add);

        manager.byCourier(me).forEach(c -> {
            if (c.type().isTutorial()) {
                return;
            }
            if (c.state().isActive()) {
                raw.add(courierTask(c, manager.cargoPickedUp(c.id()), now));
            } else if (c.state().isFinal() && c.timing().acceptedAt() >= 0 && now - c.timing().acceptedAt() < DONE_WINDOW) {
                raw.add(doneTask("order:" + c.id(), TaskView.Source.ORDER_COURIER, c));
            }
        });
        manager.byIssuer(me).forEach(c -> {
            if (c.type().isNpc() || c.type().isTutorial() || c.isCourier(me)) {
                return;
            }
            if (c.state() == ContractState.OPEN || c.state().isActive()) {
                raw.add(customerTask(c, now));
            } else if (c.state().isFinal() && now - c.timing().createdAt() < DONE_WINDOW) {
                raw.add(doneTask("mine:" + c.id(), TaskView.Source.ORDER_CUSTOMER, c));
            }
        });

        int mailItems = 0;
        long mailAt = 0;
        for (CourierInbox.Message m : CourierInbox.get(server).messages(me)) {
            if (!m.items().isEmpty()) {
                mailItems += CourierInbox.Message.totalItemCount(m.items());
                mailAt = Math.max(mailAt, m.at());
            }
        }
        if (mailItems > 0) {
            raw.add(new TaskView("mail", TaskView.Source.MAIL,
                    Component.translatable("ui.aerocouriers.tasks.mail.title", mailItems),
                    TaskView.steps(0, Component.translatable("ui.aerocouriers.tasks.mail.step")),
                    -1f, -1L, false, false, false, TaskView.Action.OPEN_MAIL, Optional.empty(), mailAt, false));
        }

        rankGoal(player).ifPresent(raw::add);

        Set<String> live = new HashSet<>();
        List<TaskView> out = new ArrayList<>(raw.size());
        for (TaskView t : raw) {
            live.add(t.id());
            int f = prefs.flags(me, t.id());
            boolean byDefault = switch (t.source()) {
                case TUTORIAL -> true;
                case ORDER_COURIER, ORDER_CUSTOMER -> autoTrack && !t.done();
                case RANK_GOAL, MAIL -> false;
            };
            boolean tracked = (f & TaskPrefs.TRACKED) != 0 || (byDefault && (f & TaskPrefs.UNTRACKED) == 0);
            out.add(t.withPrefs(tracked && !t.done(), (f & TaskPrefs.PINNED) != 0 && !t.done()));
            if (out.size() >= TaskView.MAX_TASKS) {
                break;
            }
        }
        prefs.retain(me, live);
        return out;
    }

    private static TaskView courierTask(Contract c, boolean pickedUp, long now) {
        int current = c.state() == ContractState.ACCEPTED || !pickedUp ? 1 : 2;
        List<TaskView.Step> steps = new ArrayList<>(TaskView.steps(current,
                Component.translatable("ui.aerocouriers.tasks.order.step.accepted"),
                Component.translatable("ui.aerocouriers.tasks.order.step.pickup"),
                Component.translatable("ui.aerocouriers.tasks.order.step.carry"),
                Component.translatable("ui.aerocouriers.tasks.order.step.deliver")));
        steps.set(1, new TaskView.Step(steps.get(1).text(), steps.get(1).state(), 1));
        steps.set(2, new TaskView.Step(steps.get(2).text(), steps.get(2).state(), 2));
        long due = c.timing().dueAt();
        boolean urgent = due - now <= URGENT_TICKS;
        return new TaskView("order:" + c.id(), TaskView.Source.ORDER_COURIER,
                Component.translatable("ui.aerocouriers.tasks.order.title", c.shortId()), steps, -1f, due, urgent,
                false, false, TaskView.Action.SHOW_ON_MAP, Optional.of(c.id()), c.timing().acceptedAt(), false);
    }

    private static TaskView customerTask(Contract c, long now) {
        int current = switch (c.state()) {
            case OPEN -> 0;
            case ACCEPTED -> 1;
            default -> 2;
        };
        List<TaskView.Step> steps = TaskView.steps(current,
                Component.translatable("ui.aerocouriers.tasks.mine.step.waiting"),
                Component.translatable("ui.aerocouriers.tasks.mine.step.accepted"),
                Component.translatable("ui.aerocouriers.tasks.mine.step.transit"),
                Component.translatable("ui.aerocouriers.tasks.mine.step.delivered"));
        long deadline = c.state() == ContractState.OPEN ? c.timing().expiresAt() : c.timing().dueAt();
        return new TaskView("mine:" + c.id(), TaskView.Source.ORDER_CUSTOMER,
                Component.translatable("ui.aerocouriers.tasks.mine.title", c.shortId()), steps, -1f, deadline, false,
                false, false, TaskView.Action.OPEN_ORDER, Optional.of(c.id()), c.timing().createdAt(), false);
    }

    private static TaskView doneTask(String id, TaskView.Source source, Contract c) {
        String key = c.state() == ContractState.DELIVERED ? "ui.aerocouriers.tasks.done.delivered"
                : c.state() == ContractState.EXPIRED ? "ui.aerocouriers.tasks.done.expired"
                : "ui.aerocouriers.tasks.done.failed";
        Component title = Component.translatable(source == TaskView.Source.ORDER_COURIER
                ? "ui.aerocouriers.tasks.order.title" : "ui.aerocouriers.tasks.mine.title", c.shortId());
        return new TaskView(id, source, title, TaskView.steps(1, Component.translatable(key)), 1f, -1L, false,
                false, false, TaskView.Action.OPEN_ORDER, Optional.of(c.id()), c.timing().createdAt(), true);
    }

    private static Optional<TaskView> rankGoal(ServerPlayer player) {
        if (Licenses.profile(player).licensesIssued() <= 0 && !Licenses.isLicensedCourier(player)) {
            return Optional.empty();
        }
        CourierProfile profile = Licenses.profile(player);
        LicenseRank rank = Licenses.rankOf(player);
        if (rank.isAdmin()) {
            return Optional.empty();
        }
        List<Integer> thresholds = new ArrayList<>();
        for (Integer xp : CouriersServerConfig.RANK_XP.get()) {
            thresholds.add(xp);
        }
        RankProgress.State state = RankProgress.state(profile.xp(), rank, thresholds);
        if (state.maxRank()) {
            return Optional.empty();
        }
        LicenseRank next = RankProgress.nextRank(rank);
        return Optional.of(new TaskView("rank:" + next.getSerializedName(), TaskView.Source.RANK_GOAL,
                Component.translatable("ui.aerocouriers.tasks.rank.title", next.displayName()),
                TaskView.steps(0, Component.translatable("ui.aerocouriers.tasks.rank.step",
                        state.xpInRank(), state.span())),
                state.fraction(), -1L, false, false, false, TaskView.Action.NONE, Optional.empty(), 0L, false));
    }
}
