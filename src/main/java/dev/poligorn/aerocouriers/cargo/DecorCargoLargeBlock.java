package dev.poligorn.aerocouriers.cargo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Decorative L: 2×2×2. Segment 0 is the controller. Footprint is always +X/+Z. */
public class DecorCargoLargeBlock extends DecorCargoBlock {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 7);

    public DecorCargoLargeBlock(Properties properties) {
        super(CargoSize.L, properties);
        registerDefaultState(defaultBlockState().setValue(SEGMENT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SEGMENT);
    }

    @Override
    public boolean isController(BlockState state) {
        return state.getValue(SEGMENT) == 0;
    }

    @Override
    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        return pos.subtract(CargoStructure.segmentOffsetLarge(state.getValue(SEGMENT)));
    }

    @Override
    public List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (BlockPos offset : CargoStructure.offsets(CargoSize.L)) {
            positions.add(controller.offset(offset));
        }
        return positions;
    }
}
