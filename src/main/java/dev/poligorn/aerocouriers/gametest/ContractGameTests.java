package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/** Iteration 2: the command-level contract flow with money, escrow and mailbox. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContractGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);

    private ContractGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void deliveryPaysCourierAndMailsCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");

        Contract contract = f.create(20, 30);
        helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 79L, "issuer after reward + 5% fee");
        helper.assertTrue(f.chest().isEmpty(), "Cargo must leave the chest");
        helper.assertValueEqual(contract.requiredRank(), LicenseRank.RANK1, "required rank");

        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        helper.assertValueEqual(f.economy.balance(f.courier.getUUID()), 20L, "courier after collateral");
        f.expect(f.manager.deliver(f.courier, contract.id()), false, "deliver before pickup");

        f.courier.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
        f.expect(f.manager.pickup(f.courier, contract.id()), true, "pickup");
        f.expect(f.manager.deliver(f.courier, contract.id()), false, "deliver away from destination");

        f.courier.setPos(Vec3.atCenterOf(helper.absolutePos(TARGET)));
        f.expect(f.manager.deliver(f.courier, contract.id()), true, "deliver");
        f.expect(f.manager.deliver(f.courier, contract.id()), false, "second deliver");
        helper.assertValueEqual(f.economy.balance(f.courier.getUUID()), 70L, "courier after reward + collateral");
        helper.assertValueEqual(f.state(contract), ContractState.DELIVERED, "state");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 2, "issuer mailbox");
        helper.assertValueEqual(Licenses.profile(f.courier).delivered(), 1, "delivered count");
        // floor(10 + 7.07/200 + (32 × 1.0 + 5 × 0.11)/50) = 10
        helper.assertValueEqual(Licenses.profile(f.courier).xp(), 10, "xp");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void timeoutReturnsCollateralAndCargoToIssuer(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");

        long now = ContractManager.now(f.server());
        f.manager.tick(f.server(), now + contract.timing().deadline());
        helper.assertValueEqual(f.state(contract), ContractState.ACCEPTED, "still inside the late window");
        f.manager.tick(f.server(), now + contract.timing().deadline() * 2);
        helper.assertValueEqual(f.state(contract), ContractState.FAILED, "state after the late window");
        helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 129L, "issuer: 79 + reward 20 + collateral 30");
        helper.assertValueEqual(f.economy.balance(f.courier.getUUID()), 20L, "courier lost the collateral");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 2,
                "cargo never picked up goes back to the issuer");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void openContractExpiresAndCancelIsIssuerOnly(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        Contract first = f.create(20, 0);
        f.expect(f.manager.cancel(f.courier, first.id()), false, "cancel by a stranger");
        f.expect(f.manager.cancel(f.issuer, first.id()), true, "cancel by the issuer");
        helper.assertValueEqual(f.state(first), ContractState.EXPIRED, "cancelled");
        helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 99L, "reward back, fee burned");

        f.fillChest();
        Contract second = f.create(20, 0);
        f.manager.tick(f.server(), second.timing().expiresAt());
        helper.assertValueEqual(f.state(second), ContractState.EXPIRED, "expired on the board");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 4, "both cargos mailed");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void acceptAndCreateGuards(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.issuer, contract.id()), false, "own contract");
        f.expect(f.manager.accept(f.courier, contract.id()), false, "courier without money");
        helper.assertValueEqual(f.state(contract), ContractState.OPEN, "still open");

        ServerPlayer unlicensed = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "no_license"));
        f.economy.deposit(unlicensed.getUUID(), 100, "test");
        f.expect(f.manager.accept(unlicensed, contract.id()), false, "no license");
        helper.assertValueEqual(f.economy.balance(unlicensed.getUUID()), 100L, "nothing withdrawn");

        f.chest().setItem(0, new ItemStack(Items.SHULKER_BOX));
        f.expect(f.tryCreate(20, 0), false, "shulker box in cargo");
        helper.assertTrue(!f.chest().isEmpty(), "Rejected cargo stays in the chest");
        f.chest().clearContent();
        f.chest().setItem(0, new ItemStack(Items.DIRT));
        f.expect(f.tryCreate(0, 0), false, "zero reward");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void contractCodecRoundTrip(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        Contract contract = f.create(20, 30);
        Tag encoded = Contract.CODEC.encodeStart(NbtOps.INSTANCE, contract).getOrThrow();
        helper.assertValueEqual(Contract.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow(), contract, "contract");
        helper.succeed();
    }

    /** What a server restart does to the three SavedData files. */
    @GameTest(template = CouriersGameTests.EMPTY)
    public static void savedDataSurvivesReload(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        ContractManager reloaded = ContractManager.load(f.manager.save(new CompoundTag(), registries), registries);
        helper.assertValueEqual(reloaded.byId(contract.id()).orElseThrow(), f.manager.byId(contract.id()).orElseThrow(),
                "contract after reload");

        Escrow escrow = Escrow.get(f.server());
        Escrow.Holding before = escrow.holding(contract.id());
        Escrow.Holding after = Escrow.load(escrow.save(new CompoundTag(), registries), registries).holding(contract.id());
        helper.assertValueEqual(after.reward(), 20L, "escrowed reward");
        helper.assertValueEqual(after.collateral(), 30L, "escrowed collateral");
        helper.assertTrue(sameStacks(before.items(), after.items()), "escrowed items after reload");

        Mailbox mailbox = Mailbox.get(f.server());
        mailbox.post(f.courier.getUUID(), List.of(new ItemStack(Items.DIAMOND, 3)));
        Mailbox mailReloaded = Mailbox.load(mailbox.save(new CompoundTag(), registries), registries);
        helper.assertTrue(sameStacks(mailbox.contents(f.courier.getUUID()),
                mailReloaded.contents(f.courier.getUUID())), "mailbox after reload");
        helper.succeed();
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer issuer;
        final ServerPlayer courier;
        final ContractManager manager;
        final Economy economy;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.issuer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "issuer"));
            this.courier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "courier"));
            this.manager = ContractManager.get(server());
            this.economy = Economies.get(server());
            issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
            var curios = CuriosApi.getCuriosInventory(courier).orElseThrow();
            curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
            Licenses.tickWorn(courier, Licenses.findLicense(courier).orElseThrow());
            helper.setBlock(CHEST, Blocks.CHEST);
            fillChest();
        }

        void fillChest() {
            chest().setItem(0, new ItemStack(Items.COBBLESTONE, 32));
            chest().setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        }

        ChestBlockEntity chest() {
            return (ChestBlockEntity) helper.getBlockEntity(CHEST);
        }

        MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        ContractManager.Outcome tryCreate(long reward, long collateral) {
            Destination destination = new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 2);
            return manager.create(issuer, chest(), helper.absolutePos(CHEST), destination, reward, collateral, 60);
        }

        Contract create(long reward, long collateral) {
            ContractManager.Outcome outcome = tryCreate(reward, collateral);
            expect(outcome, true, "create");
            return outcome.contract().orElseThrow();
        }

        ContractState state(Contract contract) {
            return manager.byId(contract.id()).orElseThrow().state();
        }

        void expect(ContractManager.Outcome outcome, boolean success, String what) {
            helper.assertTrue(outcome.success() == success,
                    what + ": expected " + (success ? "success" : "refusal") + ", got " + outcome.message().getString());
        }
    }
}
