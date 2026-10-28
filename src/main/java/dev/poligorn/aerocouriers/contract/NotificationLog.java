package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player order notification history (GDD §10), capped at {@link #MAX_ENTRIES}. */
public final class NotificationLog extends SavedData {
    public static final int MAX_ENTRIES = 50;
    private static final String NAME = "aerocouriers_notifications";

    public record Entry(long id, long at, Component text, Optional<UUID> contractId, boolean read) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(Entry::id),
                Codec.LONG.fieldOf("at").forGetter(Entry::at),
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Entry::text),
                UUIDUtil.CODEC.optionalFieldOf("contract").forGetter(Entry::contractId),
                Codec.BOOL.fieldOf("read").forGetter(Entry::read)
        ).apply(instance, Entry::new));

        Entry markRead() {
            return read ? this : new Entry(id, at, text, contractId, true);
        }
    }

    private static final Codec<Map<UUID, List<Entry>>> STORE_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC.listOf());

    private final Map<UUID, List<Entry>> byPlayer = new HashMap<>();
    private long nextId = 1L;

    public static NotificationLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NotificationLog::new, NotificationLog::load, null), NAME);
    }

    public static NotificationLog load(CompoundTag tag, HolderLookup.Provider registries) {
        NotificationLog log = new NotificationLog();
        log.nextId = tag.getLong("next_id");
        if (log.nextId < 1L) {
            log.nextId = 1L;
        }
        STORE_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("players"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Notifications: {}", error))
                .ifPresent(loaded -> loaded.forEach((player, entries) ->
                        log.byPlayer.put(player, new ArrayList<>(entries))));
        return log;
    }

    public List<Entry> entries(UUID player) {
        return List.copyOf(byPlayer.getOrDefault(player, List.of()));
    }

    public int unread(MinecraftServer server, UUID player) {
        return CourierInbox.get(server).unread(player);
    }

    public Entry push(MinecraftServer server, UUID player, Component text, Optional<UUID> contractId) {
        CourierInbox.Message message = CourierInbox.get(server).pushText(server, player, text, contractId);
        return new Entry(message.id(), message.at(), message.text(), message.contractId(), message.read());
    }

    Map<UUID, List<Entry>> exportAll() {
        return Map.copyOf(byPlayer);
    }

    void clearAll() {
        if (!byPlayer.isEmpty()) {
            byPlayer.clear();
            nextId = 1L;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("next_id", nextId);
        tag.put("players", STORE_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), byPlayer)
                .getOrThrow());
        return tag;
    }
}
