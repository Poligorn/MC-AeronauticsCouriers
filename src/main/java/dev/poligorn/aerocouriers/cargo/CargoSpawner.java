package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a container and turns it into a Sable physics object (GDD §9.1). The contract id is written into the
 * block entity before assembly so Sable carries it into the sub-level plot.
 */
public final class CargoSpawner {
    private CargoSpawner() {
    }

    public sealed interface Result {
        record Spawned(UUID subLevelId) implements Result {
        }

        record Occupied() implements Result {
        }

        record AssemblyFailed() implements Result {
        }
    }

    /**
     * Nearest free 3×3×3 spot with a sturdy block under its centre, within {@code radius} blocks horizontally and
     * a few blocks vertically of {@code origin} (GDD §7.3, §12). Returns the bottom-centre block of the spot.
     */
    public static Optional<BlockPos> findSpot(ServerLevel level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distance = dx * dx + dz * dz + dy * dy;
                    if (distance >= bestDistance) {
                        continue;
                    }
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (isFreeSpot(level, cursor)) {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isFreeSpot(ServerLevel level, BlockPos bottomCentre) {
        BlockPos below = bottomCentre.below();
        if (!level.isLoaded(below) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(bottomCentre.offset(-1, 0, -1), bottomCentre.offset(1, 2, 1))) {
            BlockState state = level.getBlockState(pos);
            if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Places a container on air. For stamp orders the source block must already have been removed without drops.
     */
    public static Result spawn(ServerLevel level, BlockPos pos, int fill, UUID contractId, boolean debug,
                               Direction facing) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return new Result.Occupied();
        }
        BlockState state = ModBlocks.CARGO_CONTAINER_S.get().defaultBlockState()
                .setValue(CargoContainerBlock.FILL, Math.clamp(fill, 0, CargoContainerBlock.MAX_FILL))
                .setValue(CargoContainerBlock.FACING, facing)
                .setValue(CargoContainerBlock.WAYBILL, contractId != null);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be)) {
            level.removeBlock(pos, false);
            return new Result.AssemblyFailed();
        }
        be.bind(contractId, debug);

        Optional<UUID> subLevel = SableBridge.assembleSingleBlock(level, pos);
        if (subLevel.isEmpty()) {
            AeronauticsCouriers.LOGGER.warn("[cargo] Sable assembled no mass for container {} at {}", contractId, pos);
            return new Result.AssemblyFailed();
        }
        AeronauticsCouriers.LOGGER.info("[cargo] Spawned container {} (fill {}) at {} as sub-level {}",
                contractId, fill, pos, subLevel.get());
        return new Result.Spawned(subLevel.get());
    }

    /** Removes a cargo block without treating it as a vanished in-transit container. */
    public static boolean removeQuietly(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be)) {
            return false;
        }
        be.markSelfRemoving();
        BlockState cleared = level.getBlockState(pos).setValue(CargoContainerBlock.WAYBILL, false);
        level.setBlock(pos, cleared, Block.UPDATE_CLIENTS);
        return level.removeBlock(pos, false);
    }

    /** Reads {@link HorizontalDirectionalBlock#FACING} from a block about to be replaced (e.g. chest). */
    public static Direction facingFromReplacedBlock(ServerLevel level, BlockPos pos, Direction fallback) {
        BlockState replaced = level.getBlockState(pos);
        if (replaced.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return replaced.getValue(HorizontalDirectionalBlock.FACING);
        }
        return fallback;
    }
}
