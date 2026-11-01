package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.net.StationPackets;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.tutorial.TutorialData;
import dev.poligorn.aerocouriers.tutorial.Tutorials;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Post stations stay closed while a survival player's tutorial is running. State is written straight
 * into {@link TutorialData} so {@link Tutorials#start} does not try to sync tasks to a FakePlayer.
 */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TutorialStationGameTests {
    private static final BlockPos LOWER = new BlockPos(2, 1, 2);

    private TutorialStationGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.tutorial.station")
    public static void stationBlockedWhileTutorialInProgress(GameTestHelper helper) {
        ServerPlayer courier = survivalCourier(helper);
        placePair(helper);
        BlockState state = helper.getBlockState(LOWER);
        BlockHitResult hit = hit(helper, LOWER);
        TutorialData data = TutorialData.get(helper.getLevel().getServer());
        UUID id = courier.getUUID();
        for (TutorialData.State stateNow : List.of(
                TutorialData.State.STARTED, TutorialData.State.ACCEPTED, TutorialData.State.IN_TRANSIT)) {
            data.put(id, TutorialData.Entry.EMPTY.withState(stateNow));
            helper.assertValueEqual(state.useWithoutItem(helper.getLevel(), courier, hit),
                    InteractionResult.FAIL, stateNow + " must refuse the station");
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.tutorial.station")
    public static void stationOpensWhenTutorialNotRunning(GameTestHelper helper) {
        ServerPlayer courier = survivalCourier(helper);
        placePair(helper);
        BlockState state = helper.getBlockState(LOWER);
        BlockHitResult hit = hit(helper, LOWER);
        TutorialData data = TutorialData.get(helper.getLevel().getServer());
        UUID id = courier.getUUID();
        for (TutorialData.State stateNow : List.of(
                TutorialData.State.NOT_STARTED, TutorialData.State.OFFERED, TutorialData.State.DONE,
                TutorialData.State.SKIPPED, TutorialData.State.DISABLED_REMOVED)) {
            data.put(id, TutorialData.Entry.EMPTY.withState(stateNow));
            helper.assertValueEqual(state.useWithoutItem(helper.getLevel(), courier, hit),
                    InteractionResult.CONSUME, stateNow + " must open the station");
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.tutorial.station")
    public static void stationBlockedBothHalvesAndShift(GameTestHelper helper) {
        ServerPlayer courier = survivalCourier(helper);
        placePair(helper);
        TutorialData.get(helper.getLevel().getServer())
                .put(courier.getUUID(), TutorialData.Entry.EMPTY.withState(TutorialData.State.STARTED));
        BlockState lower = helper.getBlockState(LOWER);
        BlockState upper = helper.getBlockState(LOWER.above());
        helper.assertValueEqual(lower.useWithoutItem(helper.getLevel(), courier, hit(helper, LOWER)),
                InteractionResult.FAIL, "lower half");
        helper.assertValueEqual(upper.useWithoutItem(helper.getLevel(), courier, hit(helper, LOWER.above())),
                InteractionResult.FAIL, "upper half");
        courier.setShiftKeyDown(true);
        helper.assertValueEqual(lower.useWithoutItem(helper.getLevel(), courier, hit(helper, LOWER)),
                InteractionResult.FAIL, "shift+use admin panel");
        courier.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertValueEqual(lower.useWithoutItem(helper.getLevel(), courier, hit(helper, LOWER)),
                InteractionResult.FAIL, "item in hand still uses the same server guard");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.tutorial.station")
    public static void stationUnblockedWhenTutorialSwitchedOff(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean was = CouriersServerConfig.TUTORIAL_ENABLED.get();
        ServerPlayer courier = survivalCourier(helper);
        placePair(helper);
        TutorialData.get(server).put(courier.getUUID(),
                TutorialData.Entry.EMPTY.withState(TutorialData.State.STARTED));
        try {
            Tutorials.setEnabled(server, false);
            helper.assertValueEqual(helper.getBlockState(LOWER)
                            .useWithoutItem(helper.getLevel(), courier, hit(helper, LOWER)),
                    InteractionResult.CONSUME, "disabled tutorial opens the station");
        } finally {
            Tutorials.setEnabled(server, was);
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.tutorial.station")
    public static void stationPacketPathBlocked(GameTestHelper helper) {
        ServerPlayer courier = survivalCourier(helper);
        placePair(helper);
        TutorialData.get(helper.getLevel().getServer())
                .put(courier.getUUID(), TutorialData.Entry.EMPTY.withState(TutorialData.State.ACCEPTED));
        ServerLevel level = helper.getLevel();
        helper.assertFalse(StationPackets.open(courier, level, helper.absolutePos(LOWER)),
                "C2S open must refuse during the tutorial");
        helper.assertFalse(StationPackets.open(courier, level, helper.absolutePos(LOWER), true),
                "admin-panel open must refuse during the tutorial");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.tutorial.station")
    public static void stationOpensInCreativeDuringTutorial(GameTestHelper helper) {
        ServerPlayer courier = TestPlayers.courier(helper, "creative");
        courier.setGameMode(GameType.CREATIVE);
        placePair(helper);
        TutorialData.get(helper.getLevel().getServer())
                .put(courier.getUUID(), TutorialData.Entry.EMPTY.withState(TutorialData.State.IN_TRANSIT));
        helper.assertFalse(Tutorials.blocksStation(courier), "creative bypasses the station lock");
        helper.assertValueEqual(helper.getBlockState(LOWER)
                        .useWithoutItem(helper.getLevel(), courier, hit(helper, LOWER)),
                InteractionResult.CONSUME, "creative still opens the station");
        helper.succeed();
    }

    private static ServerPlayer survivalCourier(GameTestHelper helper) {
        ServerPlayer courier = TestPlayers.courier(helper, "trainee");
        courier.setGameMode(GameType.SURVIVAL);
        return courier;
    }

    private static void placePair(GameTestHelper helper) {
        helper.setBlock(LOWER.below(), Blocks.STONE);
        BlockState lower = ModBlocks.POST_STATION.get().defaultBlockState()
                .setValue(PostStationBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(PostStationBlock.FACING, Direction.NORTH);
        helper.setBlock(LOWER, lower);
        helper.setBlock(LOWER.above(), lower.setValue(PostStationBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
    }
}
