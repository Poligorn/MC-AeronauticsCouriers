package dev.poligorn.aerocouriers.contract;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Datapack {@code data/<ns>/item_weights/*.json}, merged (GDD §8.2). */
public final class ItemWeights extends SimpleJsonResourceReloadListener {
    private static final Map<Item, Double> ITEMS = new HashMap<>();
    private static final Map<ResourceLocation, Double> TAGS = new HashMap<>();

    private ItemWeights() {
        super(new Gson(), "item_weights");
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ItemWeights());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        ITEMS.clear();
        TAGS.clear();
        files.forEach((id, json) -> {
            try {
                merge(json.getAsJsonObject());
            } catch (RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("Item weights {}: {}", id, e.getMessage());
            }
        });
        AeronauticsCouriers.LOGGER.info("Loaded item weights: {} items, {} tags", ITEMS.size(), TAGS.size());
    }

    private static void merge(JsonObject root) {
        if (root.has("items")) {
            JsonObject items = GsonHelper.getAsJsonObject(root, "items");
            for (String key : items.keySet()) {
                ResourceLocation id = ResourceLocation.parse(key);
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) {
                    ITEMS.put(item, GsonHelper.getAsDouble(items, key));
                }
            }
        }
        if (root.has("tags")) {
            JsonObject tags = GsonHelper.getAsJsonObject(root, "tags");
            for (String key : tags.keySet()) {
                TAGS.put(ResourceLocation.parse(key), GsonHelper.getAsDouble(tags, key));
            }
        }
    }

    public static double datapackPerItem(ItemStack stack) {
        Double direct = ITEMS.get(stack.getItem());
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<ResourceLocation, Double> entry : TAGS.entrySet()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, entry.getKey());
            if (stack.is(tag)) {
                return entry.getValue();
            }
        }
        return Double.NaN;
    }
}
