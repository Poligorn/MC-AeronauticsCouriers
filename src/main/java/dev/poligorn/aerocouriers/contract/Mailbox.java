package dev.poligorn.aerocouriers.contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-player mailbox for delivered cargo and returns (GDD §10.2 "Почта"). Waits for offline players indefinitely.
 * Money never goes through the mailbox: it is deposited straight into the {@code Economy}.
 * <p>
 * Storage is delegated to {@link CourierInbox}; this type remains for game tests and commands.
 */
public final class Mailbox extends SavedData {
    private static final String NAME = "aerocouriers_mailbox";

    /** Pre-migration flat stacks (read once by {@link CourierInbox}). */
    private final Map<UUID, List<ItemStack>> legacyBoxes = new HashMap<>();
    private transient MinecraftServer runtimeServer;

    public static Mailbox get(MinecraftServer server) {
        Mailbox mailbox = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Mailbox::new, Mailbox::load, null), NAME);
        mailbox.runtimeServer = server;
        return mailbox;
    }

    public static Mailbox load(CompoundTag tag, HolderLookup.Provider registries) {
        Mailbox mailbox = new Mailbox();
        mailbox.legacyBoxes.putAll(LegacyCodec.load(tag, registries));
        return mailbox;
    }

    public List<ItemStack> contents(UUID player) {
        return CourierInbox.get(requireServer()).allItemStacks(player);
    }

    public void post(UUID player, List<ItemStack> items) {
        post(player, items, CourierInbox.defaultCargoText(items));
    }

    public void post(UUID player, List<ItemStack> items, Component text) {
        if (items.isEmpty()) {
            return;
        }
        MinecraftServer server = requireServer();
        CourierInbox.get(server).postItems(server, player, items, text);
    }

    /** "Забрать всё": moves what fits into the inventory, the rest stays in the mailbox. Returns stacks moved. */
    public int claim(UUID player, Inventory inventory) {
        return CourierInbox.get(requireServer()).claimAll(player, inventory);
    }

    Map<UUID, List<ItemStack>> exportAll() {
        return Map.copyOf(legacyBoxes);
    }

    void clearAll() {
        if (!legacyBoxes.isEmpty()) {
            legacyBoxes.clear();
            setDirty();
        }
    }

    private MinecraftServer requireServer() {
        if (runtimeServer == null) {
            throw new IllegalStateException("Mailbox accessed before level attachment");
        }
        return runtimeServer;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LegacyCodec.save(tag, registries, legacyBoxes);
        return tag;
    }

    /** Legacy NBT only; live cargo lives in {@link CourierInbox}. */
    private static final class LegacyCodec {
        private LegacyCodec() {
        }

        static Map<UUID, List<ItemStack>> load(CompoundTag tag, HolderLookup.Provider registries) {
            return LegacyBoxes.load(tag, registries);
        }

        static void save(CompoundTag tag, HolderLookup.Provider registries, Map<UUID, List<ItemStack>> boxes) {
            LegacyBoxes.save(tag, registries, boxes);
        }
    }

    /** Isolated legacy codec to avoid pulling old Codec into the hot path. */
    private static final class LegacyBoxes {
        private static final com.mojang.serialization.Codec<Map<UUID, List<ItemStack>>> BOXES_CODEC =
                com.mojang.serialization.Codec.unboundedMap(
                        net.minecraft.core.UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf());

        static Map<UUID, List<ItemStack>> load(CompoundTag tag, HolderLookup.Provider registries) {
            Map<UUID, List<ItemStack>> boxes = new HashMap<>();
            BOXES_CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get("boxes"))
                    .resultOrPartial(error -> dev.poligorn.aerocouriers.AeronauticsCouriers.LOGGER.error("Mailbox: {}", error))
                    .ifPresent(loaded -> loaded.forEach((player, items) ->
                            boxes.put(player, new ArrayList<>(items))));
            return boxes;
        }

        static void save(CompoundTag tag, HolderLookup.Provider registries, Map<UUID, List<ItemStack>> boxes) {
            if (boxes.isEmpty()) {
                return;
            }
            tag.put("boxes", BOXES_CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), boxes)
                    .getOrThrow());
        }
    }
}
