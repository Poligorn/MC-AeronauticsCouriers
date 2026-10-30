package dev.poligorn.aerocouriers.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player onboarding progress (overworld SavedData by UUID). */
public final class TutorialData extends SavedData {
    private static final String NAME = "aerocouriers_tutorial";

    public enum State {
        NOT_STARTED, OFFERED, STARTED, ACCEPTED, IN_TRANSIT, DONE, SKIPPED, DISABLED_REMOVED;

        static final Codec<State> CODEC = Codec.STRING.xmap(
                s -> {
                    try {
                        return State.valueOf(s.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return NOT_STARTED;
                    }
                },
                s -> s.name().toLowerCase(Locale.ROOT));

        public boolean inProgress() {
            return this == STARTED || this == ACCEPTED || this == IN_TRANSIT;
        }

        public static State byOrdinal(int i) {
            State[] all = values();
            return i >= 0 && i < all.length ? all[i] : NOT_STARTED;
        }
    }

    /**
     * @param noStation         point A is a temporary spot next to the player (no station within the search radius)
     * @param completionPending the «tutorial complete» window was not shown yet
     * @param hintsSeen         bitmask of coach-marks already dismissed
     */
    public record Entry(State state, Optional<UUID> orderId, int attempt, boolean xpAwarded, int hintsSeen,
                        boolean completionPending, boolean noStation) {
        public static final Entry EMPTY = new Entry(State.NOT_STARTED, Optional.empty(), 0, false, 0, false, false);

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                State.CODEC.optionalFieldOf("state", State.NOT_STARTED).forGetter(Entry::state),
                UUIDUtil.CODEC.optionalFieldOf("order").forGetter(Entry::orderId),
                Codec.INT.optionalFieldOf("attempt", 0).forGetter(Entry::attempt),
                Codec.BOOL.optionalFieldOf("xp_awarded", false).forGetter(Entry::xpAwarded),
                Codec.INT.optionalFieldOf("hints_seen", 0).forGetter(Entry::hintsSeen),
                Codec.BOOL.optionalFieldOf("completion_pending", false).forGetter(Entry::completionPending),
                Codec.BOOL.optionalFieldOf("no_station", false).forGetter(Entry::noStation)
        ).apply(instance, Entry::new));

        public Entry withState(State next) {
            return new Entry(next, orderId, attempt, xpAwarded, hintsSeen, completionPending, noStation);
        }

        public Entry withOrder(Optional<UUID> order, boolean temporaryA) {
            return new Entry(state, order, attempt, xpAwarded, hintsSeen, completionPending, temporaryA);
        }

        public Entry nextAttempt() {
            return new Entry(state, orderId, attempt + 1, xpAwarded, hintsSeen, completionPending, noStation);
        }

        public Entry withXpAwarded() {
            return new Entry(state, orderId, attempt, true, hintsSeen, completionPending, noStation);
        }

        public Entry withHint(int bit) {
            return new Entry(state, orderId, attempt, xpAwarded, hintsSeen | bit, completionPending, noStation);
        }

        public Entry withCompletionPending(boolean pending) {
            return new Entry(state, orderId, attempt, xpAwarded, hintsSeen, pending, noStation);
        }
    }

    private static final Codec<Map<UUID, Entry>> STORE = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC);
    private final Map<UUID, Entry> entries = new HashMap<>();

    public static TutorialData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TutorialData::new, TutorialData::load, null), NAME);
    }

    private static TutorialData load(CompoundTag tag, HolderLookup.Provider registries) {
        TutorialData data = new TutorialData();
        if (tag.contains("players")) {
            STORE.parse(NbtOps.INSTANCE, tag.get("players"))
                    .resultOrPartial(e -> AeronauticsCouriers.LOGGER.error("Tutorial data: {}", e))
                    .ifPresent(data.entries::putAll);
        }
        return data;
    }

    public Entry get(UUID player) {
        return entries.getOrDefault(player, Entry.EMPTY);
    }

    public void put(UUID player, Entry entry) {
        entries.put(player, entry);
        setDirty();
    }

    public Map<UUID, Entry> all() {
        return Map.copyOf(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("players", STORE.encodeStart(NbtOps.INSTANCE, entries).getOrThrow());
        return tag;
    }
}
