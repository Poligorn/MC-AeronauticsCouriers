package dev.poligorn.aerocouriers.economy;

import java.util.UUID;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.Mailbox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Balance is emeralds on the player: loose emeralds plus emerald blocks at 9 each. Withdrawal spends loose emeralds
 * first, then breaks blocks and returns the change as emeralds (mailbox if the inventory is full). A deposit that
 * does not fit, and any credit to an offline player, goes to the mailbox with the usual "Забрать" row.
 */
public final class EmeraldEconomy implements Economy {
    private final MinecraftServer server;

    public EmeraldEconomy(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public long balance(UUID player) {
        ServerPlayer online = ContractManager.findPlayer(server, player);
        if (online == null) {
            return 0L;
        }
        return count(online.getInventory());
    }

    @Override
    public boolean withdraw(UUID player, long amount, String reason) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative withdrawal: " + amount);
        }
        if (amount == 0) {
            return true;
        }
        ServerPlayer online = ContractManager.findPlayer(server, player);
        if (online == null) {
            return false;
        }
        Inventory inventory = online.getInventory();
        if (count(inventory) < amount) {
            return false;
        }
        long left = consume(inventory, Items.EMERALD, amount);
        while (left > 0) {
            int slot = find(inventory, Items.EMERALD_BLOCK);
            if (slot < 0) {
                return false;
            }
            inventory.getItem(slot).shrink(1);
            long use = Math.min(9L, left);
            long change = 9L - use;
            left -= use;
            if (change > 0) {
                give(online, online.getUUID(), change);
            }
        }
        return true;
    }

    @Override
    public void deposit(UUID player, long amount, String reason) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative deposit: " + amount);
        }
        if (amount == 0) {
            return;
        }
        give(ContractManager.findPlayer(server, player), player, amount);
    }

    private void give(ServerPlayer online, UUID player, long amount) {
        long left = amount;
        Inventory inventory = online == null ? null : online.getInventory();
        while (left > 0 && inventory != null) {
            int size = (int) Math.min(64L, left);
            ItemStack stack = new ItemStack(Items.EMERALD, size);
            inventory.add(stack);
            int leftover = stack.getCount();
            left -= size - leftover;
            if (leftover > 0) {
                Mailbox.get(server).post(player, java.util.List.of(stack),
                        Component.translatable("ui.aerocouriers.inbox.emeralds", leftover));
                left -= leftover;
                break;
            }
        }
        if (left > 0) {
            mail(player, left);
        }
    }

    private void mail(UUID player, long amount) {
        long left = amount;
        while (left > 0) {
            int size = (int) Math.min(64L, left);
            Mailbox.get(server).post(player, java.util.List.of(new ItemStack(Items.EMERALD, size)),
                    Component.translatable("ui.aerocouriers.inbox.emeralds", size));
            left -= size;
        }
    }

    static long count(Inventory inventory) {
        long total = 0L;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.EMERALD)) {
                total += stack.getCount();
            } else if (stack.is(Items.EMERALD_BLOCK)) {
                total += (long) stack.getCount() * 9L;
            }
        }
        return total;
    }

    private static long consume(Inventory inventory, Item item, long amount) {
        long left = amount;
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int take = (int) Math.min(stack.getCount(), left);
            stack.shrink(take);
            left -= take;
        }
        return left;
    }

    private static int find(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }
}
