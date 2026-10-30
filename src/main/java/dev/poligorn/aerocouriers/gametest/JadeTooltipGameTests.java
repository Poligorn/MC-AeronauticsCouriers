package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.compat.JadeTooltipData;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.CargoSnapshot;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.CustomerKind;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.DisplayStatus;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.StationSnapshot;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side Jade tooltip assembly. Does not import Jade types so the suite runs without the optional mod.
 */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JadeTooltipGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);
    private static final BlockPos STATION_A = new BlockPos(1, 1, 1);
    private static final BlockPos STATION_B = new BlockPos(6, 1, 6);
    private static final BlockPos LARGE = new BlockPos(2, 1, 2);
    private static final long REWARD = 20;
    private static final long COLLATERAL = 24;

    private JadeTooltipGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void emptyContainerShowsNoContract(GameTestHelper helper) {
        prepareFloor(helper);
        BlockPos pos = helper.absolutePos(CHEST);
        CargoSpawner.Result result = CargoSpawner.spawn(helper.getLevel(), pos, CargoSize.S, 0, null, true,
                Direction.NORTH, true);
        helper.assertTrue(result instanceof CargoSpawner.Result.Placed, "empty crate placed");
        CargoSnapshot snapshot = JadeTooltipData.cargoAt(helper.getLevel(), pos).orElseThrow();
        helper.assertFalse(snapshot.bound(), "unbound crate is not a contract");
        CompoundTag tag = new CompoundTag();
        snapshot.write(tag);
        helper.assertFalse(CargoSnapshot.read(tag).bound(), "empty payload round-trip");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void playerOrderShowsCustomerIdWeightAndPublished(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract contract = f.stamp();
        helper.assertValueEqual(contract.state(), ContractState.OPEN, "stamp stays OPEN");
        CargoSnapshot snapshot = JadeTooltipData.cargoAt(helper.getLevel(), helper.absolutePos(CHEST)).orElseThrow();
        helper.assertTrue(snapshot.bound(), "bound crate");
        helper.assertValueEqual(snapshot.shortId(), contract.shortId(), "short id");
        helper.assertValueEqual(snapshot.customerKind(), CustomerKind.PLAYER, "player customer");
        helper.assertValueEqual(snapshot.customer(), f.issuer.getGameProfile().getName(), "customer nick");
        helper.assertTrue(Math.abs(snapshot.weight() - contract.weight()) < 0.001, "weight from contract");
        helper.assertValueEqual(snapshot.status(), DisplayStatus.PUBLISHED, "OPEN → published");
        CompoundTag tag = new CompoundTag();
        snapshot.write(tag);
        CargoSnapshot decoded = CargoSnapshot.read(tag);
        helper.assertValueEqual(decoded.shortId(), snapshot.shortId(), "nbt short id");
        helper.assertValueEqual(decoded.status(), DisplayStatus.PUBLISHED, "nbt status");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void acceptedCrateIsInTransitAndPastDueIsOverdue(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract contract = f.stamp();
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        Contract live = f.manager.byId(contract.id()).orElseThrow();
        helper.assertValueEqual(live.state(), ContractState.IN_TRANSIT, "stamp accept is IN_TRANSIT");
        // Accept unlocks the stamp crate and Sable moves it off the chest cell; status is still on Contract.
        CargoSnapshot onTime = JadeTooltipData.fromContract(live, f.server(), ContractManager.now(f.server()));
        helper.assertValueEqual(onTime.status(), DisplayStatus.IN_TRANSIT, "on-time IN_TRANSIT");

        DisplayStatus overdue = JadeTooltipData.displayStatus(live, live.timing().dueAt() + 1);
        helper.assertValueEqual(overdue, DisplayStatus.OVERDUE, "past dueAt is overdue");
        helper.assertValueEqual(JadeTooltipData.displayStatus(live, live.timing().dueAt() - 1),
                DisplayStatus.IN_TRANSIT, "before dueAt stays in transit");

        f.expect(f.manager.cancel(f.issuer, contract.id()), false, "cannot cancel after accept");
        f.expect(f.manager.abandon(f.courier, contract.id()), true, "abandon");
        Contract failed = f.manager.byId(contract.id()).orElseThrow();
        helper.assertValueEqual(failed.state(), ContractState.FAILED, "abandoned");
        helper.assertValueEqual(JadeTooltipData.displayStatus(failed, ContractManager.now(f.server())),
                DisplayStatus.OVERDUE, "FAILED → overdue");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void deliveredAndExpiredMapToJadeStatuses(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract delivered = f.create();
        f.expect(f.manager.accept(f.courier, delivered.id()), true, "accept");
        f.spawn(delivered);
        f.expect(f.manager.forceDeliver(f.server(), delivered.id()), true, "force deliver");
        helper.assertValueEqual(f.manager.byId(delivered.id()).orElseThrow().state(), ContractState.DELIVERED,
                "delivered");
        helper.assertValueEqual(
                JadeTooltipData.displayStatus(f.manager.byId(delivered.id()).orElseThrow(),
                        ContractManager.now(f.server())),
                DisplayStatus.DELIVERED, "DELIVERED stays delivered");

        f.fillChest();
        Contract expired = f.create();
        f.expect(f.manager.cancel(f.issuer, expired.id()), true, "cancel open");
        helper.assertValueEqual(
                JadeTooltipData.displayStatus(f.manager.byId(expired.id()).orElseThrow(),
                        ContractManager.now(f.server())),
                DisplayStatus.OVERDUE, "EXPIRED → overdue");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void largeSegmentReadsControllerContract(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract contract = f.create();
        prepareLargePad(helper);
        BlockPos controller = helper.absolutePos(LARGE);
        CargoSpawner.Result result = CargoSpawner.spawn(helper.getLevel(), controller, CargoSize.L, 4, contract.id(),
                true, Direction.NORTH, true);
        helper.assertTrue(result instanceof CargoSpawner.Result.Placed, "L placed locked");
        BlockPos segment = controller.offset(1, 0, 0);
        helper.assertTrue(helper.getLevel().getBlockState(segment).is(ModBlocks.CARGO_CONTAINER_L.get()),
                "non-controller L segment");
        helper.assertTrue(helper.getLevel().getBlockEntity(segment) == null, "segment has no BE");
        CargoSnapshot fromController = JadeTooltipData.cargoAt(helper.getLevel(), controller).orElseThrow();
        CargoSnapshot fromSegment = JadeTooltipData.cargoAt(helper.getLevel(), segment).orElseThrow();
        helper.assertTrue(fromController.bound() && fromSegment.bound(), "both segments bound");
        helper.assertValueEqual(fromSegment.shortId(), fromController.shortId(), "same short id");
        helper.assertValueEqual(fromSegment.customer(), fromController.customer(), "same customer");
        helper.assertValueEqual(fromSegment.status(), DisplayStatus.PUBLISHED, "OPEN on every segment");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.jade.npc", timeoutTicks = 200)
    public static void villageOrderShowsStationAsCustomer(GameTestHelper helper) {
        isolateAtlas(helper);
        prepareFloor(helper);
        helper.setBlock(STATION_A, ModBlocks.POST_STATION.get());
        helper.setBlock(STATION_B, ModBlocks.POST_STATION.get());
        ServerPlayer courier = TestPlayers.courier(helper, "jade_npc");
        Economies.get(helper.getLevel().getServer()).deposit(courier.getUUID(), 200, "test");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas atlas = StationAtlas.get(helper.getLevel().getServer());
                    StationAtlas.Station from = atlas.byPos(helper.getLevel().dimension(),
                            helper.absolutePos(STATION_A)).orElse(null);
                    StationAtlas.Station to = atlas.byPos(helper.getLevel().dimension(),
                            helper.absolutePos(STATION_B)).orElse(null);
                    helper.assertTrue(from != null && to != null, "both stations registered");
                    atlas.discover(from.id(), courier.getUUID());
                    atlas.discover(to.id(), courier.getUUID());
                    Contract contract = StationContractSource.publish(helper.getLevel(), from, to, RandomSource.create(1))
                            .orElse(null);
                    helper.assertTrue(contract != null, "NPC publish");
                    helper.assertValueEqual(contract.type(), ContractType.VILLAGE, "village type");
                    ContractManager manager = ContractManager.get(helper.getLevel().getServer());
                    ContractManager.Outcome accepted = manager.accept(courier, contract.id());
                    helper.assertTrue(accepted.success(), "accept NPC: " + accepted.message().getString());
                    // spawnContainer hands the crate to Sable (plot ~2e7); a locked pad keeps cargoAt deterministic.
                    BlockPos crate = helper.absolutePos(new BlockPos(3, 1, 3));
                    CargoSpawner.Result placed = CargoSpawner.spawn(helper.getLevel(), crate, CargoSize.S, 2,
                            contract.id(), true, Direction.NORTH, true);
                    helper.assertTrue(placed instanceof CargoSpawner.Result.Placed, "locked village crate");
                    CargoSnapshot snapshot = JadeTooltipData.cargoAt(helper.getLevel(), crate).orElseThrow();
                    helper.assertTrue(snapshot.bound(), "village crate bound");
                    helper.assertValueEqual(snapshot.customerKind(), CustomerKind.VILLAGE, "village customer");
                    helper.assertValueEqual(snapshot.customer(), from.name(), "station name as customer");
                    helper.assertValueEqual(snapshot.shortId(), contract.shortId(), "village short id");
                    helper.assertValueEqual(snapshot.status(), DisplayStatus.IN_TRANSIT, "accepted NPC is in transit");
                    atlas.remove(from.id());
                    atlas.remove(to.id());
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.jade.station", timeoutTicks = 200)
    public static void stationTooltipNameReputationOrdersAndRefresh(GameTestHelper helper) {
        isolateAtlas(helper);
        prepareFloor(helper);
        helper.setBlock(STATION_A, ModBlocks.POST_STATION.get());
        if (helper.getBlockEntity(STATION_A) instanceof PostStationBlockEntity station) {
            station.markVillage();
        }
        helper.setBlock(STATION_B, ModBlocks.POST_STATION.get());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    PostStationBlockEntity be = helper.getBlockEntity(STATION_A) instanceof PostStationBlockEntity s
                            ? s : null;
                    helper.assertTrue(be != null && be.stationId() != null, "village station loaded");
                    be.rename("Yasnaya Polyana");
                    StationAtlas atlas = StationAtlas.get(level.getServer());
                    atlas.setReputation(be.stationId(), 27);
                    StationAtlas.Station from = atlas.byId(be.stationId()).orElseThrow();
                    StationAtlas.Station to = atlas.byPos(level.dimension(), helper.absolutePos(STATION_B))
                            .orElseThrow();
                    helper.assertTrue(StationContractSource.publish(level, from, to, RandomSource.create(2)).isPresent(),
                            "one open NPC order");
                    StationSnapshot snapshot = JadeTooltipData.stationAt(level, helper.absolutePos(STATION_A))
                            .orElseThrow();
                    helper.assertValueEqual(snapshot.name(), "Yasnaya Polyana", "station name");
                    helper.assertTrue(snapshot.village(), "village flag");
                    helper.assertValueEqual(snapshot.villageName(), "Yasnaya Polyana", "village name");
                    helper.assertValueEqual(snapshot.reputation(), 27, "viewer-facing station reputation");
                    helper.assertValueEqual(snapshot.bonusPercent(), 20, "+20% at reputation 27");
                    helper.assertTrue(snapshot.openOrders() >= 1, "open NPC count");
                    helper.assertTrue(snapshot.refreshTicks() > 0, "refresh countdown");
                    CompoundTag tag = new CompoundTag();
                    snapshot.write(tag);
                    StationSnapshot decoded = StationSnapshot.read(tag).orElseThrow();
                    helper.assertValueEqual(decoded.reputation(), 27, "nbt reputation");
                    helper.assertValueEqual(decoded.openOrders(), snapshot.openOrders(), "nbt open orders");
                    atlas.remove(from.id());
                    atlas.remove(to.id());
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void jadeIsOptionalOnTheGameTestClasspath(GameTestHelper helper) {
        helper.assertFalse(ModList.get().isLoaded("jade"),
                "GameTest must run without Jade so the soft-dep is real");
        helper.succeed();
    }

    private static void isolateAtlas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StationAtlas.get(level.getServer()).removeNear(level.dimension(), helper.absolutePos(BlockPos.ZERO), 384);
    }

    private static void prepareFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void prepareLargePad(GameTestHelper helper) {
        prepareFloor(helper);
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            }
        }
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer issuer;
        final ServerPlayer courier;
        final ContractManager manager;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.issuer = TestPlayers.player(helper, "issuer");
            this.courier = TestPlayers.courier(helper, "courier");
            this.manager = ContractManager.get(server());
            issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
            TestPlayers.giveWaybills(issuer, 4);
            Economies.get(server()).deposit(issuer.getUUID(), 100, "test");
            Economies.get(server()).deposit(courier.getUUID(), 50, "test");
            prepareFloor(helper);
            helper.setBlock(CHEST, Blocks.CHEST);
            fillChest();
        }

        void fillChest() {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
            chest.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        }

        MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        Contract stamp() {
            Destination destination = new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 2);
            ContractManager.Outcome outcome = manager.createFromWaybill(issuer, helper.absolutePos(CHEST),
                    destination, REWARD, COLLATERAL, 60);
            expect(outcome, true, "stamp");
            return outcome.contract().orElseThrow();
        }

        Contract create() {
            Destination destination = new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 2);
            ContractManager.Outcome outcome = manager.create(issuer, (ChestBlockEntity) helper.getBlockEntity(CHEST),
                    helper.absolutePos(CHEST), destination, REWARD, COLLATERAL, 60);
            expect(outcome, true, "create");
            return outcome.contract().orElseThrow();
        }

        void spawn(Contract contract) {
            expect(manager.spawnContainer(server(), contract.id()), true, "container spawn");
        }

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": expected " + (success ? "success" : "refusal") + ", got " + outcome.message().getString());
        }
    }
}
