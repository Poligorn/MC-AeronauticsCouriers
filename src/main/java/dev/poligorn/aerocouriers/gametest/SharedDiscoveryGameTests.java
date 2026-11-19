package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code stations.shared_discovery}, id-based route matching on the board, and NPC orders for stations whose
 * chunk is not loaded. Own batch: the flag is a global server config value.
 */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedDiscoveryGameTests {
    private static final String BATCH = "aerocouriers.shared_discovery";

    private SharedDiscoveryGameTests() {
    }

    /** Far from any test structure, so these chunks are never loaded by the test itself. */
    private static BlockPos remote(int index) {
        return new BlockPos(1_500_000 + index * 4_000, 70, 1_500_000);
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void sharedDiscoveryOffHidesUndiscoveredRoutes(GameTestHelper helper) {
        withFlag(helper, false, f -> {
            Contract contract = f.publish(f.from, f.to);
            helper.assertFalse(BoardPackets.seesAllStations(f.viewer), "flag off: viewer is not an operator");
            helper.assertFalse(visible(f, contract), "flag off: undiscovered route is hidden");
            helper.assertFalse(stationNames(f).contains(f.from.name()), "flag off: undiscovered station not listed");

            StationAtlas atlas = StationAtlas.get(f.server());
            atlas.discover(f.from.id(), f.viewer.getUUID());
            helper.assertFalse(visible(f, contract), "flag off: one end discovered is not enough");
            atlas.discover(f.to.id(), f.viewer.getUUID());
            helper.assertTrue(visible(f, contract), "flag off: both ends discovered shows the order");
            helper.assertTrue(stationNames(f).contains(f.from.name()), "flag off: discovered station listed");
        });
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void sharedDiscoveryOnShowsEveryRoute(GameTestHelper helper) {
        withFlag(helper, true, f -> {
            Contract contract = f.publish(f.from, f.to);
            helper.assertTrue(BoardPackets.seesAllStations(f.viewer), "flag on: everyone sees all stations");
            helper.assertTrue(visible(f, contract), "flag on: undiscovered route is visible");
            List<String> names = stationNames(f);
            helper.assertTrue(names.contains(f.from.name()) && names.contains(f.to.name()),
                    "flag on: every station is in the snapshot station list");
            StationAtlas atlas = StationAtlas.get(f.server());
            helper.assertFalse(atlas.byId(f.from.id()).orElseThrow().discoveredBy(f.viewer.getUUID()),
                    "flag on: discovery itself is not written");
        });
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void boardMatchesRouteStationsById(GameTestHelper helper) {
        withFlag(helper, false, f -> {
            Contract contract = f.publish(f.from, f.to);
            StationAtlas atlas = StationAtlas.get(f.server());
            atlas.discover(f.from.id(), f.viewer.getUUID());
            atlas.discover(f.to.id(), f.viewer.getUUID());

            // The origin station moves (same id, new position): coordinates no longer match, the id still does.
            atlas.register(f.from.id(), f.from.name(), f.from.dimension(), remote(9));
            helper.assertTrue(visible(f, contract), "moved origin station still matches by id");

            // A stranger station sits on the destination's old spot; the viewer discovered only that one.
            UUID impostorId = UUID.randomUUID();
            f.created.add(impostorId);
            ServerPlayer other = TestPlayers.player(helper, "shared_other");
            atlas.discover(f.from.id(), other.getUUID());
            atlas.register(f.to.id(), f.to.name(), f.to.dimension(), remote(10));
            atlas.register(impostorId, f.to.name() + " (old spot)", f.to.dimension(), f.to.pos());
            atlas.discover(impostorId, other.getUUID());
            helper.assertFalse(BoardPackets.visibleOnBoard(contract, other.getUUID(), atlas, false),
                    "a different station on the destination's coordinates does not count");

            // Destination re-placed with a new id: fall back to coordinates.
            atlas.remove(f.to.id());
            helper.assertTrue(BoardPackets.visibleOnBoard(contract, other.getUUID(), atlas, false),
                    "station gone by id falls back to the station at the same coordinates");
        });
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void npcOrdersPublishForUnloadedStations(GameTestHelper helper) {
        withFlag(helper, false, f -> {
            ServerLevel level = helper.getLevel();
            helper.assertFalse(level.isLoaded(f.from.pos()), "origin chunk must not be loaded for this test");
            int previousMax = CouriersServerConfig.VILLAGE_BOARD_MAX.get();
            try {
                CouriersServerConfig.VILLAGE_BOARD_MAX.set(10_000);
                int created = 0;
                for (int attempt = 0; attempt < 8 && created == 0; attempt++) {
                    created = StationContractSource.generateFor(f.server(), f.from);
                }
                helper.assertTrue(created >= 1, "station in an unloaded chunk still gets NPC orders");
                helper.assertTrue(f.manager.open().anyMatch(c -> c.type() == ContractType.VILLAGE
                                && c.issuer().equals(f.from.id())),
                        "an order starts at the unloaded station");
                helper.assertFalse(level.isLoaded(f.from.pos()), "publishing did not load the origin chunk");
                helper.assertFalse(level.hasChunk(f.from.pos().getX() >> 4, f.from.pos().getZ() >> 4),
                        "origin chunk is still absent");
            } finally {
                CouriersServerConfig.VILLAGE_BOARD_MAX.set(previousMax);
            }
        });
    }

    private static boolean visible(Fixture f, Contract contract) {
        return BoardPackets.visibleOnBoard(contract, f.viewer.getUUID(), StationAtlas.get(f.server()),
                BoardPackets.seesAllStations(f.viewer));
    }

    private static List<String> stationNames(Fixture f) {
        BoardSnapshotPayload snapshot = BoardPackets.snapshot(f.viewer, 0, false, Optional.empty());
        return snapshot.stations().stream().map(BoardSnapshotPayload.StationHint::name).toList();
    }

    private interface Body {
        void run(Fixture f);
    }

    private static void withFlag(GameTestHelper helper, boolean flag, Body body) {
        boolean previous = CouriersServerConfig.STATIONS_SHARED_DISCOVERY.get();
        Fixture f = new Fixture(helper);
        try {
            CouriersServerConfig.STATIONS_SHARED_DISCOVERY.set(flag);
            body.run(f);
        } finally {
            CouriersServerConfig.STATIONS_SHARED_DISCOVERY.set(previous);
            f.cleanup();
        }
        helper.succeed();
    }

    private static final class Fixture {
        private static int counter;
        final GameTestHelper helper;
        final ServerPlayer viewer;
        final ContractManager manager;
        final StationAtlas.Station from;
        final StationAtlas.Station to;
        final List<UUID> created = new ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.viewer = TestPlayers.courier(helper, "shared_viewer");
            this.manager = ContractManager.get(server());
            int slot = (counter++ % 64) * 2;
            this.from = station("0 shared from " + slot, remote(slot));
            this.to = station("0 shared to " + slot, remote(slot).offset(600, 0, 0));
        }

        MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        private StationAtlas.Station station(String name, BlockPos pos) {
            UUID id = UUID.randomUUID();
            StationAtlas atlas = StationAtlas.get(server());
            atlas.register(id, name, helper.getLevel().dimension(), pos);
            created.add(id);
            return atlas.byId(id).orElseThrow();
        }

        Contract publish(StationAtlas.Station a, StationAtlas.Station b) {
            for (int seed = 1; seed <= 32; seed++) {
                Optional<Contract> c = StationContractSource.publish(helper.getLevel(), a, b, RandomSource.create(seed));
                if (c.isPresent()) {
                    return c.get();
                }
            }
            throw new IllegalStateException("NPC publish failed for every seed");
        }

        void cleanup() {
            StationAtlas atlas = StationAtlas.get(server());
            for (Contract c : List.copyOf(manager.all().toList())) {
                if (c.type() == ContractType.VILLAGE && c.state() == ContractState.OPEN
                        && (created.contains(c.issuer()) || created.contains(c.recipient()))) {
                    manager.adminCancelWithRefund(server(), c.id());
                }
            }
            for (UUID id : created) {
                atlas.remove(id);
            }
        }
    }
}
