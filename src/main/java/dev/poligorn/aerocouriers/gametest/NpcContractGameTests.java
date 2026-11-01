package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Iteration 5: NPC contracts between post stations (admin-placed included). */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcContractGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos B = new BlockPos(6, 1, 6);
    private static final int SABLE_SETTLE_TICKS = 40;

    private NpcContractGameTests() {
    }

    /** Own batch: {@code generate} pairs any stations in the atlas, including the far one of a sibling test. */
    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.npc.generate", timeoutTicks = 200)
    public static void npcGenerateAcceptDeliver(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station from = f.station(A);
                    StationAtlas.Station to = f.station(B);
                    int created = StationContractSource.INSTANCE.generate(f.server());
                    helper.assertTrue(created >= 1, "generated at least one NPC order, got " + created);
                    Contract contract = f.manager.open()
                            .filter(c -> c.type() == ContractType.VILLAGE)
                            .filter(c -> (c.issuer().equals(from.id()) && c.recipient().equals(to.id()))
                                    || (c.issuer().equals(to.id()) && c.recipient().equals(from.id())))
                            .findFirst()
                            .orElse(null);
                    helper.assertTrue(contract != null, "generated contract not on the board");
                    helper.assertValueEqual(contract.type(), ContractType.VILLAGE, "type");
                    helper.assertFalse(contract.preplaced(), "NPC cargo is escrow-only until accept");
                    helper.assertTrue(Escrow.get(f.server()).holding(contract.id()).items().size() >= 1,
                            "generated cargo is in escrow");
                    helper.assertValueEqual(f.economy.balance(from.id()), 0L, "NPC reward is minted, not taken from station");
                    helper.assertValueEqual(f.economy.balance(to.id()), 0L, "destination station is not charged");

                    f.economy.deposit(f.courier.getUUID(), 200, "test");
                    long before = f.economy.balance(f.courier.getUUID());
                    ContractManager.Outcome accepted = f.manager.accept(f.courier, contract.id());
                    f.expect(accepted, true, "accept NPC");
                    helper.assertValueEqual(f.state(contract), ContractState.ACCEPTED, "accepted before spawn");
                    f.spawnAtPickup(contract);
                    helper.assertValueEqual(f.state(contract), ContractState.IN_TRANSIT, "spawned after accept");
                    helper.assertValueEqual(f.economy.balance(f.courier.getUUID()), before, "trainee: no collateral");

                    BlockPos dest = contract.recipient().equals(to.id()) ? B : A;
                    Vector3d atDest = f.world(dest);
                    f.courier.setPos(Vec3.atCenterOf(helper.absolutePos(dest)));
                    int checks = CouriersServerConfig.DELIVERY_STILL_CHECKS.get();
                    for (int i = 0; i < checks - 1; i++) {
                        f.check(contract, atDest, 0, ContractManager.Verdict.KEEP, "still " + i);
                    }
                    f.check(contract, atDest, 0, ContractManager.Verdict.REMOVE, "deliver NPC");
                    helper.assertValueEqual(f.state(contract), ContractState.DELIVERED, "delivered");
                    helper.assertTrue(f.economy.balance(f.courier.getUUID()) > before, "NPC reward paid");
                    helper.assertTrue(Mailbox.get(f.server()).contents(f.courier.getUUID()).isEmpty(),
                            "NPC cargo is consumed, not mailed");
                    helper.assertTrue(Mailbox.get(f.server()).contents(contract.recipient()).isEmpty(),
                            "destination station has no mailbox");
                    helper.assertValueEqual(Licenses.profile(f.courier).villageDelivered(), 1, "village delivered");
                    helper.assertTrue(Escrow.get(f.server()).holding(contract.id()).isEmpty(), "escrow empty");
                    f.cleanup();
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.npc", timeoutTicks = 200)
    public static void npcFailBurnsCollateralAndDestroysCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station from = f.station(A);
                    StationAtlas.Station to = f.station(B);
                    Contract contract = f.publish(from, to);
                    f.economy.deposit(f.courier.getUUID(), 200, "test");
                    Licenses.setProfile(f.courier, Licenses.profile(f.courier)
                            .withVillageDelivery(0, 0, false, 0L)
                            .withVillageDelivery(0, 0, false, 0L)
                            .withVillageDelivery(0, 0, false, 0L));
                    helper.assertValueEqual(Licenses.profile(f.courier).villageDelivered(), 3, "past trainee");
                    long before = f.economy.balance(f.courier.getUUID());
                    f.expect(f.manager.accept(f.courier, contract.id()), true, "accept after trainee");
                    helper.assertTrue(f.economy.balance(f.courier.getUUID()) < before, "collateral taken");
                    long afterAccept = f.economy.balance(f.courier.getUUID());
                    long stationBefore = f.economy.balance(from.id());
                    f.expect(f.manager.abandon(f.courier, contract.id()), true, "abandon NPC");
                    helper.assertValueEqual(f.state(contract), ContractState.FAILED, "failed");
                    helper.assertValueEqual(f.economy.balance(f.courier.getUUID()), afterAccept,
                            "courier does not get collateral back");
                    helper.assertValueEqual(f.economy.balance(from.id()), stationBefore,
                            "NPC collateral burns, not paid to the station");
                    helper.assertTrue(Mailbox.get(f.server()).contents(f.courier.getUUID()).isEmpty(),
                            "NPC cargo destroyed on fail");
                    helper.assertTrue(Escrow.get(f.server()).holding(contract.id()).isEmpty(), "escrow empty");
                    f.cleanup();
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.npc", timeoutTicks = 200)
    public static void npcTraineeAdvanceAfterThirdDelivery(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station from = f.station(A);
                    StationAtlas.Station to = f.station(B);
                    f.economy.deposit(f.courier.getUUID(), 50, "test");
                    Licenses.setProfile(f.courier, Licenses.profile(f.courier)
                            .withVillageDelivery(0, 0, false, 0L)
                            .withVillageDelivery(0, 0, false, 0L));
                    helper.assertValueEqual(Licenses.profile(f.courier).villageDelivered(), 2, "two village jobs");
                    helper.assertFalse(Licenses.profile(f.courier).traineeAdvancePaid(), "advance not yet");

                    Contract contract = f.publish(from, to);
                    f.expect(f.manager.accept(f.courier, contract.id()), true, "third trainee accept");
                    f.spawnAtPickup(contract);
                    Vector3d atDest = f.world(B);
                    f.courier.setPos(Vec3.atCenterOf(helper.absolutePos(B)));
                    int checks = CouriersServerConfig.DELIVERY_STILL_CHECKS.get();
                    for (int i = 0; i < checks - 1; i++) {
                        f.check(contract, atDest, 0, ContractManager.Verdict.KEEP, "still " + i);
                    }
                    long beforeDeliver = f.economy.balance(f.courier.getUUID());
                    f.check(contract, atDest, 0, ContractManager.Verdict.REMOVE, "third delivery");
                    helper.assertValueEqual(Licenses.profile(f.courier).villageDelivered(), 3, "third village");
                    helper.assertTrue(Licenses.profile(f.courier).traineeAdvancePaid(), "advance flagged");
                    helper.assertTrue(f.economy.balance(f.courier.getUUID())
                                    >= beforeDeliver + MoneyScale.fromSpurs((long) CouriersServerConfig.TRAINEE_BONUS.get()),
                            "trainee bonus paid");
                    f.cleanup();
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.npc", timeoutTicks = 200)
    public static void npcTraineeRejectsLongRoute(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        BlockPos far = new BlockPos(1600, 1, 1);
        helper.setBlock(far, ModBlocks.POST_STATION.get());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station near = f.station(A);
                    StationAtlas.Station distant = f.station(far);
                    Contract contract = f.publish(near, distant);
                    helper.assertTrue(contract.distance() > CouriersServerConfig.TRAINEE_MAX_DISTANCE.get(),
                            "contract distance should exceed trainee cap");
                    f.expect(f.manager.accept(f.courier, contract.id()), false, "trainee long route");
                    f.cleanup();
                    helper.setBlock(far, Blocks.AIR);
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.npc.generate.short", timeoutTicks = 200)
    public static void npcGeneratorPrefersShortTraineePairs(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        BlockPos far = new BlockPos(2000, 1, 1);
        helper.setBlock(far, ModBlocks.POST_STATION.get());
        int previousMax = CouriersServerConfig.VILLAGE_BOARD_MAX.get();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station from = f.station(A);
                    StationAtlas.Station near = f.station(B);
                    StationAtlas.Station distant = f.station(far);
                    try {
                        StationAtlas atlas = StationAtlas.get(f.server());
                        for (StationAtlas.Station leftover : List.copyOf(atlas.all())) {
                            if (!leftover.id().equals(from.id())
                                    && !leftover.id().equals(near.id())
                                    && !leftover.id().equals(distant.id())) {
                                atlas.remove(leftover.id());
                            }
                        }
                        for (Contract leftover : List.copyOf(f.manager.all().toList())) {
                            if (leftover.type() == ContractType.VILLAGE
                                    && leftover.state() == ContractState.OPEN) {
                                f.manager.adminCancelWithRefund(f.server(), leftover.id());
                            }
                        }
                        CouriersServerConfig.VILLAGE_BOARD_MAX.set(2);
                        int created = StationContractSource.INSTANCE.generate(f.server());
                        helper.assertTrue(created >= 1, "generated at least one NPC order, got " + created);
                        long shortPairs = f.manager.open()
                                .filter(c -> c.type() == ContractType.VILLAGE)
                                .filter(c -> c.distance() <= CouriersServerConfig.TRAINEE_MAX_DISTANCE.get())
                                .count();
                        long longPairs = f.manager.open()
                                .filter(c -> c.type() == ContractType.VILLAGE)
                                .filter(c -> c.distance() > CouriersServerConfig.TRAINEE_MAX_DISTANCE.get())
                                .count();
                        helper.assertTrue(shortPairs >= 1, "generator should publish a short trainee pair");
                        helper.assertValueEqual(longPairs, 0L, "board cap should fill with short pairs first");
                        helper.assertTrue(
                                f.manager.open().anyMatch(c ->
                                        (c.issuer().equals(from.id()) && c.recipient().equals(near.id()))
                                                || (c.issuer().equals(near.id()) && c.recipient().equals(from.id()))),
                                "short pair uses the nearby stations, not " + distant.name());
                    } finally {
                        CouriersServerConfig.VILLAGE_BOARD_MAX.set(previousMax);
                        StationAtlas.get(f.server()).remove(near.id());
                        StationAtlas.get(f.server()).remove(distant.id());
                        f.cleanup();
                        helper.setBlock(far, Blocks.AIR);
                    }
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer courier;
        final ContractManager manager;
        final Economy economy;
        final AtomicReference<UUID> stationA = new AtomicReference<>();
        final AtomicReference<UUID> stationB = new AtomicReference<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.courier = TestPlayers.courier(helper, "npc_courier");
            this.manager = ContractManager.get(server());
            this.economy = Economies.get(server());
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            helper.setBlock(A, ModBlocks.POST_STATION.get());
            helper.setBlock(B, ModBlocks.POST_STATION.get());
        }

        MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        StationAtlas.Station station(BlockPos relative) {
            BlockPos abs = helper.absolutePos(relative);
            StationAtlas atlas = StationAtlas.get(server());
            StationAtlas.Station found = atlas.byPos(helper.getLevel().dimension(), abs).orElse(null);
            helper.assertTrue(found != null, "station at " + abs + " is not in the atlas");
            if (relative.equals(A)) {
                stationA.set(found.id());
            } else {
                stationB.set(found.id());
            }
            atlas.discover(found.id(), courier.getUUID());
            return found;
        }

        Contract publish(StationAtlas.Station from, StationAtlas.Station to) {
            Contract contract = StationContractSource.publish(helper.getLevel(), from, to, RandomSource.create(1))
                    .orElse(null);
            helper.assertTrue(contract != null, "NPC publish failed (no crate spot?)");
            return contract;
        }

        void check(Contract contract, Vector3d position, double speed, ContractManager.Verdict expected, String what) {
            helper.assertValueEqual(manager.checkContainer(helper.getLevel(), contract.id(), position, speed),
                    expected, what);
        }

        Vector3d world(BlockPos relative) {
            Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
            return new Vector3d(center.x, center.y, center.z);
        }

        ContractState state(Contract contract) {
            return manager.byId(contract.id()).orElseThrow().state();
        }

        void spawnAtPickup(Contract contract) {
            expect(manager.spawnContainer(server(), contract.id()), true, "NPC container spawn");
        }

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": expected " + (success ? "success" : "refusal") + ", got " + outcome.message().getString());
        }

        void cleanup() {
            StationAtlas atlas = StationAtlas.get(server());
            if (stationA.get() != null) {
                atlas.remove(stationA.get());
            }
            if (stationB.get() != null) {
                atlas.remove(stationB.get());
            }
            if (helper.getBlockEntity(A) instanceof PostStationBlockEntity) {
                helper.setBlock(A, Blocks.AIR);
            }
            if (helper.getBlockEntity(B) instanceof PostStationBlockEntity) {
                helper.setBlock(B, Blocks.AIR);
            }
        }
    }
}
