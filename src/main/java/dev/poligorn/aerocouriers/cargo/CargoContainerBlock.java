package dev.poligorn.aerocouriers.cargo;

import com.mojang.serialization.MapCodec;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.registry.ModBlockEntities;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Sealed cargo container (GDD §9). Holds only a contract id in its block entity; no inventory, no GUI, no
 * item capability, drops nothing. {@link #FILL} drives the Sable mass via
 * {@code data/aerocouriers/physics_block_properties/}.
 *
 * <p>Hardness matches obsidian ({@code 50}) with diamond-pick mining tags; blast resistance stays low (~stone) so
 * TNT and cannons can destroy it (GDD §9.7). With piracy disabled it is unbreakable and explosion-proof.
 * Contract consequences of damage are in {@link CargoEvents}.
 */
public class CargoContainerBlock extends BaseEntityBlock implements BlockSubLevelAssemblyListener, CargoBlock {
    public static final int MAX_FILL = 8;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);
    /** Door face; matches replaced chests and vanilla directional blocks. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Paper waybill on {@code facing + 90°} clockwise while a contract is bound (GDD §9.1). */
    public static final BooleanProperty WAYBILL = BooleanProperty.create("waybill");
    /** While {@code true}, the container stays a world block (no Sable assembly) until a courier accepts. */
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public static boolean isPhysicsLocked(BlockState state) {
        return state.hasProperty(LOCKED) && state.getValue(LOCKED);
    }

    private final CargoSize size;

    public CargoContainerBlock(CargoSize size, Properties properties) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any()
                .setValue(FILL, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(WAYBILL, false)
                .setValue(LOCKED, false));
    }

    public CargoSize size() {
        return size;
    }

    @Override
    public boolean isController(BlockState state) {
        return true;
    }

    @Override
    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        return pos;
    }

    @Override
    public List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState) {
        return List.of(controller);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(props -> new CargoContainerBlock(size, props));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL, FACING, WAYBILL, LOCKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(FILL, 0);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (isPhysicsLocked(state) && !player.getAbilities().instabuild) {
            return 0.0F;
        }
        if (CouriersServerConfig.SPEC.isLoaded() && !CouriersServerConfig.PIRACY_ENABLED.get()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        if (isPhysicsLocked(state)) {
            return 3_600_000.0F;
        }
        if (!CouriersServerConfig.SPEC.isLoaded() || !CouriersServerConfig.PIRACY_ENABLED.get()) {
            return 3_600_000.0F;
        }
        return CouriersServerConfig.PIRACY_BLAST_RESISTANCE.get().floatValue();
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
        if (!isController(newState)) {
            return;
        }
        // Iteration 1 checklist (GDD §16): confirm this fires when a ship is re-assembled with the container.
        AeronauticsCouriers.LOGGER.info("[cargo] Sable moved container {} -> {} ({} -> {})",
                oldPos, newPos, originLevel.dimension().location(), resultingLevel.dimension().location());
        if (resultingLevel.getBlockEntity(newPos) instanceof CargoContainerBlockEntity be
                && be.contractId() != null) {
            Vector3d world = SableBridge.worldCenter(resultingLevel, newPos);
            Vector3d velocity = SableBridge.velocity(resultingLevel, newPos);
            CargoTracker.report(be.contractId(), resultingLevel, newPos, world, velocity, be.isDebug());
        }
    }
}
