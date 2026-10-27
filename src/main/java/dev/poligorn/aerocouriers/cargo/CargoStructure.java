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

/** Placement, clearance checks, and teardown for multi-block cargo containers. */
public final class CargoStructure {
    private CargoStructure() {
    }

    public static Block blockFor(CargoSize size) {
        return switch (size) {
            case S -> ModBlocks.CARGO_CONTAINER_S.get();
            case M -> ModBlocks.CARGO_CONTAINER_M.get();
            case L -> ModBlocks.CARGO_CONTAINER_L.get();
        };
    }

    public static List<BlockPos> offsets(CargoSize size) {
        return switch (size) {
            case S -> List.of(BlockPos.ZERO);
            case M -> List.of(BlockPos.ZERO, BlockPos.ZERO.above());
            case L -> {
                List<BlockPos> list = new ArrayList<>(8);
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        for (int x = 0; x < 2; x++) {
                            list.add(new BlockPos(x, y, z));
                        }
                    }
                }
                yield list;
            }
        };
    }

    public static int segmentIndex(BlockPos offset) {
        return (offset.getX() & 1) | ((offset.getY() & 1) << 1) | ((offset.getZ() & 1) << 2);
    }

    public static BlockPos segmentOffset(int index) {
        return new BlockPos(index & 1, (index >> 1) & 1, (index >> 2) & 1);
    }

    /**
     * Free volume around the structure: footprint plus one-block margin on every side (GDD §9.1 / §12), same idea
     * as the original 3×3×3 check for S.
     */
    public static boolean isSpawnClear(ServerLevel level, BlockPos anchor, CargoSize size) {
        if (!hasSolidBase(level, anchor, size)) {
            return false;
        }
        int width = size == CargoSize.L ? 2 : 1;
        int height = size == CargoSize.S ? 1 : 2;
        int depth = size == CargoSize.L ? 2 : 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= height; y++) {
            for (int x = -1; x <= width; x++) {
                for (int z = -1; z <= depth; z++) {
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
        int width = size == CargoSize.L ? 2 : 1;
        int depth = size == CargoSize.L ? 2 : 1;
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                below.set(anchor.getX() + x, anchor.getY() - 1, anchor.getZ() + z);
                if (!level.isLoaded(below) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static BlockState stateForPart(CargoSize size, int fill, Direction facing, boolean waybill, BlockPos offset) {
        BlockState base = blockFor(size).defaultBlockState()
                .setValue(CargoContainerBlock.FILL, fill)
                .setValue(CargoContainerBlock.FACING, facing)
                .setValue(CargoContainerBlock.WAYBILL, waybill);
        return switch (size) {
            case S -> base;
            case M -> base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    offset.getY() == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
            case L -> base.setValue(CargoContainerLargeBlock.SEGMENT, segmentIndex(offset));
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
