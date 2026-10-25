package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.registry.ModBlockEntities;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Holds only the contract id (GDD §9.2). Once per second it resolves its real world position and velocity through
 * Sable Companion and asks {@link ContractManager#checkContainer} whether it was delivered (GDD §9.3); a container
 * whose contract is unknown or no longer in transit removes itself. While assembled into a Sable sub-level
 * {@link #worldPosition} is a plot coordinate, never a world coordinate.
 */
public class CargoContainerBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private @Nullable UUID contractId;
    private boolean debug;
    private int ticks;
    private boolean chunkUnloading;
    private boolean selfRemoving;

    public CargoContainerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARGO_CONTAINER.get(), pos, state);
    }

    /** Must be called before Sable assembly so the id moves together with the block entity NBT. */
    public void bind(UUID contractId, boolean debug) {
        this.contractId = contractId;
        this.debug = debug;
        setChanged();
    }

    public @Nullable UUID contractId() {
        return contractId;
    }

    public boolean isDebug() {
        return debug;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && contractId != null) {
            AeronauticsCouriers.LOGGER.debug("[cargo] Container for contract {} loaded at {} (sub-level {})",
                    contractId, worldPosition, SableBridge.containingSubLevel(serverLevel, worldPosition));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    /**
     * Sable re-creates the block entity when it moves blocks (assembly, ship re-assembly), so a removal is only
     * reported; the manager fails the contract if no container with this id reports again shortly (GDD §12).
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!chunkUnloading && !selfRemoving && !debug && contractId != null && level instanceof ServerLevel serverLevel) {
            ContractManager.get(serverLevel.getServer()).containerRemoved(contractId, serverLevel.getGameTime());
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CargoContainerBlockEntity be) {
        if (++be.ticks % CHECK_INTERVAL_TICKS != 0 || be.contractId == null) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        Vector3d world = SableBridge.worldCenter(serverLevel, pos);
        Vector3d velocity = SableBridge.velocity(serverLevel, pos);
        CargoTracker.report(be.contractId, serverLevel, pos, world, velocity, be.debug);

        if (be.debug) {
            int logInterval = CouriersServerConfig.SPEC.isLoaded() ? CouriersServerConfig.CARGO_DEBUG_LOG_INTERVAL.get() : 0;
            if (logInterval > 0 && be.ticks % Math.max(CHECK_INTERVAL_TICKS, logInterval) == 0) {
                AeronauticsCouriers.LOGGER.info("[cargo] {} plot={} world=({}, {}, {}) v=({}, {}, {}) |v|={} b/s fill={}",
                        be.contractId, pos,
                        fmt(world.x), fmt(world.y), fmt(world.z),
                        fmt(velocity.x), fmt(velocity.y), fmt(velocity.z), fmt(velocity.length()),
                        state.getValue(CargoContainerBlock.FILL));
            }
            return;
        }

        ContractManager.Verdict verdict = ContractManager.get(serverLevel.getServer())
                .checkContainer(serverLevel, be.contractId, world, velocity.length());
        if (verdict == ContractManager.Verdict.REMOVE) {
            be.selfRemoving = true;
            CargoTracker.forget(be.contractId);
            level.removeBlock(pos, false);
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (contractId != null) {
            tag.putUUID("contract_id", contractId);
        }
        if (debug) {
            tag.putBoolean("debug", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contractId = tag.hasUUID("contract_id") ? tag.getUUID("contract_id") : null;
        debug = tag.getBoolean("debug");
    }
}
