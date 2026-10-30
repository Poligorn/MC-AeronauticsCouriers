package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoStructure;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.client.WaybillCreateLayout;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Stamp-created orders: in-place container, no dupe, cancel/expire mail the cargo back. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StampGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);
    private static final int SABLE_SETTLE_TICKS = 40;

    private StampGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampCreatesFromChestWithoutDuping(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        ContractManager.Outcome created = f.stamp(20, 20);
        helper.assertTrue(created.success(), "stamp: " + created.message().getString());
        Contract contract = created.contract().orElseThrow();
        helper.assertTrue(contract.preplaced(), "stamp orders are preplaced");
        helper.assertValueEqual(contract.state(), ContractState.OPEN, "state");
        helper.assertFalse(helper.getBlockState(CHEST).is(Blocks.CHEST), "source chest must be gone");
        helper.assertTrue(Escrow.get(f.server()).holding(contract.id()).items().size() == 2,
                "cargo lives only in escrow");
        helper.assertValueEqual(droppedAround(helper, CHEST), 0, "no world drops (no dupe)");
        helper.assertValueEqual(Economies.get(f.server()).balance(f.issuer.getUUID()), 79L,
                "reward + 5% fee taken");
        helper.assertTrue(helper.getBlockState(CHEST).is(ModBlocks.CARGO_CONTAINER_S.get()),
                "stamp places a locked container at pickup");
        helper.assertTrue(CargoContainerBlock.isPhysicsLocked(helper.getBlockState(CHEST)),
                "OPEN stamp container has no Sable physics yet");
        helper.assertTrue(CargoTracker.get(contract.id()).isEmpty(), "tracker empty until accept");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampCancelReturnsCargoToMailbox(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract contract = f.stamp(20, 20).contract().orElseThrow();
        f.expect(f.manager.cancel(f.issuer, contract.id()), true, "cancel");
        helper.assertValueEqual(f.manager.byId(contract.id()).orElseThrow().state(), ContractState.EXPIRED,
                "cancelled");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 2,
                "cargo returned to mailbox");
        helper.assertTrue(Escrow.get(f.server()).holding(contract.id()).isEmpty(), "escrow emptied");
        helper.startSequence()
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenExecute(() -> helper.assertFalse(helper.getBlockState(CHEST).is(ModBlocks.CARGO_CONTAINER_S.get()),
                        "physical container is gone after cancel"))
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = ContractGameTests.CLOCK_BATCH)
    public static void stampExpireReturnsCargoToMailbox(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract contract = f.stamp(20, 20).contract().orElseThrow();
        f.manager.tick(f.server(), contract.timing().expiresAt());
        helper.assertValueEqual(f.manager.byId(contract.id()).orElseThrow().state(), ContractState.EXPIRED,
                "expired on the board");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 2,
                "expired stamp cargo goes to mailbox");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void stampDeliveryRestoresSourceChestAtDestination(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        ServerPlayer courier = TestPlayers.courier(helper, "courier");
        Economies.get(f.server()).deposit(courier.getUUID(), 50, "test");
        Contract contract = f.stamp(20, 20).contract().orElseThrow();
        f.expect(f.manager.accept(courier, contract.id()), true, "accept stamp");
        courier.setPos(Vec3.atCenterOf(helper.absolutePos(TARGET)));
        f.expect(f.manager.forceDeliver(f.server(), contract.id()), true, "force deliver");
        helper.assertValueEqual(f.manager.byId(contract.id()).orElseThrow().state(), ContractState.DELIVERED,
                "delivered");
        // forceDeliver restores the chest where the tracker last saw the container, which may still be the pickup.
        helper.assertTrue(restoredChestWith(helper, Items.COBBLESTONE, 32), "original chest restored with the cargo");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 0, "not mailed");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void stampCreateLayoutFitsGuiScales(GameTestHelper helper) {
        java.util.List<String> failures = WaybillCreateLayout.checkCommonScales();
        helper.assertTrue(failures.isEmpty(), "stamp layout overlaps at GUI scales 2–4: " + failures);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampHonoursRaisedMinRank(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        ContractManager.Outcome created = f.manager.createFromWaybill(
                f.issuer, helper.absolutePos(CHEST),
                new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8),
                20, 20, 60, 3);
        helper.assertTrue(created.success(), "raised rank stamp: " + created.message().getString());
        helper.assertValueEqual(created.contract().orElseThrow().requiredRank(), LicenseRank.RANK3,
                "issuer floor raises required rank");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampMediumContainerForHeavyCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper, LicenseRank.RANK1);
        fillChest(f, 8, 64, Items.COBBLESTONE);
        ContractManager.Outcome created = f.stamp(50, f.minCollateral(50));
        helper.assertTrue(created.success(), "stamp M: " + created.message().getString());
        helper.assertValueEqual(Economies.get(f.server()).balance(f.issuer.getUUID()), 46L,
                "reward + size-scaled fee (50 + 4)");
        Contract contract = created.contract().orElseThrow();
        helper.assertValueEqual(contract.size(), CargoSize.M, "contract size");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 0, "mailbox");
        helper.assertValueEqual(Escrow.get(f.server()).holding(contract.id()).items().size(), 8, "escrow stacks");
        helper.startSequence()
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenExecute(() -> assertStructureAt(helper, contract, CargoSize.M, ModBlocks.CARGO_CONTAINER_M.get()))
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampLargeContainerForVeryHeavyCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper, LicenseRank.RANK1);
        fillChest(f, 24, 64, Items.COBBLESTONE);
        ContractManager.Outcome created = f.stamp(80, f.minCollateral(80));
        helper.assertTrue(created.success(), "stamp L: " + created.message().getString());
        helper.assertValueEqual(Economies.get(f.server()).balance(f.issuer.getUUID()), 10L,
                "reward + size-scaled fee (80 + 10)");
        Contract contract = created.contract().orElseThrow();
        helper.assertValueEqual(contract.size(), CargoSize.L, "contract size");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 0, "mailbox");
        helper.assertValueEqual(Escrow.get(f.server()).holding(contract.id()).items().size(), 24, "escrow stacks");
        helper.startSequence()
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenExecute(() -> assertStructureAt(helper, contract, CargoSize.L, ModBlocks.CARGO_CONTAINER_L.get()))
                .thenSucceed();
    }

    /** The anchor may shift off the chest ({@code CargoSpawner.resolveAnchor}); the pickup records where it went. */
    private static void assertStructureAt(GameTestHelper helper, Contract contract, CargoSize size,
                                          net.minecraft.world.level.block.Block block) {
        BlockPos anchor = contract.origin().pos();
        for (BlockPos offset : CargoStructure.offsets(size)) {
            helper.assertTrue(helper.getLevel().getBlockState(anchor.offset(offset)).is(block),
                    size + " container part at " + anchor.offset(offset).toShortString());
        }
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void stampDoesNotRequireLicense(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(CHEST, Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIRT, 8));
        ServerPlayer stranger = TestPlayers.player(helper, "no_lic");
        stranger.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
        stranger.getInventory().add(dev.poligorn.aerocouriers.item.Waybills.stack(1));
        Economies.get(helper.getLevel().getServer()).deposit(stranger.getUUID(), 100, "test");
        ServerPlayer remote = TestPlayers.player(helper, "far");
        remote.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)).add(64, 0, 64));
        remote.getInventory().add(dev.poligorn.aerocouriers.item.Waybills.stack(1));
        Economies.get(helper.getLevel().getServer()).deposit(remote.getUUID(), 100, "test");
        ContractManager manager = ContractManager.get(helper.getLevel().getServer());
        Destination dest = new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8);
        ContractManager.Outcome denied = manager.createFromWaybill(remote, helper.absolutePos(CHEST), dest, 20, 20, 60);
        helper.assertFalse(denied.success(), "stamp from out of reach");
        helper.assertTrue(helper.getBlockState(CHEST).is(Blocks.CHEST), "chest stays if stamp is refused");
        ContractManager.Outcome created = manager.createFromWaybill(stranger, helper.absolutePos(CHEST), dest, 20, 20, 60);
        helper.assertTrue(created.success(), "unlicensed waybill: " + created.message().getString());
        helper.succeed();
    }

    private static void fillChest(Fixture f, int stacks, int countPerStack, net.minecraft.world.item.Item item) {
        ChestBlockEntity chest = (ChestBlockEntity) f.helper.getBlockEntity(CHEST);
        for (int slot = 0; slot < stacks; slot++) {
            chest.setItem(slot, new ItemStack(item, countPerStack));
        }
    }

    private static boolean restoredChestWith(GameTestHelper helper, net.minecraft.world.item.Item item, int count) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 7, 3, 7)) {
            if (helper.getBlockState(pos).is(Blocks.CHEST)
                    && helper.getBlockEntity(pos) instanceof ChestBlockEntity chest
                    && chest.countItem(item) == count) {
                return true;
            }
        }
        return false;
    }

    private static int droppedAround(GameTestHelper helper, BlockPos relative) {
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(relative));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4)).stream()
                .mapToInt(e -> e.getItem().getCount())
                .sum();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer issuer;
        final ContractManager manager;

        Fixture(GameTestHelper helper) {
            this(helper, LicenseRank.RANK1);
        }

        Fixture(GameTestHelper helper, LicenseRank rank) {
            this.helper = helper;
            this.issuer = TestPlayers.player(helper, "stamper");
            this.manager = ContractManager.get(helper.getLevel().getServer());
            issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
            Licenses.setProfile(issuer, Licenses.profile(issuer).withXp(rank == LicenseRank.RANK1 ? 0
                    : rank == LicenseRank.RANK3 ? 1500 : 300));
            TestPlayers.license(issuer);
            TestPlayers.giveWaybills(issuer, 4);
            Economies.get(helper.getLevel().getServer()).deposit(issuer.getUUID(),
                    rank == LicenseRank.RANK3 ? 10_000 : 100, "test");
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            helper.setBlock(CHEST, Blocks.CHEST);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
            chest.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        }

        net.minecraft.server.MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        ContractManager.Outcome stamp(long reward, long collateral) {
            return manager.createFromWaybill(issuer, helper.absolutePos(CHEST),
                    new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8),
                    reward, collateral, 60);
        }

        /** Heavy cargo is worth more than the reward, so the collateral profile sets the floor. */
        long minCollateral(long reward) {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
            java.util.List<ItemStack> cargo = new java.util.ArrayList<>();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (!chest.getItem(slot).isEmpty()) {
                    cargo.add(chest.getItem(slot));
                }
            }
            return Pricing.minP2pCollateral(reward, cargo);
        }

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": expected " + (success ? "success" : "refusal") + ", got " + outcome.message().getString());
        }
    }
}
