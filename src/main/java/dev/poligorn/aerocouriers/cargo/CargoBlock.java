package dev.poligorn.aerocouriers.cargo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Shared behaviour for S/M/L/XL cargo container blocks (controller resolution, footprint). */
public interface CargoBlock {
    CargoSize size();

    boolean isController(BlockState state);

    BlockPos controllerPos(BlockPos pos, BlockState state);

    /** Block positions that belong to this structure when {@code controller} is the controller block. */
    List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState);
}
