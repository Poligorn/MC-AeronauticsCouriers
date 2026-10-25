package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
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

    public static Result spawn(ServerLevel level, BlockPos pos, int fill, UUID contractId, boolean debug) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return new Result.Occupied();
        }
        BlockState state = ModBlocks.CARGO_CONTAINER_S.get().defaultBlockState()
                .setValue(CargoContainerBlock.FILL, Math.clamp(fill, 0, CargoContainerBlock.MAX_FILL));
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
}
