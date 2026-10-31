package dev.poligorn.aerocouriers.village;

import com.mojang.serialization.MapCodec;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModProcessors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * During jigsaw placement, extra {@code aerocouriers:post_station} blocks of the same village become a
 * cartography table so copies never enter the saved chunk as a station.
 */
public final class OncePerVillageStationProcessor extends StructureProcessor {
    public static final OncePerVillageStationProcessor INSTANCE = new OncePerVillageStationProcessor();
    public static final MapCodec<OncePerVillageStationProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    private OncePerVillageStationProcessor() {
    }

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo process(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureTemplate.StructureBlockInfo raw,
            StructureTemplate.StructureBlockInfo block,
            StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        if (block == null || !block.state().is(ModBlocks.POST_STATION.get())) {
            return block;
        }
        // placeInChunk clips to the current chunk; do not claim a station this slice will not write.
        BoundingBox box = settings.getBoundingBox();
        if (box != null && !box.isInside(block.pos())) {
            return block;
        }
        // Dedup only the controller. The upper half of a kept station must not look like a second
        // station; the upper half of an extra house becomes air (not a second cartography table).
        if (PostStationBlock.isUpper(block.state())) {
            BlockPos below = block.pos().below();
            if (VillageStationDedup.isClaimed(below)) {
                return block;
            }
            if (VillageStationDedup.isDuplicateAtPlacement(level, below)) {
                AeronauticsCouriers.LOGGER.debug("[station] Extra village post station upper at {} -> air",
                        block.pos().toShortString());
                return new StructureTemplate.StructureBlockInfo(
                        block.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null);
            }
            return block;
        }
        if (VillageStationDedup.isDuplicateAtPlacement(level, block.pos())) {
            AeronauticsCouriers.LOGGER.debug("[station] Extra village post station at {} -> cartography table",
                    block.pos().toShortString());
            return new StructureTemplate.StructureBlockInfo(
                    block.pos(), VillageStationDedup.cartographyTable(), null);
        }
        VillageStationDedup.claim(level, block.pos());
        return block;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModProcessors.ONCE_PER_VILLAGE_STATION.get();
    }
}
