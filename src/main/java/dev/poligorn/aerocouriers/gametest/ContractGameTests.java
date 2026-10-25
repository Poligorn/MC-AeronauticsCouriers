package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
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
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import top.theillusivec4.curios.api.CuriosApi;

/** Iterations 2–3: the contract flow with money, escrow, mailbox, the lazy container and every way to fail. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContractGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);
    /**
     * Sable 2.0.6 removes an emptied sub-level on a later tick; if the server saves before that, it throws
     * "Body has been removed" from {@code SubLevelSerializer}.
     */
    private static final int SABLE_SETTLE_TICKS = 40;

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
        f.expect(f.manager.forceDeliver(f.server(), contract.id()), false, "deliver before the container appeared");

        f.spawn(contract);
        Vector3d atPickup = f.world(CHEST);
        Vector3d atTarget = f.world(TARGET);
        f.courier.setPos(Vec3.atCenterOf(helper.absolutePos(TARGET)));
        int checks = CouriersServerConfig.DELIVERY_STILL_CHECKS.get();
        for (int i = 0; i < checks + 1; i++) {
            f.check(contract, atPickup, 0, ContractManager.Verdict.KEEP, "away from the destination");
        }
        for (int i = 0; i < checks - 1; i++) {
            f.check(contract, atTarget, 0, ContractManager.Verdict.KEEP, "still check " + i);
        }
        f.check(contract, atTarget, 50, ContractManager.Verdict.KEEP, "too fast resets the count");
        for (int i = 0; i < checks - 1; i++) {
            f.check(contract, atTarget, 0, ContractManager.Verdict.KEEP, "still check after reset " + i);
        }
        helper.assertValueEqual(f.state(contract), ContractState.IN_TRANSIT, "not yet delivered");
        f.check(contract, atTarget, 0, ContractManager.Verdict.REMOVE, "last still check delivers");
        f.check(contract, atTarget, 0, ContractManager.Verdict.REMOVE, "a delivered container removes itself");
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

    /**
     * {@code debug.allow_self_accept}: one player is issuer and courier. Money, mail, XP and {@code M_повтор}
     * stay single-account; the fee is the only net loss.
     */
    @GameTest(template = CouriersGameTests.EMPTY)
    public static void debugSelfAcceptRunsFullCycle(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        var curios = CuriosApi.getCuriosInventory(f.issuer).orElseThrow();
        curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(f.issuer, Licenses.findLicense(f.issuer).orElseThrow());
        ContractManager.registerTestPlayer(f.issuer);
        f.economy.deposit(f.issuer.getUUID(), 200, "test");

        helper.assertFalse(CouriersServerConfig.ALLOW_SELF_ACCEPT.get(), "flag off by default");
        helper.assertFalse(BoardPackets.snapshot(f.issuer, 0, false, Optional.empty()).allowSelfAccept(),
                "board snapshot hides self-accept");

        CouriersServerConfig.ALLOW_SELF_ACCEPT.set(true);
        try {
            helper.assertTrue(BoardPackets.snapshot(f.issuer, 0, false, Optional.empty()).allowSelfAccept(),
                    "board snapshot exposes self-accept");

            Contract first = f.create(20, 30);
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 179L, "after create: 200 − 21");
            ContractManager.Outcome accepted = f.manager.accept(f.issuer, first.id());
            f.expect(accepted, true, "self-accept");
            String acceptText = accepted.message().getString();
            helper.assertTrue(acceptText.contains("debug.allow_self_accept") || acceptText.contains("Debug mode")
                            || acceptText.contains("отладочный"),
                    "debug warning in accept chat: " + acceptText);
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 149L, "after collateral: 179 − 30");

            f.spawn(first);
            Vector3d atTarget = f.world(TARGET);
            f.issuer.setPos(Vec3.atCenterOf(helper.absolutePos(TARGET)));
            int checks = CouriersServerConfig.DELIVERY_STILL_CHECKS.get();
            for (int i = 0; i < checks - 1; i++) {
                f.check(first, atTarget, 0, ContractManager.Verdict.KEEP, "still " + i);
            }
            f.check(first, atTarget, 0, ContractManager.Verdict.REMOVE, "deliver");
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 199L, "after self-delivery: 149 + 20 + 30");
            helper.assertValueEqual(f.state(first), ContractState.DELIVERED, "delivered");
            helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 2, "mailbox");
            helper.assertValueEqual(Licenses.profile(f.issuer).delivered(), 1, "delivered count");
            helper.assertValueEqual(Licenses.profile(f.issuer).xp(), 10, "first delivery XP, M_repeat = 1.0");
            helper.assertTrue(Escrow.get(f.server()).holding(first.id()).isEmpty(), "escrow empty after delivery");

            f.fillChest();
            Contract second = f.create(20, 30);
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 178L, "199 − 21");
            f.expect(f.manager.accept(f.issuer, second.id()), true, "second self-accept");
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 148L, "178 − 30");
            f.spawn(second);
            for (int i = 0; i < checks - 1; i++) {
                f.check(second, atTarget, 0, ContractManager.Verdict.KEEP, "still2 " + i);
            }
            f.check(second, atTarget, 0, ContractManager.Verdict.REMOVE, "deliver2");
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 198L, "148 + 50");
            helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 4, "mail after two");
            helper.assertValueEqual(Licenses.profile(f.issuer).xp(), 19, "10 + floor(base × 0.9) = 19");

            f.fillChest();
            Contract failed = f.create(20, 30);
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 177L, "198 − 21");
            f.expect(f.manager.accept(f.issuer, failed.id()), true, "accept before fail");
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 147L, "177 − 30");
            f.expect(f.manager.abandon(f.issuer, failed.id()), true, "self-abandon");
            helper.assertValueEqual(f.state(failed), ContractState.FAILED, "failed");
            helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 197L, "147 + collateral 30 + reward 20");
            helper.assertValueEqual(Mailbox.get(f.server()).contents(f.issuer.getUUID()).size(), 6,
                    "returned cargo mailed to the same player");
            helper.assertValueEqual(Licenses.profile(f.issuer).failed(), 1, "one failure");
            helper.assertValueEqual(Licenses.profile(f.issuer).xp(), 19, "fail does not change XP");
            helper.assertTrue(Escrow.get(f.server()).holding(failed.id()).isEmpty(), "escrow empty after fail");
        } finally {
            CouriersServerConfig.ALLOW_SELF_ACCEPT.set(false);
        }
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

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void blockedPickupPausesTheDeadline(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        long dueAt = f.manager.byId(contract.id()).orElseThrow().timing().dueAt();

        int radius = CouriersServerConfig.SPAWN_SEARCH_RADIUS.get();
        CouriersServerConfig.SPAWN_SEARCH_RADIUS.set(0);
        try {
            // Radius 0 around the chest: the chest itself, the floor below and air above a non-sturdy chest top.
            f.expect(f.manager.spawnContainer(f.server(), contract.id()), false, "no free spot");
        } finally {
            CouriersServerConfig.SPAWN_SEARCH_RADIUS.set(radius);
        }
        Contract paused = f.manager.byId(contract.id()).orElseThrow();
        helper.assertValueEqual(paused.state(), ContractState.ACCEPTED, "still accepted");
        helper.assertTrue(paused.timing().dueAt() > dueAt, "The deadline must move while the pickup is blocked");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void damagedContainerDropsShareAndPaysIssuer(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        f.spawn(contract);

        ServerPlayer pirate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pirate"));
        Vec3 site = Vec3.atCenterOf(helper.absolutePos(TARGET));
        f.expect(f.manager.damage(contract.id(), new ContractManager.DropSite(helper.getLevel(), site, pirate)),
                true, "damage");
        f.expect(f.manager.damage(contract.id(), new ContractManager.DropSite(helper.getLevel(), site, pirate)),
                false, "second damage");
        helper.assertValueEqual(f.state(contract), ContractState.FAILED, "state");
        helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 129L, "issuer: 79 + reward 20 + collateral 30");
        helper.assertValueEqual(f.economy.balance(f.courier.getUUID()), 20L, "courier lost the collateral");
        helper.assertValueEqual(f.dropped(site, Items.COBBLESTONE), 16, "half of 32 cobblestone");
        helper.assertValueEqual(f.dropped(site, Items.IRON_INGOT), 2, "floor(5 × 0.5) iron");
        helper.assertTrue(Escrow.get(f.server()).holding(contract.id()).isEmpty(), "Escrow must be empty");
        helper.assertTrue(Mailbox.get(f.server()).contents(f.issuer.getUUID()).isEmpty(), "Nothing mailed to the issuer");
        helper.assertTrue(Mailbox.get(f.server()).contents(f.courier.getUUID()).isEmpty(), "Nothing mailed to the courier");
        f.clearDrops(site);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void courierBreakingOwnContainerDropsNothing(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        f.spawn(contract);

        Vec3 site = Vec3.atCenterOf(helper.absolutePos(TARGET));
        f.expect(f.manager.damage(contract.id(), new ContractManager.DropSite(helper.getLevel(), site, f.courier)),
                true, "damage");
        helper.assertValueEqual(f.state(contract), ContractState.FAILED, "state");
        helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 129L, "issuer is paid anyway");
        helper.assertValueEqual(f.dropped(site, Items.COBBLESTONE) + f.dropped(site, Items.IRON_INGOT), 0, "drops");
        helper.succeed();
    }

    /** The real path: a player breaks the block inside the Sable plot, {@code BreakEvent} reaches {@code CargoEvents}. */
    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void breakingTheContainerInThePlotDamagesIt(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        f.spawn(contract);
        ServerPlayer pirate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pirate"));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(CargoTracker.get(contract.id()).isPresent(),
                        "The container has not reported yet"))
                .thenExecute(() -> {
                    CargoTracker.Snapshot snapshot = CargoTracker.get(contract.id()).orElseThrow();
                    pirate.setPos(snapshot.position().x(), snapshot.position().y(), snapshot.position().z());
                    helper.assertTrue(pirate.gameMode.destroyBlock(snapshot.plotPos()),
                            "The pirate could not break the container at " + snapshot.plotPos());
                    helper.assertValueEqual(f.state(contract), ContractState.FAILED, "state");
                    Vec3 site = new Vec3(snapshot.position().x(), snapshot.position().y(), snapshot.position().z());
                    helper.assertValueEqual(f.dropped(site, Items.COBBLESTONE), 16, "cobblestone dropped in the world");
                    helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 129L, "issuer paid");
                    f.clearDrops(site);
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void vanishedContainerIsDestroyed(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 100, "test");
        f.economy.deposit(f.courier.getUUID(), 50, "test");
        Contract contract = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, contract.id()), true, "accept");
        f.spawn(contract);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(CargoTracker.get(contract.id()).isPresent(),
                        "The container has not reported yet"))
                .thenExecute(() -> {
                    CargoTracker.Snapshot snapshot = CargoTracker.get(contract.id()).orElseThrow();
                    helper.getLevel().removeBlock(snapshot.plotPos(), false);
                    long now = helper.getLevel().getGameTime();
                    f.manager.tick(f.server(), now + 1);
                    helper.assertValueEqual(f.state(contract), ContractState.IN_TRANSIT, "grace period");
                    f.manager.tick(f.server(), now + ContractManager.MISSING_GRACE_TICKS);
                    helper.assertValueEqual(f.state(contract), ContractState.FAILED, "destroyed after the grace period");
                    helper.assertValueEqual(f.economy.balance(f.issuer.getUUID()), 129L, "issuer paid");
                    helper.assertTrue(Mailbox.get(f.server()).contents(f.issuer.getUUID()).isEmpty(), "Cargo is lost");
                    helper.assertTrue(Mailbox.get(f.server()).contents(f.courier.getUUID()).isEmpty(), "Cargo is lost");
                })
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void fallingIntoTheVoidDestroysAndAbandonMailsCourier(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.economy.deposit(f.issuer.getUUID(), 200, "test");
        f.economy.deposit(f.courier.getUUID(), 100, "test");
        Contract lost = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, lost.id()), true, "accept");
        f.spawn(lost);
        Vector3d below = f.world(TARGET).sub(0, 1000, 0);
        f.check(lost, below, 0, ContractManager.Verdict.REMOVE, "below the world");
        helper.assertValueEqual(f.state(lost), ContractState.FAILED, "destroyed");

        f.fillChest();
        Contract dropped = f.create(20, 30);
        f.expect(f.manager.accept(f.courier, dropped.id()), true, "accept");
        f.spawn(dropped);
        f.expect(f.manager.abandon(f.courier, dropped.id()), true, "abandon");
        helper.assertValueEqual(f.state(dropped), ContractState.FAILED, "abandoned");
        helper.assertValueEqual(Mailbox.get(f.server()).contents(f.courier.getUUID()).size(), 2,
                "cargo of an abandoned in-transit contract goes to the courier");
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
            ContractManager.registerTestPlayer(courier);
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
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

        void spawn(Contract contract) {
            expect(manager.spawnContainer(server(), contract.id()), true, "container spawn");
            helper.assertValueEqual(state(contract), ContractState.IN_TRANSIT, "state after the container appeared");
        }

        Vector3d world(BlockPos relative) {
            Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
            return new Vector3d(center.x, center.y, center.z);
        }

        void check(Contract contract, Vector3d position, double speed, ContractManager.Verdict expected, String what) {
            helper.assertValueEqual(manager.checkContainer(helper.getLevel(), contract.id(), position, speed), expected, what);
        }

        int dropped(Vec3 site, Item item) {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(site, site).inflate(3)).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(item))
                    .mapToInt(ItemStack::getCount)
                    .sum();
        }

        void clearDrops(Vec3 site) {
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(site, site).inflate(3)).forEach(Entity::discard);
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
