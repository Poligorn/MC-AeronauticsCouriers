package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerXlBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoStructure;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CargoStructureGameTests {
    private static final BlockPos ANCHOR = new BlockPos(3, 1, 3);

    private CargoStructureGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void xlSegmentIndexRoundTrip(GameTestHelper helper) {
        for (BlockPos offset : CargoStructure.offsets(CargoSize.XL)) {
            int index = CargoStructure.segmentIndexXl(offset);
            BlockPos back = CargoStructure.segmentOffsetXl(index);
            helper.assertValueEqual(back, offset, "segment round-trip for " + offset);
        }
        helper.assertValueEqual(CargoStructure.segmentIndexXl(new BlockPos(0, 2, 0)), 8, "top layer base");
        helper.assertValueEqual(CargoStructure.segmentIndexXl(new BlockPos(1, 2, 1)), 11, "top layer corner");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void xlStructurePlacesTwelveSegments(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        BlockPos anchor = helper.absolutePos(ANCHOR);
        UUID contractId = UUID.randomUUID();
        var result = CargoSpawner.spawn(helper.getLevel(), anchor, CargoSize.XL, 4, contractId, false,
                Direction.NORTH, true);
        helper.assertTrue(result instanceof CargoSpawner.Result.Placed, "XL placed locked: " + result);
        Set<Integer> segments = new HashSet<>();
        for (BlockPos offset : CargoStructure.offsets(CargoSize.XL)) {
            BlockPos pos = anchor.offset(offset);
            helper.assertTrue(helper.getBlockState(pos).is(ModBlocks.CARGO_CONTAINER_XL.get()), "block at " + offset);
            int segment = helper.getBlockState(pos).getValue(CargoContainerXlBlock.SEGMENT);
            helper.assertTrue(segments.add(segment), "duplicate segment " + segment);
            helper.assertTrue(CargoContainerBlock.isPhysicsLocked(helper.getBlockState(pos)), "locked until accept");
        }
        helper.assertValueEqual(segments.size(), 12, "segment count");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void xlResolveAnchorFindsFootprint(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        BlockPos click = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 3), net.minecraft.world.level.block.Blocks.CHEST);
        var anchor = CargoSpawner.resolveAnchor(helper.getLevel(), click, CargoSize.XL);
        helper.assertTrue(anchor.isPresent(), "XL anchor near chest");
        helper.assertTrue(CargoStructure.isSpawnClear(helper.getLevel(), anchor.get(), CargoSize.XL),
                "resolved anchor is spawn-clear");
        helper.succeed();
    }
}
