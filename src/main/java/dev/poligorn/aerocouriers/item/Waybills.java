package dev.poligorn.aerocouriers.item;

import dev.poligorn.aerocouriers.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Inventory helpers for {@link TransportWaybillItem}. */
public final class Waybills {
    private Waybills() {
    }

    public static boolean hasOne(Player player) {
        return count(player) > 0;
    }

    public static int count(Player player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(ModItems.TRANSPORT_WAYBILL.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes one transport waybill from the player inventory. Creative callers should skip this. */
    public static boolean consumeOne(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(ModItems.TRANSPORT_WAYBILL.get())) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inv.setItem(slot, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    public static ItemStack stack(int count) {
        return new ItemStack(ModItems.TRANSPORT_WAYBILL.get(), count);
    }
}
