package dev.poligorn.aerocouriers.village;

import com.mojang.serialization.MapCodec;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModProcessors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Plants the 1×1×2 stand beside the town-center bell (or on the nearest solid pad) when this village
 * still has no station. Runs while the town-center piece is placed, so it only writes cells inside
 * the current chunk clip. House-pool extras still go through {@link OncePerVillageStationProcessor}.
 */
public final class VillageTownCenterStationProcessor extends StructureProcessor {
    public static final VillageTownCenterStationProcessor INSTANCE = new VillageTownCenterStationProcessor();
    public static final MapCodec<VillageTownCenterStationProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    private VillageTownCenterStationProcessor() {
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
        return block;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level,
            BlockPos offset,
            BlockPos pos,
            List<StructureTemplate.StructureBlockInfo> original,
            List<StructureTemplate.StructureBlockInfo> processed,
            StructurePlaceSettings settings) {
        if (!CouriersServerConfig.SPEC.isLoaded() || !CouriersServerConfig.STATIONS_IN_VILLAGES.get()) {
            return processed;
        }
        if (alreadyHasController(processed)) {
            return processed;
        }
        BoundingBox box = settings.getBoundingBox();
        BlockPos stand = findStand(level, original, processed, box);
        if (stand == null) {
            return processed;
        }
        if (VillageStationDedup.isClaimed(stand)
                || VillageStationDedup.isDuplicateAtPlacement(level, stand)) {
            return processed;
        }
        Direction facing = facingTowardBell(processed, stand);
        VillageStationDedup.claim(level, stand);
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processed.size() + 2);
        for (StructureTemplate.StructureBlockInfo info : processed) {
            if (info == null || info.pos().equals(stand) || info.pos().equals(stand.above())) {
                continue;
            }
            out.add(info);
        }
        BlockState lower = ModBlocks.POST_STATION.get().defaultBlockState()
                .setValue(PostStationBlock.FACING, facing)
                .setValue(PostStationBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = lower.setValue(PostStationBlock.HALF, DoubleBlockHalf.UPPER);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", AeronauticsCouriers.id("post_station").toString());
        nbt.putBoolean("village", true);
        out.add(new StructureTemplate.StructureBlockInfo(stand, lower, nbt));
        out.add(new StructureTemplate.StructureBlockInfo(stand.above(), upper, null));
        AeronauticsCouriers.LOGGER.debug("[station] Town-center stand at {} facing {}",
                stand.toShortString(), facing.getSerializedName());
        return out;
    }

    static boolean alreadyHasController(List<StructureTemplate.StructureBlockInfo> processed) {
        for (StructureTemplate.StructureBlockInfo info : processed) {
            if (info != null && info.state().is(ModBlocks.POST_STATION.get())
                    && PostStationBlock.isController(info.state())) {
                return true;
            }
        }
        return false;
    }

    static @Nullable BlockPos findStand(LevelReader level, List<StructureTemplate.StructureBlockInfo> processed,
            @Nullable BoundingBox box) {
        return findStand(level, processed, processed, box);
    }

