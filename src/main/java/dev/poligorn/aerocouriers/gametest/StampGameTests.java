package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.client.WaybillCreateLayout;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.UUID;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

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

    @GameTest(template = CouriersGameTests.EMPTY)
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
        ServerPlayer courier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "courier"));
        CuriosApi.getCuriosInventory(courier).orElseThrow()
                .setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(courier, Licenses.findLicense(courier).orElseThrow());
        ContractManager.registerTestPlayer(courier);
        Economies.get(f.server()).deposit(courier.getUUID(), 50, "test");
        Contract contract = f.stamp(20, 30).contract().orElseThrow();
        f.expect(f.manager.accept(courier, contract.id()), true, "accept stamp");
        courier.setPos(Vec3.atCenterOf(helper.absolutePos(TARGET)));
        f.expect(f.manager.forceDeliver(f.server(), contract.id()), true, "force deliver");
        helper.assertValueEqual(f.manager.byId(contract.id()).orElseThrow().state(), ContractState.DELIVERED,
                "delivered");
        helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.CHEST), "original chest block at destination");
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
    public static void stampMediumContainerForHeavyCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper, LicenseRank.RANK3);
        fillChest(f, 8, 64, Items.COBBLESTONE);
        ContractManager.Outcome created = f.stamp(50, 50);
        helper.assertTrue(created.success(), "stamp M: " + created.message().getString());
        Contract contract = created.contract().orElseThrow();
        helper.assertValueEqual(contract.size(), CargoSize.M, "contract size");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 0, "mailbox");
        helper.assertValueEqual(Escrow.get(f.server()).holding(contract.id()).items().size(), 8, "escrow stacks");
        helper.startSequence()
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(CHEST).is(ModBlocks.CARGO_CONTAINER_M.get()),
                            "medium container at pickup");
                    helper.assertTrue(helper.getBlockState(CHEST.above()).is(ModBlocks.CARGO_CONTAINER_M.get()),
                            "medium upper segment");
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampLargeContainerForVeryHeavyCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper, LicenseRank.RANK3);
        fillChest(f, 24, 64, Items.COBBLESTONE);
        ContractManager.Outcome created = f.stamp(80, 80);
        helper.assertTrue(created.success(), "stamp L: " + created.message().getString());
        Contract contract = created.contract().orElseThrow();
        helper.assertValueEqual(contract.size(), CargoSize.L, "contract size");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 0, "mailbox");
        helper.assertValueEqual(Escrow.get(f.server()).holding(contract.id()).items().size(), 24, "escrow stacks");
        helper.startSequence()
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(CHEST).is(ModBlocks.CARGO_CONTAINER_L.get()),
                        "large container controller at pickup"))
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void stampRequiresLicenseAndAccess(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(CHEST, Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIRT, 8));
        ServerPlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "no_lic"));
        stranger.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
        Economies.get(helper.getLevel().getServer()).deposit(stranger.getUUID(), 100, "test");
        ContractManager.Outcome denied = ContractManager.get(helper.getLevel().getServer())
                .createFromWaybill(stranger, helper.absolutePos(CHEST),
                        new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8),
                        20, 0, 60);
        helper.assertFalse(denied.success(), "unlicensed stamp");
        helper.assertTrue(helper.getBlockState(CHEST).is(Blocks.CHEST), "chest stays if stamp is refused");
        helper.assertFalse(((ChestBlockEntity) helper.getBlockEntity(CHEST)).isEmpty(), "cargo stays");
        helper.succeed();
    }

    private static void fillChest(Fixture f, int stacks, int countPerStack, net.minecraft.world.item.Item item) {
        ChestBlockEntity chest = (ChestBlockEntity) f.helper.getBlockEntity(CHEST);
        for (int slot = 0; slot < stacks; slot++) {
            chest.setItem(slot, new ItemStack(item, countPerStack));
        }
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
            this.issuer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "stamper"));
            this.manager = ContractManager.get(helper.getLevel().getServer());
            issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
            var curios = CuriosApi.getCuriosInventory(issuer).orElseThrow();
            curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
            Licenses.setProfile(issuer, Licenses.profile(issuer).withXp(rank == LicenseRank.RANK1 ? 0
                    : rank == LicenseRank.RANK3 ? 1500 : 300));
            Licenses.tickWorn(issuer, Licenses.findLicense(issuer).orElseThrow());
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

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": expected " + (success ? "success" : "refusal") + ", got " + outcome.message().getString());
        }
    }
}
