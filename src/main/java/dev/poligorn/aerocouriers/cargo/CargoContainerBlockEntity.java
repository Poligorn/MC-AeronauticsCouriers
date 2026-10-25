package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.registry.ModBlockEntities;
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
 * Sable Companion (GDD §9.3) and reports them to {@link CargoTracker}. While assembled into a Sable sub-level
 * {@link #worldPosition} is a plot coordinate, never a world coordinate.
 */
public class CargoContainerBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private @Nullable UUID contractId;
    private boolean debug;
    private int ticks;

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
            AeronauticsCouriers.LOGGER.info("[cargo] Container for contract {} loaded at {} (sub-level {})",
                    contractId, worldPosition, SableBridge.containingSubLevel(serverLevel, worldPosition));
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CargoContainerBlockEntity be) {
        if (++be.ticks % CHECK_INTERVAL_TICKS != 0 || be.contractId == null) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        Vector3d global = SableBridge.worldCenter(serverLevel, pos);
        Vector3d velocity = SableBridge.velocity(serverLevel, pos);
        CargoTracker.report(be.contractId, serverLevel, global, velocity, be.debug);

        int logInterval = CouriersServerConfig.SPEC.isLoaded() ? CouriersServerConfig.CARGO_DEBUG_LOG_INTERVAL.get() : 0;
        if (be.debug && logInterval > 0 && be.ticks % Math.max(CHECK_INTERVAL_TICKS, logInterval) == 0) {
            AeronauticsCouriers.LOGGER.info("[cargo] {} plot={} world=({}, {}, {}) v=({}, {}, {}) |v|={} b/s fill={}",
                    be.contractId, pos,
                    fmt(global.x), fmt(global.y), fmt(global.z),
                    fmt(velocity.x), fmt(velocity.y), fmt(velocity.z), fmt(velocity.length()),
                    state.getValue(CargoContainerBlock.FILL));
        }
        // Delivery self-check against ContractManager arrives with iteration 3 (GDD §9.3).
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
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
