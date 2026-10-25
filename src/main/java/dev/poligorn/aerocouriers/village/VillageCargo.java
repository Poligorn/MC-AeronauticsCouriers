package dev.poligorn.aerocouriers.village;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Datapack cargo tables for NPC station orders (GDD §6.2): {@code data/<ns>/village_cargo/<biome>.json}.
 * Admin-placed stations use the biome at their block; missing biomes fall back to {@code default}.
 */
public final class VillageCargo extends SimpleJsonResourceReloadListener {
    public record Offer(Item item, int min, int max) {
        ItemStack roll(RandomSource random) {
            int lo = Math.max(1, min);
            int hi = Math.max(lo, max);
            return new ItemStack(item, lo + random.nextInt(hi - lo + 1));
        }
    }

    public record Table(List<Offer> produces, List<Offer> consumes) {
        static final Table EMPTY = new Table(List.of(), List.of());
    }

    private static volatile Map<String, Table> tables = Map.of();

    private VillageCargo() {
        super(new Gson(), "village_cargo");
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VillageCargo());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Table> loaded = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id.getPath(), parse(json.getAsJsonObject()));
            } catch (RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("Village cargo {}: {}", id, e.getMessage());
            }
        });
        tables = Map.copyOf(loaded);
        AeronauticsCouriers.LOGGER.info("Loaded {} village cargo tables", tables.size());
    }

    public static Table tableFor(ResourceLocation biome) {
        Table named = tables.get(biome.getPath());
        if (named != null) {
            return named;
        }
        Table fallback = tables.get("default");
        return fallback != null ? fallback : Table.EMPTY;
    }

    /**
     * What the origin produces that the destination wants; if the overlap is empty, what the origin produces.
     */
    public static List<ItemStack> rollManifest(ResourceLocation originBiome, ResourceLocation destBiome,
                                              RandomSource random) {
        Table origin = tableFor(originBiome);
        Table dest = tableFor(destBiome);
        List<Offer> wanted = origin.produces.stream()
                .filter(offer -> dest.consumes.stream().anyMatch(need -> need.item() == offer.item()))
                .toList();
        List<Offer> pool = wanted.isEmpty() ? origin.produces : wanted;
        if (pool.isEmpty()) {
            return List.of(new ItemStack(Items.WHEAT, 8 + random.nextInt(9)));
        }
        int stacks = 1 + random.nextInt(Math.min(3, pool.size()));
        List<ItemStack> manifest = new ArrayList<>();
        for (int i = 0; i < stacks; i++) {
            manifest.add(pool.get(random.nextInt(pool.size())).roll(random));
        }
        return List.copyOf(manifest);
    }

    private static Table parse(JsonObject json) {
        return new Table(parseOffers(json, "produces"), parseOffers(json, "consumes"));
    }

    private static List<Offer> parseOffers(JsonObject json, String key) {
        List<Offer> offers = new ArrayList<>();
        if (!json.has(key)) {
            return List.of();
        }
        json.getAsJsonArray(key).forEach(element -> {
            JsonObject row = element.getAsJsonObject();
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse(GsonHelper.getAsString(row, "id")));
            if (item.isEmpty() || item.get() == Items.AIR) {
                return;
            }
            offers.add(new Offer(item.get(), GsonHelper.getAsInt(row, "min", 1), GsonHelper.getAsInt(row, "max", 16)));
        });
        return List.copyOf(offers);
    }
}
