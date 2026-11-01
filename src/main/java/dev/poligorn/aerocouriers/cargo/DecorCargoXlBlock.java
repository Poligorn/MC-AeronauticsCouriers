package dev.poligorn.aerocouriers.cargo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Decorative XL: 2×2×3. Segment 0 is the controller. Footprint is always +X/+Z. */
public class DecorCargoXlBlock extends DecorCargoBlock {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 11);

    public DecorCargoXlBlock(Properties properties) {
        super(CargoSize.XL, properties);
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
        return pos.subtract(CargoStructure.segmentOffsetXl(state.getValue(SEGMENT)));
    }

    @Override
    public List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState) {
        List<BlockPos> positions = new ArrayList<>(12);
        for (BlockPos offset : CargoStructure.offsets(CargoSize.XL)) {
            positions.add(controller.offset(offset));
        }
        return positions;
    }
}
