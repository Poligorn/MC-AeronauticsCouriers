package dev.poligorn.aerocouriers.cargo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Decorative M: 1×1×2. Lower half is the controller. */
public class DecorCargoMediumBlock extends DecorCargoBlock {
    public DecorCargoMediumBlock(Properties properties) {
        super(CargoSize.M, properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
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
}
