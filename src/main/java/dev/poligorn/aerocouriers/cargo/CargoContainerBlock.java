package dev.poligorn.aerocouriers.cargo;

import com.mojang.serialization.MapCodec;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.registry.ModBlockEntities;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Sealed cargo container (GDD §9). Holds only a contract id in its block entity; no inventory, no GUI, no
 * item capability, unbreakable in survival. {@link #FILL} drives the Sable mass via
 * {@code data/aerocouriers/physics_block_properties/}.
 */
public class CargoContainerBlock extends BaseEntityBlock implements BlockSubLevelAssemblyListener {
    public static final int MAX_FILL = 8;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);

    private final CargoSize size;

    public CargoContainerBlock(CargoSize size, Properties properties) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    public CargoSize size() {
        return size;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(props -> new CargoContainerBlock(size, props));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoContainerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CARGO_CONTAINER.get(), CargoContainerBlockEntity::serverTick);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        // Iteration 1 checklist (GDD §16): confirm this fires when a ship is re-assembled with the container.
        AeronauticsCouriers.LOGGER.info("[cargo] Sable moved container {} -> {} ({} -> {})",
                oldPos, newPos, originLevel.dimension().location(), resultingLevel.dimension().location());
    }
}
