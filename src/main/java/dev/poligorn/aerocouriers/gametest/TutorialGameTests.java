package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.config.NoticeChannel;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.NoticeKind;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.task.TaskView;
import dev.poligorn.aerocouriers.tutorial.TutorialData;
import dev.poligorn.aerocouriers.tutorial.Tutorials;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Onboarding order (0.2.0): free to take, cannot fail, removed silently when the operator switches it off. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TutorialGameTests {
    private TutorialGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void tutorialOrderIsFreeAndReissuedOnAbandon(GameTestHelper helper) {
        ServerPlayer courier = TestPlayers.courier(helper, "trainee");
        ContractManager manager = ContractManager.get(helper.getLevel().getServer());
        Destination a = new Destination(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)), 8);
        Destination b = new Destination(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(9, 1, 9)), 8);
        long before = Economies.get(helper.getLevel().getServer()).balance(courier.getUUID());
        Contract order = manager.publishTutorial(courier, a, b);
        helper.assertValueEqual(order.type(), ContractType.TUTORIAL, "type");
        helper.assertValueEqual(order.reward(), 0L, "no reward");
        helper.assertValueEqual(order.collateral(), 0L, "no collateral");

        ContractManager.Outcome accepted = manager.accept(courier, order.id());
        helper.assertTrue(accepted.success(), "accept: " + accepted.message().getString());
        helper.assertValueEqual(manager.byId(order.id()).orElseThrow().state(), ContractState.ACCEPTED,
                "tutorial cargo is not preplaced");
        helper.assertValueEqual(Economies.get(helper.getLevel().getServer()).balance(courier.getUUID()), before,
                "nothing charged");

        ContractManager.Outcome abandoned = manager.abandon(courier, order.id());
        helper.assertTrue(abandoned.success(), "abandon: " + abandoned.message().getString());
        helper.assertValueEqual(manager.byId(order.id()).orElseThrow().state(), ContractState.OPEN,
                "abandon puts the order back on the board");

        manager.removeTutorialOrder(helper.getLevel().getServer(), order.id());
        helper.assertTrue(manager.byId(order.id()).orElseThrow().state().isFinal(), "removed order is final");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void switchingTutorialOffParksAndOnRestores(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean was = CouriersServerConfig.TUTORIAL_ENABLED.get();
        UUID running = UUID.randomUUID();
        UUID offered = UUID.randomUUID();
        UUID done = UUID.randomUUID();
        TutorialData data = TutorialData.get(server);
        data.put(running, TutorialData.Entry.EMPTY.withState(TutorialData.State.ACCEPTED));
        data.put(offered, TutorialData.Entry.EMPTY.withState(TutorialData.State.OFFERED));
        data.put(done, TutorialData.Entry.EMPTY.withState(TutorialData.State.DONE));
        try {
            Tutorials.setEnabled(server, false);
            helper.assertValueEqual(data.get(running).state(), TutorialData.State.DISABLED_REMOVED, "running parked");
            helper.assertValueEqual(data.get(offered).state(), TutorialData.State.NOT_STARTED, "offer withdrawn");
            helper.assertValueEqual(data.get(done).state(), TutorialData.State.DONE, "done untouched");
            Tutorials.setEnabled(server, true);
            helper.assertValueEqual(data.get(running).state(), TutorialData.State.NOT_STARTED, "parked restored");
            helper.assertValueEqual(data.get(done).state(), TutorialData.State.DONE, "done stays done");
        } finally {
            Tutorials.setEnabled(server, was);
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void extrasAndTasksRoundTrip(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        UUID order = UUID.randomUUID();
        BoardSnapshotPayload.Extras extras = new BoardSnapshotPayload.Extras("easy", List.of("a=b"), true,
                TutorialData.State.IN_TRANSIT.ordinal(), Optional.of(order), true, false, 100, 3, 25L, true);
        BoardSnapshotPayload.Extras.STREAM_CODEC.encode(buf, extras);
        TaskView task = new TaskView("tutorial", TaskView.Source.TUTORIAL, Component.literal("t"),
                List.of(new TaskView.Step(Component.literal("s"), TaskView.StepState.CURRENT, 2)), 0.5F, 1200L,
                true, true, false, TaskView.Action.SHOW_ON_MAP, Optional.of(order), 7L, false);
        TaskView.STREAM_CODEC.encode(buf, task);
        buf.readerIndex(0);
        BoardSnapshotPayload.Extras decoded = BoardSnapshotPayload.Extras.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded, extras, "extras round-trip");
        helper.assertTrue(decoded.isTutorialOrder(order), "tutorial order id");
        TaskView back = TaskView.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(back.currentStep(), 0, "current step");
        helper.assertValueEqual(back.steps().get(0).zone(), 2, "step zone");
        helper.assertValueEqual(back.contractId(), Optional.of(order), "contract id");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void urgentNoticesUseCriticalRowWithChat(GameTestHelper helper) {
        helper.assertValueEqual(NoticeChannel.Row.of(NoticeKind.URGENT), NoticeChannel.Row.CRITICAL, "urgent row");
        helper.assertTrue(NoticeChannel.Row.CRITICAL.defaultChat(), "critical goes to chat");
        for (NoticeChannel.Row row : NoticeChannel.Row.values()) {
            if (row != NoticeChannel.Row.CRITICAL) {
                helper.assertFalse(row.defaultChat(), row.key() + " stays out of chat by default");
            }
        }
        helper.assertFalse(NoticeChannel.Row.TUTORIAL.defaultSound(), "tutorial is silent");
        helper.assertValueEqual(NoticeKind.classify(Component.translatable(
                "contract.aerocouriers.notify.far_container_behind", "ab", 70)), NoticeKind.URGENT,
                "container left behind is urgent");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void legacyCollateralRatioMapsToNearestProfile(GameTestHelper helper) {
        helper.assertValueEqual(dev.poligorn.aerocouriers.contract.CollateralProfile.fromLegacyRatio(0.0),
                dev.poligorn.aerocouriers.contract.CollateralProfile.PEACEFUL, "0");
        helper.assertValueEqual(dev.poligorn.aerocouriers.contract.CollateralProfile.fromLegacyRatio(0.25),
                dev.poligorn.aerocouriers.contract.CollateralProfile.EASY, "0.25");
        helper.assertValueEqual(dev.poligorn.aerocouriers.contract.CollateralProfile.fromLegacyRatio(0.5),
                dev.poligorn.aerocouriers.contract.CollateralProfile.NORMAL, "0.5");
        helper.assertValueEqual(dev.poligorn.aerocouriers.contract.CollateralProfile.fromLegacyRatio(0.75),
                dev.poligorn.aerocouriers.contract.CollateralProfile.HARD, "0.75");
        helper.assertValueEqual(dev.poligorn.aerocouriers.contract.CollateralProfile.fromLegacyRatio(0.05),
                dev.poligorn.aerocouriers.contract.CollateralProfile.PEACEFUL, "closer to peace than easy");
        helper.assertValueEqual(dev.poligorn.aerocouriers.contract.CollateralProfile.fromLegacyRatio(1.0),
                dev.poligorn.aerocouriers.contract.CollateralProfile.HARD, "closer to hard");
        helper.succeed();
    }

}
