package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.net.StationPackets;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import dev.poligorn.aerocouriers.village.VillageAdmin;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cork-board station: admin permission, rename, reputation, in-progress notes, no accept on the station. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageAdminGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos B = new BlockPos(6, 1, 6);

    private VillageAdminGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void fakePlayerCannotManageVillage(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "no_op"));
        helper.assertFalse(VillageAdmin.canManageVillage(player), "FakePlayer is not OP or singleplayer-creative");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void adminPacketDeniedForNonOperator(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station station = f.station(A);
                    helper.assertFalse(VillageAdmin.canManageVillage(f.courier), "test courier is not an operator");
                    int before = station.reputation();
                    StationPackets.handleAdmin(f.courier, VillageAdminPayload.amount(helper.absolutePos(A),
                            VillageAdminPayload.Action.REP_SET, 40));
                    helper.assertValueEqual(StationAtlas.get(f.server()).reputation(station.id()), before,
                            "reputation unchanged without permission");
                    f.cleanup();
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void renameChecksLengthAndUniqueness(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station first = f.station(A);
                    StationAtlas.Station second = f.station(B);
                    String original = first.name();
                    String taken = second.name();
                    VillageAdmin.rename(f.server(), first, taken);
                    helper.assertValueEqual(StationAtlas.get(f.server()).byId(first.id()).orElseThrow().name(),
                            original, "taken name is rejected");
                    helper.assertTrue(VillageAdmin.nameTaken(StationAtlas.get(f.server()), first.id(), taken),
                            "nameTaken sees the other village");
                    VillageAdmin.rename(f.server(), first, "A".repeat(VillageBoardPolicy.NAME_MAX + 1));
                    helper.assertValueEqual(StationAtlas.get(f.server()).byId(first.id()).orElseThrow().name(),
                            original, "overlong name is rejected");
                    VillageAdmin.rename(f.server(), first, "Лунная Поляна");
                    helper.assertValueEqual(StationAtlas.get(f.server()).byId(first.id()).orElseThrow().name(),
                            "Лунная Поляна", "unique rename");
                    f.cleanup();
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void reputationClampsAndAdds(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station station = f.station(A);
                    VillageAdmin.setReputation(f.server(), station, 99);
                    helper.assertValueEqual(StationAtlas.get(f.server()).reputation(station.id()),
                            VillageBoardPolicy.REPUTATION_MAX, "set clamps to 50");
                    VillageAdmin.addReputation(f.server(), StationAtlas.get(f.server()).byId(station.id()).orElseThrow(),
                            -100);
                    helper.assertValueEqual(StationAtlas.get(f.server()).reputation(station.id()),
                            VillageBoardPolicy.REPUTATION_MIN, "add clamps to 0");
                    VillageAdmin.addReputation(f.server(), StationAtlas.get(f.server()).byId(station.id()).orElseThrow(),
                            10);
                    helper.assertValueEqual(StationAtlas.get(f.server()).reputation(station.id()), 10, "add +10");
                    VillageAdmin.setReputation(f.server(), StationAtlas.get(f.server()).byId(station.id()).orElseThrow(),
                            27);
                    helper.assertValueEqual(StationAtlas.get(f.server()).reputation(station.id()), 27, "set exact");
                    f.cleanup();
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void inProgressNoteStaysUntilDelivered(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station from = f.station(A);
                    StationAtlas.Station to = f.station(B);
                    Contract contract = StationContractSource.publish(helper.getLevel(), from, to, RandomSource.create(1))
                            .orElse(null);
                    helper.assertTrue(contract != null, "published village order");
                    helper.assertTrue(StationPackets.onBoard(contract), "OPEN note is on the board");
                    helper.assertFalse(StationPackets.stationAcceptsOrders(), "station never accepts");
                    Economies.get(f.server()).deposit(f.courier.getUUID(), 200, "test");
                    f.expect(ContractManager.get(f.server()).accept(f.courier, contract.id()), true, "accept");
                    Contract accepted = ContractManager.get(f.server()).byId(contract.id()).orElseThrow();
                    helper.assertTrue(accepted.state().isActive(), "accepted/in transit");
                    helper.assertTrue(StationPackets.onBoard(accepted), "in-progress note stays on the board");
                    VillageAdmin.Stats mid = VillageAdmin.stats(f.server(),
                            StationAtlas.get(f.server()).byId(from.id()).orElseThrow());
                    helper.assertValueEqual(mid.active(), 1, "active haul counted");
                    helper.assertValueEqual(mid.onBoard(), 0, "OPEN count dropped after accept");
                    f.expect(ContractManager.get(f.server()).spawnContainer(f.server(), contract.id()),
                            true, "spawn crate");
                    helper.assertTrue(StationPackets.onBoard(ContractManager.get(f.server()).byId(contract.id())
                            .orElseThrow()), "IN_TRANSIT note stays");
                    f.expect(ContractManager.get(f.server()).forceDeliver(f.server(), contract.id()),
                            true, "deliver");
                    Contract done = ContractManager.get(f.server()).byId(contract.id()).orElseThrow();
                    helper.assertValueEqual(done.state(), ContractState.DELIVERED, "delivered");
                    helper.assertFalse(StationPackets.onBoard(done), "completed note leaves the board");
                    helper.assertTrue(StationAtlas.get(f.server()).byId(from.id()).orElseThrow().completed() >= 1,
                            "delivery increments village completed");
                    f.cleanup();
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void stationNeverAcceptsAndHasNoAcceptAction(GameTestHelper helper) {
        helper.assertFalse(StationPackets.stationAcceptsOrders(), "stationAcceptsOrders");
        for (VillageAdminPayload.Action action : VillageAdminPayload.Action.values()) {
            helper.assertFalse(action.name().toLowerCase().contains("accept"),
                    "village admin has no accept action: " + action);
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void policyPersistsOnAtlas(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    StationAtlas.Station station = f.station(A);
                    VillageAdmin.setExpensive(f.server(), station, false);
                    VillageAdmin.setRare(f.server(), StationAtlas.get(f.server()).byId(station.id()).orElseThrow(),
                            false);
                    VillageAdmin.setSizes(f.server(), StationAtlas.get(f.server()).byId(station.id()).orElseThrow(),
                            VillageBoardPolicy.maskOf(dev.poligorn.aerocouriers.cargo.CargoSize.S));
                    VillageBoardPolicy policy = StationAtlas.get(f.server()).policy(station.id());
                    helper.assertFalse(policy.allowExpensive(), "expensive off");
                    helper.assertFalse(policy.allowRare(), "rare off");
                    helper.assertTrue(policy.allowsSize(dev.poligorn.aerocouriers.cargo.CargoSize.S), "S allowed");
                    helper.assertFalse(policy.allowsSize(dev.poligorn.aerocouriers.cargo.CargoSize.XL), "XL blocked");
                    f.cleanup();
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void snapshotViewRoundTrips(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        StationSnapshotPayload.View view = new StationSnapshotPayload.View(
                true, true, false, true, 3, false, true, 0b0101, 15, 4, 1, 8, 2, 1, 12,
                List.of("Alex"), List.of("Речной Берег"));
        StationSnapshotPayload payload = new StationSnapshotPayload(
                BlockPos.ZERO, "Ясная Поляна", 27, 20, 2, 50, true, List.of(), 100L, 20L, view);
        StationSnapshotPayload.STREAM_CODEC.encode(buf, payload);
        buf.readerIndex(0);
        StationSnapshotPayload decoded = StationSnapshotPayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.name(), "Ясная Поляна", "name");
        helper.assertTrue(decoded.view().openAdmin(), "openAdmin");
        helper.assertFalse(decoded.view().allowExpensive(), "policy");
        helper.assertValueEqual(decoded.view().courierName(0), "Alex", "courier");
        helper.assertTrue(decoded.view().nameTaken("речной берег"), "other name");
        VillageAdminPayload admin = VillageAdminPayload.amount(new BlockPos(1, 2, 3),
                VillageAdminPayload.Action.REP_ADD, -10);
        RegistryFriendlyByteBuf adminBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        VillageAdminPayload.STREAM_CODEC.encode(adminBuf, admin);
        adminBuf.readerIndex(0);
        VillageAdminPayload back = VillageAdminPayload.STREAM_CODEC.decode(adminBuf);
        helper.assertValueEqual(back.amount(), -10, "rep delta");
        helper.assertValueEqual(back.action(), VillageAdminPayload.Action.REP_ADD, "action");
        helper.succeed();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer courier;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.courier = TestPlayers.courier(helper, "village_admin");
            courier.setGameMode(GameType.SURVIVAL);
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            helper.setBlock(A, ModBlocks.POST_STATION.get());
            helper.setBlock(B, ModBlocks.POST_STATION.get());
        }

        net.minecraft.server.MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        StationAtlas.Station station(BlockPos relative) {
            BlockPos abs = helper.absolutePos(relative);
            StationAtlas.Station found = StationAtlas.get(server()).byPos(helper.getLevel().dimension(), abs)
                    .orElse(null);
            helper.assertTrue(found != null, "station at " + abs);
            StationAtlas.get(server()).discover(found.id(), courier.getUUID());
            return found;
        }

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": " + outcome.message().getString());
        }

        void cleanup() {
            StationAtlas atlas = StationAtlas.get(server());
            atlas.removeNear(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO), 32);
            if (helper.getBlockEntity(A) instanceof PostStationBlockEntity) {
                helper.setBlock(A, Blocks.AIR);
            }
            if (helper.getBlockEntity(B) instanceof PostStationBlockEntity) {
                helper.setBlock(B, Blocks.AIR);
            }
        }
    }
}
