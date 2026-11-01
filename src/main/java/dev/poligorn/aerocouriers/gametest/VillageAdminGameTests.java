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
import dev.poligorn.aerocouriers.client.board.BoardFocus;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.client.station.CorkBoardLayout;
import dev.poligorn.aerocouriers.client.station.CorkNoteStyle;
import dev.poligorn.aerocouriers.client.station.CorkNoteTooltip;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.village.VillageAdmin;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin.packet")
    public static void adminPacketDeniedForNonOperator(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(f::ready)
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

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin.rename")
    public static void renameChecksLengthAndUniqueness(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(f::ready)
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

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin.rep")
    public static void reputationClampsAndAdds(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(f::ready)
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

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin.progress")
    public static void inProgressNoteStaysUntilDelivered(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(f::ready)
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
    public static void corkBoardColumnsFollowScaledWidth(GameTestHelper helper) {
        CorkBoardLayout large = CorkBoardLayout.of(640, 360);
        helper.assertValueEqual(large.cols, 4, "640×360 columns");
        helper.assertValueEqual(large.noteW, CorkBoardLayout.NOTE_W, "640 note width");
        helper.assertValueEqual(large.noteH, CorkBoardLayout.NOTE_H, "640 note height");
        helper.assertValueEqual(large.visibleRows, 2, "640 visible rows");
        helper.assertFalse(large.narrow, "640 is the large frame");

        CorkBoardLayout mid = CorkBoardLayout.of(480, 270);
        helper.assertValueEqual(mid.cols, 3, "480×270 columns");
        helper.assertValueEqual(mid.noteW, CorkBoardLayout.NOTE_W, "480 note width stays");
        helper.assertValueEqual(mid.noteH, CorkBoardLayout.NOTE_H, "480 note height stays");
        helper.assertTrue(mid.narrow, "480 uses narrow chrome");

        CorkBoardLayout tiny = CorkBoardLayout.of(384, 216);
        helper.assertValueEqual(tiny.cols, 2, "narrower than 480 columns");
        helper.assertValueEqual(tiny.noteH, CorkBoardLayout.NOTE_H, "tiny note height stays");

        CorkBoardLayout wide = CorkBoardLayout.of(960, 540);
        helper.assertValueEqual(wide.cols, 4, "960 still max 4 columns");
        helper.assertValueEqual(wide.winW, CorkBoardLayout.LARGE_W, "frame stays 640 when the screen is larger");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void corkBoardHeaderDoesNotOverlap(GameTestHelper helper) {
        CorkBoardLayout narrow = CorkBoardLayout.of(480, 270);
        helper.assertTrue(narrow.narrow, "480 is narrow chrome");
        helper.assertTrue(narrow.titleY + 8 <= narrow.subtitleY, "title vs subtitle");
        helper.assertTrue(narrow.subtitleY + 8 <= narrow.repLabelY, "subtitle vs reputation");
        helper.assertTrue(narrow.rankY >= narrow.repLabelY + 9, "rank below reputation");
        helper.assertTrue(narrow.nameplate.h() >= 22, "nameplate fits title+subtitle");
        helper.assertTrue(narrow.titleY >= narrow.nameplate.y(), "title inside nameplate");
        helper.assertTrue(narrow.subtitleY + 8 <= narrow.nameplate.y() + narrow.nameplate.h(),
                "subtitle inside nameplate");
        helper.assertTrue(narrow.filterBar.y() >= narrow.rankY + 8, "filter below rank");

        CorkBoardLayout large = CorkBoardLayout.of(640, 360);
        helper.assertFalse(large.narrow, "640 is large chrome");
        helper.assertTrue(large.titleY + 8 <= large.subtitleY, "large title vs subtitle");
        helper.assertTrue(large.subtitleY + 8 <= large.repLabelY, "large subtitle vs reputation");
        helper.assertValueEqual(large.rankY, large.repLabelY, "large rank on reputation row");
        helper.assertTrue(large.nameplate.h() >= 22, "large nameplate");
        helper.assertTrue(large.subtitleY + 8 <= large.nameplate.y() + large.nameplate.h(),
                "large subtitle inside nameplate");
        helper.assertTrue(large.filterBar.y() >= large.headerPlank.y() + large.headerPlank.h(),
                "large filter below header plank");
        helper.assertTrue(timerInsideHeader(narrow), "narrow timer chip inside header");
        helper.assertTrue(timerInsideHeader(large), "large timer chip inside header");
        String time = VillageRefreshUi.formatDuration(11_560);
        helper.assertValueEqual(VillageRefreshUi.chipText(11_560, 8, String::length), time,
                "overflowing chip falls back to icon+time");
        helper.assertTrue(VillageRefreshUi.chipText(11_560, 80, String::length).contains(time),
                "wide chip keeps the countdown");
        String full = VillageRefreshUi.countdownLabel(11_560).getString();
        helper.assertValueEqual(VillageRefreshUi.chipText(11_560, full.length(), String::length), full,
                "full «new orders in» stays when the chip is wide enough");
        helper.assertValueEqual(VillageRefreshUi.chipLabel(11_560, 15 + full.length() + 4, String::length), full,
                "chipLabel keeps the sentence when the chip is wide enough");
        helper.assertTrue(narrow.footerFitsInFrame(), "GUI-4 footer stays inside the frame");
        helper.assertTrue(large.footerFitsInFrame(), "large footer stays inside the frame");
        helper.assertTrue(CorkBoardLayout.of(384, 216).footerFitsInFrame(), "tiny footer stays inside the frame");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void corkBoardFooterFitsGui4Frame(GameTestHelper helper) {
        CorkBoardLayout gui4 = CorkBoardLayout.of(480, 270);
        helper.assertTrue(gui4.narrow, "GUI 4 uses the 480×270 frame");
        helper.assertTrue(gui4.footerFitsInFrame(), "footer plank fills the compact inner hole");
        helper.assertValueEqual(gui4.cork.h(), 160, "compact cork height unchanged");
        helper.assertValueEqual(gui4.innerHole.y() + gui4.innerHole.h(), 259,
                "compact hole exclusive bottom is the last transparent row + 1");
        helper.assertTrue(gui4.footerPlank.y() >= gui4.cork.y() + gui4.cork.h(),
                "cork height leaves room for the footer");
        helper.assertTrue(gui4.footerCountY + CorkBoardLayout.TEXT
                        <= gui4.innerHole.y() + gui4.innerHole.h(),
                "footer text cell is above the inner bottom");
        helper.assertTrue(gui4.footerPlank.h() >= CorkBoardLayout.TEXT,
                "footer plank fits the 8 px line");
        helper.assertTrue(gui4.footerHintY == gui4.footerCountY, "count and hint share one line");

        CorkBoardLayout large = CorkBoardLayout.of(640, 360);
        helper.assertTrue(large.footerFitsInFrame(), "large footer plank fills the inner hole");
        helper.assertValueEqual(large.cork.h(), 243, "large cork height unchanged");
        helper.assertValueEqual(large.cork.y(), 77, "large cork top unchanged");
        helper.assertValueEqual(large.innerHole.y() + large.innerHole.h(), 343,
                "large hole exclusive bottom is the last transparent row + 1");
        helper.assertValueEqual(large.footerCountY, 324, "large footer text stays on the 16 px line");

        CorkBoardLayout tiny = CorkBoardLayout.of(384, 216);
        helper.assertTrue(tiny.footerFitsInFrame(), "9-slice footer reaches the inner hole");
        helper.assertValueEqual(tiny.noteH, CorkBoardLayout.NOTE_H, "tiny notes stay 136×108");
        helper.succeed();
    }

    private static boolean timerInsideHeader(CorkBoardLayout layout) {
        CorkBoardLayout.Rect header = layout.headerPlank;
        CorkBoardLayout.Rect chip = layout.timerChip;
        return chip.x() >= header.x()
                && chip.y() >= header.y()
                && chip.x() + chip.w() <= header.x() + header.w()
                && chip.y() + chip.h() <= header.y() + header.h();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void boardFocusKeepsStationContractVisible(GameTestHelper helper) {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        BoardSnapshotPayload.BoardEntry extra = focusSample(id, "Речной Берег", 90L);
        helper.assertTrue(BoardFocus.ensureVisible(List.of(), id, extra).getFirst().id().equals(id),
                "ensureVisible prepends the station row");
        helper.assertValueEqual(BoardFocus.ensureVisible(List.of(extra), id, extra).size(), 1,
                "ensureVisible does not duplicate");
        helper.assertTrue(BoardFocus.resolve(null, id, extra).isPresent(),
                "resolve uses the station extra when the tablet snapshot omitted it");
        helper.assertTrue(BoardFocus.resolve(null, id, null, List.of(extra)).isPresent(),
                "resolve finds the station extras list");
        UUID other = UUID.fromString("11111111-2222-3333-4444-555555555555");
        helper.assertTrue(BoardFocus.ensureVisible(List.of(), other, extra).isEmpty(),
                "ensureVisible ignores a mismatched extra");
        helper.assertValueEqual(BoardFocus.mergeMissing(List.of(), List.of(extra)).size(), 1,
                "mergeMissing injects omitted village rows");
        helper.assertValueEqual(BoardFocus.mergeMissing(List.of(extra), List.of(extra)).size(), 1,
                "mergeMissing skips an already-listed row");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin")
    public static void corkNoteHoverTooltipHasContent(GameTestHelper helper) {
        BoardSnapshotPayload.BoardEntry extra = focusSample(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "Речной Берег", 90L);
        CorkNoteStyle style = CorkNoteStyle.of(extra, 3, "");
        List<net.minecraft.network.chat.Component> lines = CorkNoteTooltip.lines(extra, style, false);
        helper.assertTrue(lines.size() >= 5, "hover lists dest, cargo, deadline, money, hint");
        helper.assertTrue(lines.stream().noneMatch(c -> c.getString().isBlank()), "no blank hover lines");
        List<net.minecraft.network.chat.Component> locked = CorkNoteTooltip.lines(extra,
                CorkNoteStyle.of(extra, 1, ""), true);
        helper.assertTrue(locked.stream().anyMatch(c -> !c.getString().isBlank()),
                "read-only hover still has a tablet hint");
        CorkBoardLayout.Rect note = new CorkBoardLayout.Rect(20, 80, 136, 108);
        CorkBoardLayout.Rect tip = CorkNoteTooltip.place(480, 270, note, 160, 72);
        helper.assertFalse(CorkNoteTooltip.overlaps(note, tip), "tooltip sits beside the note");
        helper.assertTrue(tip.x() >= 4 && tip.y() >= 4, "tooltip stays on screen");
        CorkBoardLayout.Rect cramped = CorkNoteTooltip.place(220, 160,
                new CorkBoardLayout.Rect(40, 20, 136, 108), 160, 72);
        helper.assertTrue(cramped.x() >= 4 && cramped.y() >= 4, "cramped tooltip stays on screen");
        helper.succeed();
    }

    private static BoardSnapshotPayload.BoardEntry focusSample(UUID id, String dest, long reward) {
        return new BoardSnapshotPayload.BoardEntry(
                id,
                "abc123",
                ContractState.OPEN,
                "issuer",
                "Ясная Поляна",
                dest,
                100,
                "S",
                "4.0 / 64",
                reward,
                10L,
                LicenseRank.RANK1,
                60L,
                55L,
                List.of(),
                Optional.empty(),
                "Ясная Поляна",
                true,
                Level.OVERWORLD,
                BlockPos.ZERO,
                Level.OVERWORLD,
                new BlockPos(10, 64, 10),
                List.of(),
                27,
                false,
                0,
                -1,
                30,
                true,
                false,
                true,
                true,
                9,
                64,
                4.0,
                3L,
                false);
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

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.village.admin.policy")
    public static void policyPersistsOnAtlas(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(f::ready)
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
        final java.util.Set<UUID> owned = new java.util.HashSet<>();

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

        void ready() {
            station(A);
            station(B);
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
            owned.add(found.id());
            return found;
        }

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": " + outcome.message().getString());
        }

        void cleanup() {
            StationAtlas atlas = StationAtlas.get(server());
            owned.forEach(atlas::remove);
            if (helper.getBlockEntity(A) instanceof PostStationBlockEntity) {
                helper.setBlock(A, Blocks.AIR);
            }
            if (helper.getBlockEntity(B) instanceof PostStationBlockEntity) {
                helper.setBlock(B, Blocks.AIR);
            }
        }
    }
}
