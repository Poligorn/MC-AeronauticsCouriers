package dev.poligorn.aerocouriers.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player tracked / pinned choice for tasks and the auto-track switch (the only stored part of the task list). */
public final class TaskPrefs extends SavedData {
    private static final String NAME = "aerocouriers_task_prefs";
    static final int TRACKED = 1;
    static final int PINNED = 2;
    /** Explicitly hidden from the HUD panel (overrides the default for the source). */
    static final int UNTRACKED = 4;

    private final Map<UUID, Map<String, Integer>> flags = new HashMap<>();
    private final Set<UUID> autoTrackOff = new HashSet<>();

    public static TaskPrefs get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TaskPrefs::new, TaskPrefs::load, null), NAME);
    }

    private static TaskPrefs load(CompoundTag tag, HolderLookup.Provider registries) {
        TaskPrefs prefs = new TaskPrefs();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag entry = players.getCompound(key);
            CompoundTag tasks = entry.getCompound("tasks");
            Map<String, Integer> map = new HashMap<>();
            for (String id : tasks.getAllKeys()) {
                map.put(id, tasks.getInt(id));
            }
            prefs.flags.put(player, map);
            if (entry.getBoolean("auto_track_off")) {
                prefs.autoTrackOff.add(player);
            }
        }
        for (String key : tag.getCompound("auto_off").getAllKeys()) {
            try {
                prefs.autoTrackOff.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // skip malformed keys
            }
        }
        return prefs;
    }

    int flags(UUID player, String taskId) {
        return flags.getOrDefault(player, Map.of()).getOrDefault(taskId, 0);
    }

    boolean autoTrack(UUID player) {
        return !autoTrackOff.contains(player);
    }

    void setAutoTrack(UUID player, boolean on) {
        if (on ? autoTrackOff.remove(player) : autoTrackOff.add(player)) {
            setDirty();
        }
    }

    void toggleTracked(UUID player, String taskId, boolean currentlyTracked) {
        int f = flags(player, taskId);
        f = currentlyTracked ? (f & ~TRACKED) | UNTRACKED : (f | TRACKED) & ~UNTRACKED;
        put(player, taskId, f);
    }

    void togglePinned(UUID player, String taskId) {
        put(player, taskId, flags(player, taskId) ^ PINNED);
    }

    /** Drops choices for tasks that no longer exist. */
    void retain(UUID player, Set<String> live) {
        Map<String, Integer> map = flags.get(player);
        if (map != null && map.keySet().retainAll(live)) {
            setDirty();
        }
    }

    private void put(UUID player, String taskId, int f) {
        Map<String, Integer> map = flags.computeIfAbsent(player, k -> new HashMap<>());
        if (f == 0) {
            map.remove(taskId);
        } else {
            map.put(taskId, f);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        flags.forEach((player, map) -> {
            CompoundTag tasks = new CompoundTag();
            map.forEach(tasks::putInt);
            CompoundTag entry = new CompoundTag();
            entry.put("tasks", tasks);
            players.put(player.toString(), entry);
        });
        tag.put("players", players);
        CompoundTag off = new CompoundTag();
        autoTrackOff.forEach(p -> off.putBoolean(p.toString(), true));
        tag.put("auto_off", off);
        return tag;
    }
}
