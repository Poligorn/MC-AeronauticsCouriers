package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.List;
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
        /** Static world block waiting for courier acceptance (no sub-level). */
        record Placed() implements Result {
        }

        record Spawned(UUID subLevelId) implements Result {
        }

        record Occupied() implements Result {
        }

        record AssemblyFailed() implements Result {
        }
    }

    /**
     * Nearest free spot with a sturdy base, within {@code radius} blocks of {@code origin}. Returns the controller
     * (bottom) anchor of the structure.
     */
    public static Optional<BlockPos> findSpot(ServerLevel level, BlockPos origin, int radius, CargoSize size) {
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
                    if (CargoStructure.isSpawnClear(level, cursor, size)) {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Result spawn(ServerLevel level, BlockPos anchor, CargoSize size, int fill, UUID contractId,
                               boolean debug, Direction facing) {
        return spawn(level, anchor, size, fill, contractId, debug, facing, false);
    }

    /**
     * @param lockedUntilAccept when {@code true}, places a static block (OPEN orders) instead of assembling Sable
     */
    public static Result spawn(ServerLevel level, BlockPos anchor, CargoSize size, int fill, UUID contractId,
                               boolean debug, Direction facing, boolean lockedUntilAccept) {
        if (!CargoStructure.isSpawnClear(level, anchor, size)) {
            return new Result.Occupied();
        }
        int clampedFill = Math.clamp(fill, 0, CargoContainerBlock.MAX_FILL);
        boolean waybill = contractId != null;
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos offset : CargoStructure.offsets(size)) {
            BlockPos pos = anchor.offset(offset);
            if (!level.getBlockState(pos).canBeReplaced()) {
                rollback(level, placed);
                return new Result.Occupied();
            }
            BlockState state = CargoStructure.stateForPart(size, clampedFill, facing, waybill, lockedUntilAccept, offset);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            placed.add(pos);
        }
        if (!(level.getBlockEntity(anchor) instanceof CargoContainerBlockEntity be)) {
            rollback(level, placed);
            return new Result.AssemblyFailed();
        }
        be.bind(contractId, debug);

        if (lockedUntilAccept) {
            return new Result.Placed();
        }

        Optional<UUID> subLevel = SableBridge.assembleStructure(level, anchor, placed);
        if (subLevel.isEmpty()) {
            rollback(level, placed);
            return new Result.AssemblyFailed();
        }
        return new Result.Spawned(subLevel.get());
    }

    /**
     * Turns a locked preplaced structure at {@code anyPart} into a Sable object (courier accepted the order).
     */
    public static Optional<UUID> unlockAndAssemble(ServerLevel level, BlockPos anyPart) {
        BlockState state = level.getBlockState(anyPart);
        if (!(state.getBlock() instanceof CargoBlock cargo)) {
            return Optional.empty();
        }
        BlockPos controller = cargo.controllerPos(anyPart, state);
        BlockState controllerState = level.getBlockState(controller);
        List<BlockPos> blocks = cargo.structurePositions(controller, controllerState);
        for (BlockPos pos : blocks) {
            BlockState part = level.getBlockState(pos);
            if (!(part.getBlock() instanceof CargoBlock)) {
                return Optional.empty();
            }
            if (CargoContainerBlock.isPhysicsLocked(part)) {
                level.setBlock(pos, part.setValue(CargoContainerBlock.LOCKED, false), Block.UPDATE_ALL);
            }
        }
        Optional<UUID> subLevel = SableBridge.assembleStructure(level, controller, blocks);
        if (subLevel.isEmpty()) {
            AeronauticsCouriers.LOGGER.warn("[cargo] Sable did not assemble unlocked container at {}", controller);
        }
        return subLevel;
    }

    /** Removes a cargo structure without treating it as a vanished in-transit container. */
    public static boolean removeQuietly(ServerLevel level, BlockPos anyPart) {
        BlockState state = level.getBlockState(anyPart);
        if (!(state.getBlock() instanceof CargoBlock cargo)) {
            return false;
        }
        BlockPos controller = cargo.controllerPos(anyPart, state);
        CargoSize size = cargo.size();
        if (level.getBlockEntity(controller) instanceof CargoContainerBlockEntity be) {
            be.markSelfRemoving();
        }
        CargoStructure.dismantle(level, controller, size);
        return true;
    }

    private static void rollback(ServerLevel level, List<BlockPos> placed) {
        for (BlockPos pos : placed) {
            level.removeBlock(pos, false);
        }
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
