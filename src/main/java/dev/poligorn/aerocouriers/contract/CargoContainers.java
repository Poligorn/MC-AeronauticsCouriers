package dev.poligorn.aerocouriers.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a player-usable inventory at a block (vanilla {@link Container} or a modded item-handler capability)
 * and can empty it without dropping anything into the world.
 */
public final class CargoContainers {
    private CargoContainers() {
    }

    public record Access(List<BlockPos> positions, List<ItemStack> items, IItemHandler handler,
                         @Nullable Container container) {
        public boolean empty() {
            return items.isEmpty();
        }
    }

    public static Optional<Access> find(ServerLevel level, BlockPos pos) {
        Container container = HopperBlockEntity.getContainerAt(level, pos);
        if (container != null) {
            List<ItemStack> items = copyOccupied(container);
            return Optional.of(new Access(positionsOf(level, pos, container), items, null, container));
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            return Optional.empty();
        }
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return Optional.of(new Access(List.of(pos.immutable()), items, handler, null));
    }

    /**
     * The player must be allowed to open this block: vanilla lock, interaction range, and the same
     * {@link ServerPlayer#mayInteract} check claims mods typically honour.
     */
    public static boolean canOpen(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!player.mayInteract(level, pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BaseContainerBlockEntity locked && !locked.canOpen(player)) {
            return false;
        }
        Container container = HopperBlockEntity.getContainerAt(level, pos);
        if (container != null && !container.stillValid(player)) {
            return false;
        }
        return find(level, pos).isPresent();
    }

    /** Empties the source without spawning item entities. Call before the blocks are removed. */
    public static void clear(Access access) {
        if (access.container != null) {
            for (int slot = 0; slot < access.container.getContainerSize(); slot++) {
                access.container.setItem(slot, ItemStack.EMPTY);
            }
            access.container.setChanged();
            return;
        }
        if (access.handler != null) {
            for (int slot = 0; slot < access.handler.getSlots(); slot++) {
                ItemStack stack = access.handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    access.handler.extractItem(slot, stack.getCount(), false);
                }
            }
        }
    }

    /** Removes every block that made up the inventory, with no block or item drops. */
    public static void removeWithoutDrop(ServerLevel level, Access access) {
        for (BlockPos pos : access.positions()) {
            level.removeBlock(pos, false);
        }
    }

    private static List<ItemStack> copyOccupied(Container container) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return items;
    }

    private static List<BlockPos> positionsOf(ServerLevel level, BlockPos clicked, Container container) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(clicked.immutable());
        BlockState state = level.getBlockState(clicked);
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction towards = ChestBlock.getConnectedDirection(state);
            BlockPos other = clicked.relative(towards);
            if (level.getBlockEntity(other) instanceof ChestBlockEntity) {
                positions.add(other);
            }
        }
        if (container instanceof ChestBlockEntity chest && !positions.contains(chest.getBlockPos())) {
            positions.add(chest.getBlockPos().immutable());
        }
        return List.copyOf(positions);
    }
}
