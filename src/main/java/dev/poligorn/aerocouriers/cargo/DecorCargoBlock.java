package dev.poligorn.aerocouriers.cargo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Decorative container: same footprint and models as cargo, no block entity, not a {@link CargoBlock}.
 * Survival treats it as a LOCKED cargo crate (unbreakable, blast-proof). Creative breaks the whole structure.
 */
public class DecorCargoBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);

    private final CargoSize size;

    public DecorCargoBlock(CargoSize size, Properties properties) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public CargoSize size() {
        return size;
    }

    public boolean isController(BlockState state) {
        return true;
    }

    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        return pos;
    }

    public List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState) {
        return List.of(controller);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!canPlaceWhole(context.getLevel(), context.getClickedPos(), size, context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        if (!isController(state)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (BlockPos offset : CargoStructure.offsets(size)) {
            if (offset.equals(BlockPos.ZERO)) {
                continue;
            }
            level.setBlock(pos.offset(offset), stateForPart(size, facing, offset), Block.UPDATE_ALL);
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.getAbilities().instabuild) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return 3_600_000.0F;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            dismantleOthers(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!DISMANTLING.get() && !movedByPiston && !state.is(newState.getBlock())) {
            dismantleOthers(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    public static boolean canPlaceWhole(LevelReader level, BlockPos origin, CargoSize size,
            @Nullable BlockPlaceContext context) {
        for (BlockPos offset : CargoStructure.offsets(size)) {
            BlockPos pos = origin.offset(offset);
            if (level instanceof Level world && pos.getY() >= world.getMaxBuildHeight()) {
                return false;
            }
            BlockState existing = level.getBlockState(pos);
            if (context != null && pos.equals(context.getClickedPos())) {
                if (!existing.canBeReplaced(context)) {
                    return false;
                }
                continue;
            }
            if (context != null) {
                BlockPlaceContext at = BlockPlaceContext.at(context, pos, context.getClickedFace());
                if (!existing.canBeReplaced(at)) {
                    return false;
                }
            } else if (!existing.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    public static BlockState stateForPart(CargoSize size, Direction facing, BlockPos offset) {
        BlockState base = blockFor(size).defaultBlockState().setValue(FACING, facing);
        return switch (size) {
            case S -> base;
            case M -> base.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                    offset.getY() == 0
                            ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
            case L -> base.setValue(DecorCargoLargeBlock.SEGMENT, CargoStructure.segmentIndexLarge(offset));
            case XL -> base.setValue(DecorCargoXlBlock.SEGMENT, CargoStructure.segmentIndexXl(offset));
        };
    }

    public static DecorCargoBlock blockFor(CargoSize size) {
        return switch (size) {
            case S -> dev.poligorn.aerocouriers.registry.ModBlocks.DECOR_CARGO_CONTAINER_S.get();
            case M -> dev.poligorn.aerocouriers.registry.ModBlocks.DECOR_CARGO_CONTAINER_M.get();
            case L -> dev.poligorn.aerocouriers.registry.ModBlocks.DECOR_CARGO_CONTAINER_L.get();
            case XL -> dev.poligorn.aerocouriers.registry.ModBlocks.DECOR_CARGO_CONTAINER_XL.get();
        };
    }

    void dismantleOthers(Level level, BlockPos pos, BlockState state) {
        if (DISMANTLING.get()) {
            return;
        }
        BlockPos controller = controllerPos(pos, state);
        DISMANTLING.set(true);
        try {
            for (BlockPos part : structurePositions(controller, state)) {
                if (part.equals(pos)) {
                    continue;
                }
                if (level.getBlockState(part).getBlock() instanceof DecorCargoBlock) {
                    level.removeBlock(part, false);
                }
            }
        } finally {
            DISMANTLING.set(false);
        }
    }
}