    static @Nullable BlockPos findStand(LevelReader level,
            List<StructureTemplate.StructureBlockInfo> original,
            List<StructureTemplate.StructureBlockInfo> processed,
            @Nullable BoundingBox box) {
        Map<BlockPos, BlockState> planned = new HashMap<>();
        boolean bellOutsideClip = false;
        for (StructureTemplate.StructureBlockInfo info : original) {
            if (info != null && info.state() != null && info.state().is(Blocks.BELL)
                    && box != null && info.pos() != null && !box.isInside(info.pos())) {
                bellOutsideClip = true;
            }
        }
        for (StructureTemplate.StructureBlockInfo info : processed) {
            if (info == null || info.pos() == null || info.state() == null) {
                continue;
            }
            if (info.state().is(Blocks.BELL) && box != null && !box.isInside(info.pos())) {
                bellOutsideClip = true;
            }
            if (box != null && !box.isInside(info.pos())) {
                continue;
            }
            planned.put(info.pos(), info.state());
        }
        List<BlockPos> bells = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
            if (entry.getValue().is(Blocks.BELL)) {
                bells.add(entry.getKey());
            }
        }
        // A 22-wide taiga meeting point spans chunks. Only the clip that contains the bell
        // may plant; other slices would stand on a street jigsaw and get overwritten.
        if (bells.isEmpty() && bellOutsideClip) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos bell : bells) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                // Bells hang 2–4 above the meeting pad; a neighbour at bell height is usually air.
                BlockPos column = bell.relative(dir);
                for (int drop = 0; drop <= 8; drop++) {
                    BlockPos stand = column.below(drop);
                    if (isValidStand(level, planned, box, stand)) {
                        candidates.add(stand);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
                if (!isStableGround(entry.getValue()) || isJigsaw(entry.getValue())) {
                    continue;
                }
                BlockPos stand = entry.getKey().above();
                if (isValidStand(level, planned, box, stand) && !nextToJigsaw(planned, stand.below())) {
                    candidates.add(stand);
                }
            }
        }
        if (candidates.isEmpty()) {
            for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
                if (!isStableGround(entry.getValue())) {
                    continue;
                }
                BlockPos stand = entry.getKey().above();
                if (isValidStand(level, planned, box, stand)) {
                    candidates.add(stand);
                }
            }
        }
        if (candidates.isEmpty()) {
            AeronauticsCouriers.LOGGER.debug("[station] Town-center planter found no stand in {}", box);
            return null;
        }
        BlockPos origin = bells.isEmpty() ? candidates.getFirst() : bells.getFirst();
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return candidates.getFirst();
    }

    private static boolean isJigsaw(BlockState state) {
        return state.is(Blocks.JIGSAW);
    }

    private static boolean nextToJigsaw(Map<BlockPos, BlockState> planned, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = planned.get(pos.relative(dir));
            if (neighbor != null && neighbor.is(Blocks.JIGSAW)) {
                return true;
            }
        }
        return false;
    }

    private static Direction facingTowardBell(List<StructureTemplate.StructureBlockInfo> processed, BlockPos stand) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (StructureTemplate.StructureBlockInfo info : processed) {
            if (info == null || !info.state().is(Blocks.BELL)) {
                continue;
            }
            double d = info.pos().distSqr(stand);
            if (d < best) {
                best = d;
                nearest = info.pos();
            }
        }
        if (nearest == null) {
            return Direction.NORTH;
        }
        Direction facing = Direction.getNearest(
                nearest.getX() - stand.getX(), 0.0, nearest.getZ() - stand.getZ());
        return facing.getAxis().isVertical() ? Direction.NORTH : facing;
    }

    private static boolean isValidStand(LevelReader level, Map<BlockPos, BlockState> planned,
            @Nullable BoundingBox box, BlockPos stand) {
        BlockPos above = stand.above();
        if (box != null && !box.isInside(stand)) {
            return false;
        }
        // Same-column upper half stays in the generating chunk; a 3-tall town center clip
        // must not reject the 1x1x2 stand that sits on its floor.
        if (box != null && !box.isInside(above)
                && (stand.getX() != above.getX() || stand.getZ() != above.getZ())) {
            return false;
        }
        BlockState lower = finalState(level, planned, stand);
        BlockState upper = finalState(level, planned, above);
        BlockState ground = finalState(level, planned, stand.below());
        return isReplaceable(lower) && isReplaceable(upper) && isStableGround(ground)
                && lower.getFluidState().isEmpty() && upper.getFluidState().isEmpty();
    }

    private static BlockState finalState(LevelReader level, Map<BlockPos, BlockState> planned, BlockPos pos) {
        BlockState plannedState = planned.get(pos);
        return plannedState != null ? plannedState : level.getBlockState(pos);
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isStableGround(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)) {
            return true;
        }
        // Dirt path / farmland / snow / mud are village floors but not a full sturdy cube.
        return state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || (state.isSolid() && !state.canBeReplaced());
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModProcessors.TOWN_CENTER_STATION.get();
    }
}
