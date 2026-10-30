package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Placement, clearance checks, and teardown for multi-block cargo containers. */
public final class CargoStructure {
    private CargoStructure() {
    }

    public static Block blockFor(CargoSize size) {
        return switch (size) {
            case S -> ModBlocks.CARGO_CONTAINER_S.get();
            case M -> ModBlocks.CARGO_CONTAINER_M.get();
            case L -> ModBlocks.CARGO_CONTAINER_L.get();
            case XL -> ModBlocks.CARGO_CONTAINER_XL.get();
        };
    }

    public static List<BlockPos> offsets(CargoSize size) {
        return switch (size) {
            case S -> List.of(BlockPos.ZERO);
            case M -> List.of(BlockPos.ZERO, BlockPos.ZERO.above());
            case L -> offsetsBox(2, 2);
            case XL -> offsetsBox(2, 3);
        };
    }

    private static List<BlockPos> offsetsBox(int xz, int height) {
        List<BlockPos> list = new ArrayList<>(xz * xz * height);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < xz; z++) {
                for (int x = 0; x < xz; x++) {
                    list.add(new BlockPos(x, y, z));
                }
            }
        }
        return list;
    }

    /** L (2×2×2): {@code x | y<<1 | z<<2} for y ∈ {0,1}. */
    public static int segmentIndexLarge(BlockPos offset) {
        return (offset.getX() & 1) | ((offset.getY() & 1) << 1) | ((offset.getZ() & 1) << 2);
    }

    public static BlockPos segmentOffsetLarge(int index) {
        return new BlockPos(index & 1, (index >> 1) & 1, (index >> 2) & 1);
    }

    /**
     * XL (2×2×3): bottom two layers use the same indices as L; y=2 uses 8..11
     * ({@code 8 + x + (z<<1)}).
     */
    public static int segmentIndexXl(BlockPos offset) {
        if (offset.getY() < 2) {
            return segmentIndexLarge(offset);
        }
        return 8 | ((offset.getX() & 1) | ((offset.getZ() & 1) << 1));
    }

    public static BlockPos segmentOffsetXl(int index) {
        if (index < 8) {
            return segmentOffsetLarge(index);
        }
        int local = index - 8;
        return new BlockPos(local & 1, 2, (local >> 1) & 1);
    }

    /**
     * Free volume around the structure: footprint plus one-block margin on every side (GDD §9.1 / §12), same idea
     * as the original 3×3×3 check for S.
     */
    public static boolean isSpawnClear(ServerLevel level, BlockPos anchor, CargoSize size) {
        if (!hasSolidBase(level, anchor, size)) {
            return false;
        }
        Footprint footprint = footprint(size);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= footprint.height; y++) {
            for (int x = -1; x <= footprint.width; x++) {
                for (int z = -1; z <= footprint.depth; z++) {
                    cursor.set(anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
                    if (y == -1) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasSolidBase(ServerLevel level, BlockPos anchor, CargoSize size) {
        Footprint footprint = footprint(size);
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int x = 0; x < footprint.width; x++) {
            for (int z = 0; z < footprint.depth; z++) {
                below.set(anchor.getX() + x, anchor.getY() - 1, anchor.getZ() + z);
                if (!level.isLoaded(below) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    private record Footprint(int width, int height, int depth) {
    }

    private static Footprint footprint(CargoSize size) {
        return switch (size) {
            case S -> new Footprint(1, 1, 1);
            case M -> new Footprint(1, 2, 1);
            case L -> new Footprint(2, 2, 2);
            case XL -> new Footprint(2, 3, 2);
        };
    }

    /**
     * Horizontal centre of the structure in world space. L/XL occupy +X/+Z from the controller regardless of
     * {@code facing}: blockstate remaps models onto the same cells, so the footprint does not rotate.
     */
    public static Vec3 horizontalCenter(BlockPos anchor, CargoSize size) {
        Footprint footprint = footprint(size);
        return new Vec3(anchor.getX() + footprint.width / 2.0, 0.0, anchor.getZ() + footprint.depth / 2.0);
    }

    public static BlockState stateForPart(CargoSize size, int fill, Direction facing, boolean waybill, boolean locked,
                                          BlockPos offset) {
        BlockState base = blockFor(size).defaultBlockState()
                .setValue(CargoContainerBlock.FILL, fill)
                .setValue(CargoContainerBlock.FACING, facing)
                .setValue(CargoContainerBlock.WAYBILL, waybill)
                .setValue(CargoContainerBlock.LOCKED, locked);
        return switch (size) {
            case S -> base;
            case M -> base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    offset.getY() == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
            case L -> base.setValue(CargoContainerLargeBlock.SEGMENT, segmentIndexLarge(offset));
            case XL -> base.setValue(CargoContainerXlBlock.SEGMENT, segmentIndexXl(offset));
        };
    }

    public static void dismantle(ServerLevel level, BlockPos controller, CargoSize size) {
        if (!(level.getBlockState(controller).getBlock() instanceof CargoBlock cargo)) {
            return;
        }
        BlockState controllerState = level.getBlockState(controller);
        if (level.getBlockEntity(controller) instanceof CargoContainerBlockEntity be) {
            be.markSelfRemoving();
        }
        List<BlockPos> positions = cargo.structurePositions(controller, controllerState);
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).getBlock() instanceof CargoBlock) {
                BlockState cleared = level.getBlockState(pos).setValue(CargoContainerBlock.WAYBILL, false);
                level.setBlock(pos, cleared, Block.UPDATE_CLIENTS);
                level.removeBlock(pos, false);
            }
        }
    }
}
