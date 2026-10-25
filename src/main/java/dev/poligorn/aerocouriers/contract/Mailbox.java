package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-player mailbox for delivered cargo and returns (GDD §10.2 "Почта"). Waits for offline players indefinitely.
 * Money never goes through the mailbox: it is deposited straight into the {@code Economy}.
 */
public final class Mailbox extends SavedData {
    private static final String NAME = "aerocouriers_mailbox";
    private static final Codec<Map<UUID, List<ItemStack>>> BOXES_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf());

    private final Map<UUID, List<ItemStack>> boxes = new HashMap<>();

    public static Mailbox get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Mailbox::new, Mailbox::load, null), NAME);
    }

    public static Mailbox load(CompoundTag tag, HolderLookup.Provider registries) {
        Mailbox mailbox = new Mailbox();
        BOXES_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("boxes"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Mailbox: {}", error))
                .ifPresent(loaded -> loaded.forEach((player, items) -> mailbox.boxes.put(player, new ArrayList<>(items))));
        return mailbox;
    }

    public List<ItemStack> contents(UUID player) {
        return List.copyOf(boxes.getOrDefault(player, List.of()));
    }

    public void post(UUID player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        List<ItemStack> box = boxes.computeIfAbsent(player, key -> new ArrayList<>());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                box.add(stack.copy());
            }
        }
        setDirty();
    }

    /** "Забрать всё": moves what fits into the inventory, the rest stays in the mailbox. Returns stacks moved. */
    public int claim(UUID player, Inventory inventory) {
        List<ItemStack> box = boxes.get(player);
        if (box == null) {
            return 0;
        }
        int moved = 0;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : box) {
            ItemStack remaining = stack.copy();
            inventory.add(remaining);
            if (remaining.isEmpty()) {
                moved++;
            } else {
                left.add(remaining);
            }
        }
        if (left.isEmpty()) {
            boxes.remove(player);
        } else {
            boxes.put(player, left);
        }
        setDirty();
        return moved;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("boxes", BOXES_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), boxes)
                .getOrThrow());
        return tag;
    }
}
