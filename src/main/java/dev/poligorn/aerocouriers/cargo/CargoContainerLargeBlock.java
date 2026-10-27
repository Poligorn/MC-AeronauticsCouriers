package dev.poligorn.aerocouriers.cargo;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/** 2×2×2 cargo container (GDD §8.1). Segment 0 is the controller with the block entity. */
public class CargoContainerLargeBlock extends CargoContainerBlock {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 7);

    public CargoContainerLargeBlock(Properties properties) {
        super(CargoSize.L, properties);
        registerDefaultState(defaultBlockState().setValue(SEGMENT, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(CargoContainerLargeBlock::new);
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
        return pos.subtract(CargoStructure.segmentOffset(state.getValue(SEGMENT)));
    }

    @Override
    public List<BlockPos> structurePositions(BlockPos controller, BlockState controllerState) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (BlockPos offset : CargoStructure.offsets(CargoSize.L)) {
            positions.add(controller.offset(offset));
        }
        return positions;
    }

    @Override
    public @Nullable net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isController(state)) {
            return null;
        }
        return super.newBlockEntity(pos, state);
    }
}
