package dev.poligorn.aerocouriers.village;

import com.mojang.serialization.MapCodec;
import dev.poligorn.aerocouriers.net.StationPackets;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code aerocouriers:post_station} (GDD §6.0), concept A «Стойка»: two halves like a door.
 * The lower half holds the block entity. The upper half is a dummy; clicks, Jade and rename
 * resolve to the controller. Model elements of the upper half reach one block above it
 * (antenna tip at 3.0 above the ground when the lower half sits on the floor).
 *
 * <p>Worlds saved before {@code half} existed load as {@code lower} (the property default).
 * {@link PostStationBlockEntity} then grows an upper half if the cell above is free, so
 * village link, reputation and orders on the existing BE stay put.
 */
public class PostStationBlock extends BaseEntityBlock {
    public static final MapCodec<PostStationBlock> CODEC = simpleCodec(PostStationBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final int LIGHT_LEVEL = 11;

    private static final VoxelShape LOWER_SHAPE = Shapes.block();
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = upperShapes();

    public PostStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    public static boolean isController(BlockState state) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    public static boolean isUpper(BlockState state) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    public static BlockPos controllerPos(BlockState state, BlockPos pos) {
        return isUpper(state) ? pos.below() : pos;
    }

    public static BlockPos otherHalfPos(BlockState state, BlockPos pos) {
        return isUpper(state) ? pos.below() : pos.above();
    }

    public static @Nullable PostStationBlockEntity stationEntity(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PostStationBlock)) {
            return null;
        }
        return level.getBlockEntity(controllerPos(state, pos)) instanceof PostStationBlockEntity station
                ? station : null;
    }

    /**
     * Used by old single-block worlds and by {@code setBlock} helpers that do not go through
     * {@link #setPlacedBy}: grow the upper half when the cell above is replaceable.
     */
    public static boolean tryPlaceUpperHalf(Level level, BlockPos lowerPos) {
        BlockState lower = level.getBlockState(lowerPos);
        if (!(lower.getBlock() instanceof PostStationBlock) || !isController(lower)) {
            return false;
        }
        BlockPos above = lowerPos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getBlock() instanceof PostStationBlock && isUpper(aboveState)) {
            return true;
        }
        if (!aboveState.canBeReplaced()) {
            return false;
        }
        return level.setBlock(above, lower.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        if (isController(state)) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!isUpper(state)) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && isController(below);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (isUpper(state) && direction == Direction.DOWN
                && !(neighborState.is(this) && isController(neighborState))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (isUpper(state)) {
            return UPPER_SHAPES.getOrDefault(state.getValue(FACING), UPPER_SHAPES.get(Direction.NORTH));
        }
        return LOWER_SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isController(state)) {
            return null;
        }
        return new PostStationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos controller = controllerPos(state, pos);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            if (serverPlayer.isShiftKeyDown()) {
                StationPackets.openAdmin(serverPlayer, serverLevel, controller);
            } else {
                StationPackets.open(serverPlayer, serverLevel, controller);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && isUpper(state)) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && isController(belowState)) {
                // Flag 35: neighbors + clients + suppress drops, same as vanilla double blocks.
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            if (isController(state) && level instanceof ServerLevel serverLevel
                    && level.getBlockEntity(pos) instanceof PostStationBlockEntity station
                    && station.stationId() != null) {
                StationAtlas.get(serverLevel.getServer()).remove(station.stationId());
            }
            BlockPos other = otherHalfPos(state, pos);
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(HALF) != state.getValue(HALF)) {
                level.removeBlock(other, false);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static Map<Direction, VoxelShape> upperShapes() {
        VoxelShape body = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.75, 1.0);
        VoxelShape eaves = Shapes.box(-1.0 / 16.0, 11.0 / 16.0, -1.0 / 16.0, 17.0 / 16.0, 13.0 / 16.0, 17.0 / 16.0);
        VoxelShape roof = Shapes.or(
                eaves,
                Shapes.box(2.0 / 16.0, 13.0 / 16.0, 2.0 / 16.0, 14.0 / 16.0, 15.0 / 16.0, 14.0 / 16.0),
                Shapes.box(5.0 / 16.0, 15.0 / 16.0, 5.0 / 16.0, 11.0 / 16.0, 17.0 / 16.0, 11.0 / 16.0));
        VoxelShape antenna = Shapes.box(7.0 / 16.0, 17.0 / 16.0, 7.0 / 16.0, 9.0 / 16.0, 32.0 / 16.0, 9.0 / 16.0);
        VoxelShape lanternNorth = Shapes.box(16.0 / 16.0, 3.0 / 16.0, 6.5 / 16.0, 19.5 / 16.0, 8.0 / 16.0, 9.5 / 16.0);
        VoxelShape north = Shapes.or(body, roof, antenna, lanternNorth);
        return Map.of(
                Direction.NORTH, north,
                Direction.EAST, rotateY(north, 1),
                Direction.SOUTH, rotateY(north, 2),
                Direction.WEST, rotateY(north, 3));
    }

    private static VoxelShape rotateY(VoxelShape shape, int quarterTurns) {
        VoxelShape[] buffer = new VoxelShape[] {shape, Shapes.empty()};
        for (int i = 0; i < quarterTurns; i++) {
            buffer[1] = Shapes.empty();
            buffer[0].forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                buffer[1] = Shapes.or(buffer[1], Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX));
            });
            buffer[0] = buffer[1];
        }
        return buffer[0];
    }
}
