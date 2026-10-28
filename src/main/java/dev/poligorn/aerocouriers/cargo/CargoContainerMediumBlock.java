package dev.poligorn.aerocouriers.cargo;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/** 1×1×2 cargo container (GDD §8.1). Lower half holds the block entity. */
public class CargoContainerMediumBlock extends CargoContainerBlock {
    public CargoContainerMediumBlock(Properties properties) {
        super(CargoSize.M, properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(CargoContainerMediumBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    @Override
    public boolean isController(BlockState state) {
        return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Override
    public List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState) {
        return List.of(controller, controller.above());
    }

    @Override
    public @Nullable net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isController(state)) {
            return null;
        }
        return super.newBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!movedByPiston && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            BlockPos other = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            if (otherState.getBlock() == this
                    && otherState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != half) {
                level.removeBlock(other, false);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
