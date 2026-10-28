package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/** Places delivered P2P cargo back into the original container block(s), or a plain chest/barrel fallback. */
public final class DeliveryPlacement {
    private static final int SEARCH_RADIUS = 3;

    private DeliveryPlacement() {
    }

    public enum Result {
        PLACED,
        MAILED
    }

    public record Outcome(Result result, Optional<BlockPos> anchor) {
    }

    /**
     * Removes the physics cargo container if it is still in the world, then restores cargo at {@code near} (snapped to
     * block grid). Falls back to the mailbox only when no spot can be found.
     */
    public static Outcome restorePlayerDelivery(MinecraftServer server, Contract contract, Vec3 near,
                                                List<ItemStack> cargo) {
        removeInTransitContainer(server, contract);
        ServerLevel level = server.getLevel(contract.destination().dimension());
        if (level == null || cargo.isEmpty()) {
            return new Outcome(Result.PLACED, Optional.empty());
        }
        BlockPos preferred = BlockPos.containing(near.x, near.y, near.z);
        Optional<OriginContainerSnapshot> snapshot = contract.originSnapshot();
        Optional<BlockPos> anchor = findAnchor(level, preferred, snapshot);
        if (anchor.isEmpty()) {
            Mailbox.get(server).post(contract.recipient(), cargo);
            return new Outcome(Result.MAILED, Optional.empty());
        }
        if (snapshot.isPresent()) {
            placeSnapshot(level, anchor.get(), snapshot.get(), cargo);
        } else {
            placeFallback(level, anchor.get(), cargo);
        }
        return new Outcome(Result.PLACED, anchor);
    }

    public static void consumeNpcDelivery(MinecraftServer server, Contract contract) {
        removeInTransitContainer(server, contract);
    }

    private static void removeInTransitContainer(MinecraftServer server, Contract contract) {
        UUID id = contract.id();
        CargoTracker.get(id).ifPresent(snapshot -> {
            ServerLevel level = server.getLevel(snapshot.dimension());
            if (level != null) {
                CargoSpawner.removeQuietly(level, snapshot.plotPos());
            }
        });
        ServerLevel origin = server.getLevel(contract.origin().dimension());
        if (origin != null && origin.isLoaded(contract.origin().pos())
                && origin.getBlockEntity(contract.origin().pos()) instanceof dev.poligorn.aerocouriers.cargo.CargoContainerBlockEntity be
                && id.equals(be.contractId())) {
            CargoSpawner.removeQuietly(origin, contract.origin().pos());
        }
        CargoTracker.forget(id);
    }

    private static Optional<BlockPos> findAnchor(ServerLevel level, BlockPos preferred,
                                                 Optional<OriginContainerSnapshot> snapshot) {
        OriginContainerSnapshot shape = snapshot.orElse(OriginContainerSnapshot.fallbackShape());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    cursor.set(preferred.getX() + dx, preferred.getY() + dy, preferred.getZ() + dz);
                    if (!shape.fitsAt(level, cursor)) {
                        continue;
                    }
                    double distance = cursor.distSqr(preferred);
                    if (distance < bestDistance) {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static void placeSnapshot(ServerLevel level, BlockPos anchor, OriginContainerSnapshot snapshot,
                                      List<ItemStack> cargo) {
        HolderLookup.Provider registries = level.registryAccess();
        List<ItemStack> remaining = new ArrayList<>(cargo);
        Container primaryInventory = null;
        for (OriginContainerSnapshot.Part part : snapshot.parts()) {
            BlockPos at = anchor.offset(part.offset());
            BlockState state = part.state();
            level.setBlock(at, state, Block.UPDATE_ALL);
            part.blockEntity().ifPresent(tag -> {
                BlockEntity be = BlockEntity.loadStatic(at, state, tag, registries);
                if (be != null) {
                    level.setBlockEntity(be);
                }
            });
            BlockEntity be = level.getBlockEntity(at);
            if (be instanceof Container container) {
                clearContainer(container);
                if (primaryInventory == null) {
                    primaryInventory = container;
                }
            }
        }
        if (primaryInventory != null) {
            spillInto(primaryInventory, remaining);
        }
        spillExtras(level, anchor, remaining.stream().filter(s -> !s.isEmpty()).toList());
    }

    private static void placeFallback(ServerLevel level, BlockPos anchor, List<ItemStack> cargo) {
        Direction facing = Direction.NORTH;
        BlockState chest = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE);
        if (!level.getBlockState(anchor).canBeReplaced()) {
            anchor = anchor.above();
        }
        level.setBlock(anchor, chest, Block.UPDATE_ALL);
        BlockEntity be = level.getBlockEntity(anchor);
        if (be instanceof ChestBlockEntity chestBe) {
            clearContainer(chestBe);
            List<ItemStack> stacks = new ArrayList<>(cargo);
            spillInto(chestBe, stacks);
            spillExtras(level, anchor, stacks.stream().filter(s -> !s.isEmpty()).toList());
        } else {
            for (ItemStack stack : cargo) {
                Containers.dropItemStack(level, anchor.getX(), anchor.getY(), anchor.getZ(), stack);
            }
        }
    }

    private static void spillExtras(ServerLevel level, BlockPos anchor, List<ItemStack> remaining) {
        List<ItemStack> left = new ArrayList<>(remaining);
        if (left.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }
        BlockPos extra = anchor.east();
        if (level.getBlockState(extra).canBeReplaced()) {
            BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
            level.setBlock(extra, barrel, Block.UPDATE_ALL);
            if (level.getBlockEntity(extra) instanceof BarrelBlockEntity barrelBe) {
                clearContainer(barrelBe);
                spillInto(barrelBe, left);
            }
        }
        for (ItemStack stack : left) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, anchor.getX(), anchor.getY(), anchor.getZ(), stack);
            }
        }
    }

    private static void clearContainer(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, ItemStack.EMPTY);
        }
        container.setChanged();
    }

    private static void spillInto(Container container, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = stack.copy();
            for (int slot = 0; slot < container.getContainerSize() && !left.isEmpty(); slot++) {
                ItemStack there = container.getItem(slot);
                if (there.isEmpty()) {
                    container.setItem(slot, left);
                    left = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(there, left)) {
                    int move = Math.min(there.getMaxStackSize() - there.getCount(), left.getCount());
                    if (move > 0) {
                        there.grow(move);
                        left.shrink(move);
                    }
                }
            }
            stack.setCount(left.getCount());
        }
        container.setChanged();
    }

}
