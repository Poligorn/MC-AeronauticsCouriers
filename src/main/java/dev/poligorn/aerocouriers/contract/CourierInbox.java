package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Unified player inbox: order notifications and cargo mail in one newest-first list (playtest #13).
 */
public final class CourierInbox extends SavedData {
    public static final int MAX_ENTRIES = 50;
    private static final String NAME = "aerocouriers_inbox";

    public record Message(
            long id,
            long at,
            Component text,
            Optional<UUID> contractId,
            boolean read,
            List<ItemStack> items
    ) {
        public static final Codec<Message> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(Message::id),
                Codec.LONG.fieldOf("at").forGetter(Message::at),
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Message::text),
                UUIDUtil.CODEC.optionalFieldOf("contract").forGetter(Message::contractId),
                Codec.BOOL.fieldOf("read").forGetter(Message::read),
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(Message::items)
        ).apply(instance, Message::new));

        boolean hasItems() {
            return !items.isEmpty();
        }

        Message markRead() {
            return read ? this : new Message(id, at, text, contractId, true, items);
        }

        static int totalItemCount(List<ItemStack> stacks) {
            int total = 0;
            for (ItemStack stack : stacks) {
                total += stack.getCount();
            }
            return total;
        }
    }

    private static final Codec<Map<UUID, List<Message>>> STORE_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Message.CODEC.listOf());

    private final Map<UUID, List<Message>> byPlayer = new HashMap<>();
    private long nextId = 1L;
    private boolean legacyMigrated;

    public static CourierInbox get(MinecraftServer server) {
        CourierInbox inbox = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CourierInbox::new, CourierInbox::load, null), NAME);
        inbox.migrateLegacy(server);
        return inbox;
    }

    public static CourierInbox load(CompoundTag tag, HolderLookup.Provider registries) {
        CourierInbox inbox = new CourierInbox();
        inbox.nextId = tag.getLong("next_id");
        if (inbox.nextId < 1L) {
            inbox.nextId = 1L;
        }
        inbox.legacyMigrated = tag.getBoolean("legacy_migrated");
        STORE_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("players"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Inbox: {}", error))
                .ifPresent(loaded -> loaded.forEach((player, entries) ->
                        inbox.byPlayer.put(player, new ArrayList<>(entries))));
        return inbox;
    }

    private void migrateLegacy(MinecraftServer server) {
        if (legacyMigrated) {
            return;
        }
        NotificationLog notifications = NotificationLog.get(server);
        Mailbox mailbox = Mailbox.get(server);
        boolean changed = false;
        for (Map.Entry<UUID, List<NotificationLog.Entry>> entry : notifications.exportAll().entrySet()) {
            UUID player = entry.getKey();
            List<Message> list = messagesMutable(player);
            for (NotificationLog.Entry note : entry.getValue()) {
                list.add(new Message(note.id(), note.at(), note.text(), note.contractId(), note.read(), List.of()));
                nextId = Math.max(nextId, note.id() + 1L);
            }
            changed = true;
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : mailbox.exportAll().entrySet()) {
            List<ItemStack> stacks = entry.getValue();
            if (stacks.isEmpty()) {
                continue;
            }
            UUID player = entry.getKey();
            List<Message> list = messagesMutable(player);
            list.add(new Message(
                    nextId++,
                    ContractManager.now(server),
                    defaultCargoText(stacks),
                    Optional.empty(),
                    false,
                    copyStacks(stacks)));
            changed = true;
        }
        if (changed) {
            trimAll();
            notifications.clearAll();
            mailbox.clearAll();
            legacyMigrated = true;
            setDirty();
            AeronauticsCouriers.LOGGER.info("[inbox] Migrated legacy mail and notifications into unified inbox");
        } else {
            legacyMigrated = true;
            setDirty();
        }
    }

    public List<Message> messages(UUID player) {
        return messages(player, Comparator.comparingLong(Message::at).reversed());
    }

    public List<Message> messages(UUID player, Comparator<Message> order) {
        List<Message> list = byPlayer.get(player);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().sorted(order).toList();
    }

    public int unread(UUID player) {
        List<Message> list = byPlayer.get(player);
        if (list == null) {
            return 0;
        }
        int count = 0;
        for (Message message : list) {
            if (!message.read()) {
                count++;
            }
        }
        return count;
    }

    public int mailStackCount(UUID player) {
        int stacks = 0;
        for (Message message : byPlayer.getOrDefault(player, List.of())) {
            stacks += message.items().size();
        }
        return stacks;
    }

    public Message pushText(MinecraftServer server, UUID player, Component text, Optional<UUID> contractId) {
        List<Message> list = messagesMutable(player);
        Message message = new Message(nextId++, ContractManager.now(server), text, contractId, false, List.of());
        list.add(message);
        trim(list);
        setDirty();
        return message;
    }

    public Message postItems(MinecraftServer server, UUID player, List<ItemStack> items, Component text) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("empty items");
        }
        List<Message> list = messagesMutable(player);
        Message message = new Message(
                nextId++,
                ContractManager.now(server),
                text,
                Optional.empty(),
                false,
                copyStacks(items));
        list.add(message);
        trim(list);
        setDirty();
        return message;
    }

    public void markAllRead(UUID player) {
        List<Message> list = byPlayer.get(player);
        if (list == null || list.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Message before = list.get(i);
            Message after = before.markRead();
            if (after != before) {
                list.set(i, after);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public boolean dismiss(UUID player, long messageId) {
        List<Message> list = byPlayer.get(player);
        if (list == null) {
            return false;
        }
        for (Iterator<Message> it = list.iterator(); it.hasNext();) {
            Message message = it.next();
            if (message.id() == messageId && !message.hasItems()) {
                it.remove();
                if (list.isEmpty()) {
                    byPlayer.remove(player);
                }
                setDirty();
                return true;
            }
        }
        return false;
    }

    public int clearPlain(UUID player) {
        List<Message> list = byPlayer.get(player);
        if (list == null) {
            return 0;
        }
        int removed = 0;
        for (Iterator<Message> it = list.iterator(); it.hasNext();) {
            Message message = it.next();
            if (!message.hasItems()) {
                it.remove();
                removed++;
            }
        }
        if (list.isEmpty()) {
            byPlayer.remove(player);
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Moves what fits into the inventory; remaining stacks stay on the message. Deletes the message when empty. */
    public int claimMessage(UUID player, long messageId, Inventory inventory) {
        List<Message> list = byPlayer.get(player);
        if (list == null) {
            return 0;
        }
        for (int i = 0; i < list.size(); i++) {
            Message message = list.get(i);
            if (message.id() != messageId) {
                continue;
            }
            if (!message.hasItems()) {
                return 0;
            }
            int movedStacks = 0;
            List<ItemStack> left = new ArrayList<>();
            for (ItemStack stack : message.items()) {
                ItemStack remaining = stack.copy();
                inventory.add(remaining);
                if (remaining.isEmpty()) {
                    movedStacks++;
                } else {
                    left.add(remaining);
                }
            }
            if (left.isEmpty()) {
                list.remove(i);
                if (list.isEmpty()) {
                    byPlayer.remove(player);
                }
            } else {
                list.set(i, new Message(message.id(), message.at(), message.text(), message.contractId(),
                        message.read(), left));
            }
            setDirty();
            return movedStacks;
        }
        return 0;
    }

    /** Claim every cargo message (command / legacy «забрать всё»). */
    public int claimAll(UUID player, Inventory inventory) {
        int moved = 0;
        List<Message> list = byPlayer.get(player);
        if (list == null) {
            return 0;
        }
        for (int i = 0; i < list.size();) {
            Message message = list.get(i);
            if (!message.hasItems()) {
                i++;
                continue;
            }
            moved += claimMessage(player, message.id(), inventory);
            list = byPlayer.get(player);
            if (list == null) {
                break;
            }
        }
        return moved;
    }

    public List<ItemStack> allItemStacks(UUID player) {
        List<ItemStack> out = new ArrayList<>();
        for (Message message : byPlayer.getOrDefault(player, List.of())) {
            for (ItemStack stack : message.items()) {
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return List.copyOf(out);
    }

    private List<Message> messagesMutable(UUID player) {
        return byPlayer.computeIfAbsent(player, key -> new ArrayList<>());
    }

    private void trimAll() {
        for (List<Message> list : byPlayer.values()) {
            trim(list);
        }
    }

    private static void trim(List<Message> list) {
        while (list.size() > MAX_ENTRIES) {
            list.removeFirst();
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> items) {
        List<ItemStack> copies = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return copies;
    }

    public static Component defaultCargoText(List<ItemStack> stacks) {
        return Component.translatable("ui.aerocouriers.inbox.cargo_return",
                Message.totalItemCount(stacks));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("next_id", nextId);
        tag.putBoolean("legacy_migrated", legacyMigrated);
        tag.put("players", STORE_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), byPlayer)
                .getOrThrow());
        return tag;
    }
}
