package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block(s) that held the cargo when a player order was created (stamp or board). Restored at delivery instead of
 * mailing the cargo.
 */
public record OriginContainerSnapshot(List<Part> parts) {
    public static final Codec<OriginContainerSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Part.CODEC.listOf().fieldOf("parts").forGetter(OriginContainerSnapshot::parts)
    ).apply(instance, OriginContainerSnapshot::new));

    public record Part(BlockPos offset, BlockState state, Optional<CompoundTag> blockEntity) {
        static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Part::offset),
                BlockState.CODEC.fieldOf("state").forGetter(Part::state),
                CompoundTag.CODEC.optionalFieldOf("block_entity").forGetter(Part::blockEntity)
        ).apply(instance, Part::new));
    }

    public static Optional<OriginContainerSnapshot> capture(ServerLevel level, CargoContainers.Access access) {
        if (access.positions().isEmpty()) {
            return Optional.empty();
        }
        BlockPos anchor = access.positions().getFirst();
        HolderLookup.Provider registries = level.registryAccess();
        List<Part> parts = new ArrayList<>();
        for (BlockPos pos : access.positions()) {
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            Optional<CompoundTag> beTag = Optional.empty();
            if (be != null) {
                beTag = Optional.of(be.saveWithFullMetadata(registries));
            }
            parts.add(new Part(pos.subtract(anchor), state, beTag));
        }
        return Optional.of(new OriginContainerSnapshot(List.copyOf(parts)));
    }

    /** Captures a single block (e.g. programmatic {@link ContractManager#create} from a chest BE). */
    public static Optional<OriginContainerSnapshot> capture(ServerLevel level, BlockPos pos) {
        return CargoContainers.find(level, pos).flatMap(access -> capture(level, access));
    }

    public boolean fitsAt(ServerLevel level, BlockPos anchor) {
        for (Part part : parts) {
            BlockPos at = anchor.offset(part.offset());
            if (!level.isLoaded(at)) {
                return false;
            }
            BlockState there = level.getBlockState(at);
            if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public BlockPos primaryOffset() {
        return parts.isEmpty() ? BlockPos.ZERO : parts.getFirst().offset();
    }

    /** Footprint used when searching for a spot on contracts saved before snapshots existed. */
    public static OriginContainerSnapshot fallbackShape() {
        return new OriginContainerSnapshot(List.of(
                new Part(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), Optional.empty())));
    }
}
