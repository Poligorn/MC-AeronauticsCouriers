package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.Optional;
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

/** Iteration 4: the board snapshot is built from existing services; operator actions stay gated. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);

    private BoardGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void snapshotListsOpenContractAndOperatorActionsStayGated(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));

        ServerPlayer issuer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "issuer"));
        issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
        Economies.get(helper.getLevel().getServer()).deposit(issuer.getUUID(), 100, "test");

        ServerPlayer courier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "courier"));
        var curios = CuriosApi.getCuriosInventory(courier).orElseThrow();
        curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(
                dev.poligorn.aerocouriers.registry.ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(courier, Licenses.findLicense(courier).orElseThrow());
        ContractManager.registerTestPlayer(courier);

        ContractManager manager = ContractManager.get(helper.getLevel().getServer());
        ContractManager.Outcome created = manager.create(issuer, chest, helper.absolutePos(CHEST),
                new dev.poligorn.aerocouriers.contract.Destination(
                        helper.getLevel().dimension(), helper.absolutePos(TARGET), 2),
                20, 30, 60);
        helper.assertTrue(created.success(), "create: " + created.message().getString());
        Contract contract = created.contract().orElseThrow();

        BoardSnapshotPayload issuerView = BoardPackets.snapshot(issuer, 0, false, Optional.empty());
        helper.assertTrue(issuerView.board().stream().anyMatch(e -> e.id().equals(contract.id())),
                "open contract is on the board");
        helper.assertTrue(issuerView.mine().stream().anyMatch(e -> e.id().equals(contract.id()) && e.role().equals("issuer")),
                "issuer sees it under Mine");
        helper.assertFalse(issuerView.operator(), "FakePlayer is not an operator");
        helper.assertFalse(issuerView.allowSelfAccept(), "self-accept is off by default");
        helper.assertFalse(issuerView.board().getFirst().manifest().isEmpty(), "manifest is in the snapshot");
        helper.assertValueEqual(issuerView.board().getFirst().manifest().getFirst().count(), 32, "stack count");

        BoardSnapshotPayload courierView = BoardPackets.snapshot(courier, 0, false, Optional.empty());
        helper.assertTrue(courierView.licensed(), "wearing a bound license");
        helper.assertTrue(courierView.board().stream().anyMatch(e -> e.id().equals(contract.id())),
                "courier sees the open contract");

        ContractManager.Outcome denied = BoardPackets.handleOperator(courier,
                OperatorBoardPayload.simple(OperatorBoardPayload.Action.ADD_MONEY, Optional.empty()));
        helper.assertFalse(denied.success(), "operator tab is gated on the server");
        helper.assertValueEqual(Economies.get(helper.getLevel().getServer()).balance(courier.getUUID()), 0L,
                "non-op did not receive test money");

        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void boardRequiresOrderTabletUnlessEquipped(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tablet"));
        helper.assertFalse(OrderTablets.canAccessBoard(player), "no tablet by default");

        var tabletCurios = CuriosApi.getCuriosInventory(player).orElseThrow();
        tabletCurios.setEquippedCurio(OrderTablets.CURIOS_SLOT, 0,
                new ItemStack(ModItems.ORDER_TABLET.get()));
        helper.assertTrue(OrderTablets.canAccessBoard(player), "tablet in Curios slot");

        tabletCurios.setEquippedCurio(OrderTablets.CURIOS_SLOT, 0, ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(ModItems.ORDER_TABLET.get()));
        helper.assertTrue(OrderTablets.canAccessBoard(player), "tablet in main hand");

        helper.succeed();
    }
}
