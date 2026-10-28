package dev.poligorn.aerocouriers.village;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Station name dictionary (GDD §6.0): every {@code data/<ns>/station_names/*.json} is {@code {"names": [...]}}; all
 * files are merged, so a datapack replaces {@code aerocouriers:station_names/default.json} or adds its own.
 */
public final class StationNames extends SimpleJsonResourceReloadListener {
    private static final String FALLBACK = "Post Station";
    private static volatile List<String> names = List.of();

    private StationNames() {
        super(new Gson(), "station_names");
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StationNames());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<String> loaded = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                GsonHelper.getAsJsonArray(json.getAsJsonObject(), "names")
                        .forEach(name -> loaded.add(name.getAsString().strip()));
            } catch (RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("Station names {}: {}", id, e.getMessage());
            }
        });
        loaded.removeIf(String::isEmpty);
        names = List.copyOf(loaded);
        AeronauticsCouriers.LOGGER.info("Loaded {} post station names", names.size());
    }

    /** A random unused name; once the dictionary runs out, names get a number: «Name 2», «Name 3»… */
    public static String pick(RandomSource random, Set<String> taken) {
        List<String> pool = names.isEmpty() ? List.of(FALLBACK) : names;
        List<String> free = pool.stream().filter(name -> !taken.contains(name)).toList();
        if (!free.isEmpty()) {
            return free.get(random.nextInt(free.size()));
        }
        String base = pool.get(random.nextInt(pool.size()));
        for (int n = 2; ; n++) {
            String candidate = base + " " + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }
}
