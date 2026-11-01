package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoStructure;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.cargo.DecorCargoLargeBlock;
import dev.poligorn.aerocouriers.cargo.DecorCargoXlBlock;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DecorCargoGameTests {
    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private DecorCargoGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void decorPlacesWholeStructure(GameTestHelper helper) {
        stoneFloor(helper);
        helper.assertTrue(placeDecor(helper, CargoSize.S, ANCHOR, Direction.NORTH), "S placed");
        helper.assertTrue(helper.getBlockState(ANCHOR).is(ModBlocks.DECOR_CARGO_CONTAINER_S.get()), "S block");
        helper.assertTrue(noBlockEntity(helper, ANCHOR), "S has no BE");
        helper.assertFalse(helper.getBlockState(ANCHOR).getBlock() instanceof CargoBlock, "S is not CargoBlock");

        helper.setBlock(ANCHOR, Blocks.AIR);
        helper.assertTrue(placeDecor(helper, CargoSize.M, ANCHOR, Direction.SOUTH), "M placed");
        helper.assertTrue(helper.getBlockState(ANCHOR).is(ModBlocks.DECOR_CARGO_CONTAINER_M.get()), "M lower");
        helper.assertValueEqual(helper.getBlockState(ANCHOR).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF),
                DoubleBlockHalf.LOWER, "M controller");
        helper.assertTrue(helper.getBlockState(ANCHOR.above()).is(ModBlocks.DECOR_CARGO_CONTAINER_M.get()), "M upper");
        helper.assertTrue(noBlockEntity(helper, ANCHOR), "M has no BE");

        helper.setBlock(ANCHOR, Blocks.AIR);
        helper.assertTrue(placeDecor(helper, CargoSize.L, ANCHOR, Direction.WEST), "L placed");
        Set<Integer> segments = new HashSet<>();
        for (BlockPos offset : CargoStructure.offsets(CargoSize.L)) {
            BlockState state = helper.getBlockState(ANCHOR.offset(offset));
            helper.assertTrue(state.is(ModBlocks.DECOR_CARGO_CONTAINER_L.get()), "L at " + offset);
            helper.assertTrue(segments.add(state.getValue(DecorCargoLargeBlock.SEGMENT)), "L unique segment");
        }
        helper.assertValueEqual(segments.size(), 8, "L 8 segments");

        helper.setBlock(ANCHOR, Blocks.AIR);
        helper.assertTrue(placeDecor(helper, CargoSize.XL, ANCHOR, Direction.EAST), "XL placed");
        segments.clear();
        for (BlockPos offset : CargoStructure.offsets(CargoSize.XL)) {
            BlockState state = helper.getBlockState(ANCHOR.offset(offset));
            helper.assertTrue(state.is(ModBlocks.DECOR_CARGO_CONTAINER_XL.get()), "XL at " + offset);
            helper.assertTrue(segments.add(state.getValue(DecorCargoXlBlock.SEGMENT)), "XL unique segment");
        }
        helper.assertValueEqual(segments.size(), 12, "XL 12 segments");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void decorRefusesOccupiedCell(GameTestHelper helper) {
        stoneFloor(helper);
        helper.setBlock(ANCHOR.offset(1, 0, 0), Blocks.STONE);
        helper.assertFalse(placeDecor(helper, CargoSize.L, ANCHOR, Direction.NORTH), "L blocked by +X");
        helper.assertTrue(helper.getBlockState(ANCHOR).isAir(), "nothing placed");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void decorCreativeBreakRemovesWhole(GameTestHelper helper) {
        stoneFloor(helper);
        helper.assertTrue(placeDecor(helper, CargoSize.XL, ANCHOR, Direction.NORTH), "XL placed");
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(ANCHOR.offset(1, 2, 1));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "decor_break"));
        player.setGameMode(GameType.CREATIVE);
        BlockState state = level.getBlockState(abs);
        state.getBlock().playerWillDestroy(level, abs, state, player);
        level.removeBlock(abs, false);
        for (BlockPos offset : CargoStructure.offsets(CargoSize.XL)) {
            helper.assertTrue(helper.getBlockState(ANCHOR.offset(offset)).isAir(), "cleared " + offset);
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void decorIgnoredByCargoTracker(GameTestHelper helper) {
        stoneFloor(helper);
        int before = CargoTracker.all().size();
        helper.assertTrue(placeDecor(helper, CargoSize.M, ANCHOR, Direction.NORTH), "M placed");
        helper.assertValueEqual(CargoTracker.all().size(), before, "tracker unchanged");
        helper.assertFalse(helper.getBlockState(ANCHOR).getBlock() instanceof CargoBlock, "not CargoBlock");
        helper.assertTrue(noBlockEntity(helper, ANCHOR), "no BE");
        ServerPlayer survival = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "surv"));
        survival.setGameMode(GameType.SURVIVAL);
        float progress = helper.getBlockState(ANCHOR).getDestroyProgress(survival, helper.getLevel(),
                helper.absolutePos(ANCHOR));
        helper.assertTrue(progress == 0.0F, "survival cannot break");
        helper.succeed();
    }

    private static boolean noBlockEntity(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative)) == null;
    }

    private static void stoneFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static boolean placeDecor(GameTestHelper helper, CargoSize size, BlockPos relative, Direction look) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relative);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "decor_" + size));
        player.setYRot(yRot(look));
        player.setPos(Vec3.atCenterOf(abs.relative(look, 2)));
        ItemStack stack = new ItemStack(itemFor(size));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.51, 0),
                Direction.UP, abs.below(), false);
        BlockPlaceContext ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        return stack.useOn(ctx).consumesAction();
    }

    private static net.minecraft.world.item.Item itemFor(CargoSize size) {
        return switch (size) {
            case S -> ModItems.DECOR_CARGO_CONTAINER_S.get();
            case M -> ModItems.DECOR_CARGO_CONTAINER_M.get();
            case L -> ModItems.DECOR_CARGO_CONTAINER_L.get();
            case XL -> ModItems.DECOR_CARGO_CONTAINER_XL.get();
        };
    }

    private static float yRot(Direction look) {
        return switch (look) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
