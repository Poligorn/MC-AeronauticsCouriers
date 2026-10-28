package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

/** OPEN preplaced cargo stays static until a courier accepts (playtest item 7). */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OpenCargoLockGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);
    private static final int SABLE_SETTLE_TICKS = 40;

    private OpenCargoLockGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void acceptUnlocksSablePhysics(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Contract contract = f.stamp(20, 20).contract().orElseThrow();
        ServerPlayer courier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "haul"));
        CuriosApi.getCuriosInventory(courier).orElseThrow()
                .setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(courier, Licenses.findLicense(courier).orElseThrow());
        ContractManager.registerTestPlayer(courier);
        Economies.get(f.server()).deposit(courier.getUUID(), 50, "test");

        f.expect(f.manager.accept(courier, contract.id()), true, "accept");
        helper.assertValueEqual(f.manager.byId(contract.id()).orElseThrow().state(), ContractState.IN_TRANSIT,
                "in transit after accept");
        helper.startSequence()
                .thenIdle(SABLE_SETTLE_TICKS)
                .thenExecute(() -> helper.assertFalse(CargoContainerBlock.isPhysicsLocked(helper.getBlockState(CHEST)),
                        "locked cleared"))
                .thenWaitUntil(() -> helper.assertTrue(
                        CargoTracker.get(contract.id()).isPresent()
                                || helper.getBlockState(CHEST).is(ModBlocks.CARGO_CONTAINER_S.get()),
                        "Sable assembled after accept"))
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void survivalCannotMineOpenCargo(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.stamp(20, 20);
        helper.assertTrue(helper.getBlockState(CHEST).is(ModBlocks.CARGO_CONTAINER_S.get()), "container placed");
        float progress = helper.getBlockState(CHEST).getDestroyProgress(f.issuer, helper.getLevel(),
                helper.absolutePos(CHEST));
        helper.assertTrue(progress <= 0.0F, "survival mining progress is zero while locked");
        helper.succeed();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer issuer;
        final ContractManager manager;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.issuer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "stamper"));
            this.manager = ContractManager.get(helper.getLevel().getServer());
            issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
            var curios = CuriosApi.getCuriosInventory(issuer).orElseThrow();
            curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
            Licenses.setProfile(issuer, Licenses.profile(issuer).withXp(0));
            Licenses.tickWorn(issuer, Licenses.findLicense(issuer).orElseThrow());
            Economies.get(helper.getLevel().getServer()).deposit(issuer.getUUID(), 100, "test");
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
