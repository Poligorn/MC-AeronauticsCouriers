package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoStructure;
import dev.poligorn.aerocouriers.cargo.WaybillFacing;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Paper waybill faces the publisher (frozen at create). Models put the sheet on {@code facing + 90°} clockwise,
 * so the spawner stores {@code facing = waybillFace.counterClockWise}.
 */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaybillFacingGameTests {
    private static final BlockPos ANCHOR = new BlockPos(3, 1, 3);
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(6, 1, 6);

    private WaybillFacingGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void formulaMatchesBlockstate(GameTestHelper helper) {
        helper.assertValueEqual(WaybillFacing.toContainerFacing(Direction.EAST), Direction.NORTH,
                "east sheet → facing north (legacy)");
        helper.assertValueEqual(WaybillFacing.toContainerFacing(Direction.SOUTH), Direction.EAST, "south sheet");
        helper.assertValueEqual(WaybillFacing.toContainerFacing(Direction.WEST), Direction.SOUTH, "west sheet");
        helper.assertValueEqual(WaybillFacing.toContainerFacing(Direction.NORTH), Direction.WEST, "north sheet");
        for (Direction face : Direction.Plane.HORIZONTAL) {
            helper.assertValueEqual(WaybillFacing.fromContainerFacing(WaybillFacing.toContainerFacing(face)), face,
                    "round-trip " + face);
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void chooseFourCardinals(GameTestHelper helper) {
        Vec3 center = new Vec3(0.5, 0.0, 0.5);
        helper.assertValueEqual(chooseAt(center, 3, 0), Direction.EAST, "east of box");
        helper.assertValueEqual(chooseAt(center, -3, 0), Direction.WEST, "west of box");
        helper.assertValueEqual(chooseAt(center, 0, 3), Direction.SOUTH, "south of box");
        helper.assertValueEqual(chooseAt(center, 0, -3), Direction.NORTH, "north of box");
        helper.assertValueEqual(chooseAt(center, 3, 0, 40, Direction.WEST), Direction.EAST,
                "height ignored");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void chooseDiagonalUsesLook(GameTestHelper helper) {
        Vec3 center = new Vec3(0.5, 0.0, 0.5);
        helper.assertValueEqual(chooseAt(center, 3, 3, 0, Direction.SOUTH), Direction.SOUTH,
                "NE diagonal looking south");
        helper.assertValueEqual(chooseAt(center, 3, 3, 0, Direction.EAST), Direction.EAST,
                "NE diagonal looking east");
        helper.assertValueEqual(chooseAt(center, -3, 3.1, 0, Direction.WEST), Direction.WEST,
                "SW-ish diagonal looking west");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void chooseCloseUsesLook(GameTestHelper helper) {
        Vec3 center = new Vec3(0.5, 0.0, 0.5);
        helper.assertValueEqual(chooseAt(center, 0, 0, 0, Direction.NORTH), Direction.NORTH, "on top looking north");
        helper.assertValueEqual(chooseAt(center, 0.4, 0.2, 8, Direction.WEST), Direction.WEST,
                "almost above looking west");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void chooseFarAndMissingDefaultsEast(GameTestHelper helper) {
        Vec3 center = new Vec3(0.5, 0.0, 0.5);
        helper.assertValueEqual(chooseAt(center, 0, -70), Direction.EAST, "far north");
        helper.assertValueEqual(WaybillFacing.choose(null, null, Direction.SOUTH,
                        helper.getLevel().dimension(), center), Direction.EAST,
                "no player");
        helper.assertValueEqual(WaybillFacing.choose(net.minecraft.world.level.Level.NETHER, new Vec3(3, 64, 0),
                        Direction.EAST, helper.getLevel().dimension(), center), Direction.EAST,
                "other dimension");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void spawnSmlXlFourSidesSameFootprint(GameTestHelper helper) {
        stoneFloor(helper);
        BlockPos anchor = helper.absolutePos(ANCHOR);
        for (CargoSize size : CargoSize.values()) {
            Set<BlockPos> firstFootprint = null;
            for (Direction waybillFace : Direction.Plane.HORIZONTAL) {
                Direction facing = WaybillFacing.toContainerFacing(waybillFace);
                UUID id = UUID.randomUUID();
                CargoSpawner.Result result = CargoSpawner.spawn(helper.getLevel(), anchor, size, 1, id, false,
                        facing, true);
                helper.assertTrue(result instanceof CargoSpawner.Result.Placed,
                        size + " " + waybillFace + " placed: " + result);
                Set<BlockPos> occupied = new HashSet<>();
                for (BlockPos offset : CargoStructure.offsets(size)) {
                    BlockPos pos = anchor.offset(offset);
                    occupied.add(pos);
                    BlockState state = helper.getLevel().getBlockState(pos);
                    helper.assertTrue(state.getBlock() instanceof dev.poligorn.aerocouriers.cargo.CargoBlock,
                            size + " part at " + pos);
                    helper.assertValueEqual(state.getValue(CargoContainerBlock.FACING), facing,
                            size + " facing for sheet " + waybillFace);
                    helper.assertTrue(state.getValue(CargoContainerBlock.WAYBILL),
                            size + " waybill flag " + waybillFace);
                }
                if (firstFootprint == null) {
                    firstFootprint = occupied;
                } else {
                    helper.assertTrue(firstFootprint.equals(occupied),
                            size + " footprint must not rotate with facing (" + waybillFace + ")");
                }
                CargoSpawner.removeQuietly(helper.getLevel(), anchor);
            }
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void stampPublishesSheetTowardPlayer(GameTestHelper helper) {
        stoneFloor(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        ServerPlayer issuer = TestPlayers.player(helper, "sheet_s");
        TestPlayers.giveWaybills(issuer, 2);
        Economies.get(helper.getLevel().getServer()).deposit(issuer.getUUID(), 100, "test");
        BlockPos absChest = helper.absolutePos(CHEST);
        Vec3 center = CargoStructure.horizontalCenter(absChest, CargoSize.S);
        issuer.setPos(new Vec3(center.x, absChest.getY(), center.z + 3));
        issuer.setYRot(Direction.NORTH.toYRot());
        ContractManager.Outcome created = ContractManager.get(helper.getLevel().getServer())
                .createFromWaybill(issuer, absChest,
                        new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8),
                        20, 20, 60);
        helper.assertTrue(created.success(), "stamp: " + created.message().getString());
        Contract contract = created.contract().orElseThrow();
        helper.assertValueEqual(contract.waybillFace(), Direction.SOUTH, "stored sheet face");
        helper.assertValueEqual(contract.containerFacing(), Direction.EAST, "stored container facing");
        BlockPos origin = contract.origin().pos();
        BlockState state = helper.getLevel().getBlockState(origin);
        helper.assertTrue(state.is(ModBlocks.CARGO_CONTAINER_S.get()), "S container at pickup");
        helper.assertValueEqual(state.getValue(CargoContainerBlock.FACING), Direction.EAST, "placed facing");
        helper.assertTrue(state.getValue(CargoContainerBlock.WAYBILL), "waybill on spawn");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, timeoutTicks = 200)
    public static void lazySpawnUsesStoredFace(GameTestHelper helper) {
        stoneFloor(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        chest.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        ServerPlayer issuer = TestPlayers.player(helper, "lazy_w");
        TestPlayers.giveWaybills(issuer, 2);
        Economies.get(helper.getLevel().getServer()).deposit(issuer.getUUID(), 100, "test");
        BlockPos absChest = helper.absolutePos(CHEST);
        Vec3 center = CargoStructure.horizontalCenter(absChest, CargoSize.S);
        issuer.setPos(new Vec3(center.x - 3, absChest.getY(), center.z));
        issuer.setYRot(Direction.EAST.toYRot());
        ContractManager manager = ContractManager.get(helper.getLevel().getServer());
        ContractManager.Outcome created = manager.create(issuer, chest, absChest,
                new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8),
                20, 20, 60);
        helper.assertTrue(created.success(), "create: " + created.message().getString());
        Contract contract = created.contract().orElseThrow();
        helper.assertValueEqual(contract.waybillFace(), Direction.WEST, "lazy stored west");
        helper.assertFalse(contract.preplaced(), "command orders spawn later");
        ServerPlayer courier = TestPlayers.courier(helper, "lazy_c");
        Economies.get(helper.getLevel().getServer()).deposit(courier.getUUID(), 50, "test");
        helper.assertTrue(manager.accept(courier, contract.id()).success(), "accept");
        helper.assertTrue(manager.spawnContainer(helper.getLevel().getServer(), contract.id()).success(),
                "lazy spawn");
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    var tracked = dev.poligorn.aerocouriers.cargo.CargoTracker.get(contract.id());
                    helper.assertTrue(tracked.isPresent(), "tracker after spawn");
                    BlockState state = helper.getLevel().getBlockState(tracked.get().plotPos());
                    helper.assertTrue(state.getBlock() instanceof dev.poligorn.aerocouriers.cargo.CargoBlock,
                            "cargo in plot: " + state);
                    helper.assertValueEqual(state.getValue(CargoContainerBlock.FACING), Direction.SOUTH,
                            "lazy facing from stored west sheet");
                    helper.assertTrue(state.getValue(CargoContainerBlock.WAYBILL), "lazy waybill");
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void missingWaybillFaceFieldDefaultsEast(GameTestHelper helper) {
        stoneFloor(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIRT, 8));
        ServerPlayer issuer = TestPlayers.player(helper, "codec_wb");
        TestPlayers.giveWaybills(issuer, 1);
        Economies.get(helper.getLevel().getServer()).deposit(issuer.getUUID(), 100, "test");
        issuer.setPos(Vec3.atCenterOf(helper.absolutePos(CHEST)));
        Contract contract = ContractManager.get(helper.getLevel().getServer())
                .create(issuer, (ChestBlockEntity) helper.getBlockEntity(CHEST), helper.absolutePos(CHEST),
                        new Destination(helper.getLevel().dimension(), helper.absolutePos(TARGET), 8),
                        20, 20, 60)
                .contract().orElseThrow();
        Tag encoded = Contract.CODEC.encodeStart(NbtOps.INSTANCE, contract).getOrThrow();
        helper.assertTrue(encoded instanceof CompoundTag, "contract nbt");
        CompoundTag tag = (CompoundTag) encoded;
        tag.remove("waybill_face");
        Contract parsed = Contract.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        helper.assertValueEqual(parsed.waybillFace(), WaybillFacing.DEFAULT_FACE, "legacy save");
        helper.assertValueEqual(parsed.containerFacing(), Direction.NORTH, "legacy facing north");
        helper.succeed();
    }

    private static Direction chooseAt(Vec3 center, double dx, double dz) {
        return chooseAt(center, dx, dz, 0, Direction.SOUTH);
    }

    private static Direction chooseAt(Vec3 center, double dx, double dz, double dy, Direction look) {
        return WaybillFacing.choose(net.minecraft.world.level.Level.OVERWORLD,
                new Vec3(center.x + dx, dy, center.z + dz), look,
                net.minecraft.world.level.Level.OVERWORLD, center);
    }

    private static void stoneFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}
