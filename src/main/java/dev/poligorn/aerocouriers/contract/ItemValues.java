package dev.poligorn.aerocouriers.contract;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Cargo value for NPC collateral (GDD §8.3): datapack {@code data/<ns>/item_values/*.json}, merged.
 * Falls back to rarity defaults when no explicit price is set.
 */
public final class ItemValues extends SimpleJsonResourceReloadListener {
    private static final Map<Item, Long> ITEMS = new HashMap<>();
    private static final Map<ResourceLocation, Long> TAGS = new HashMap<>();
    private static final Map<Rarity, Long> RARITY = new HashMap<>();
    private static volatile double collateralFactor = 1.0;

    private static final long DEFAULT_COMMON = 2;
    private static final long DEFAULT_UNCOMMON = 8;
    private static final long DEFAULT_RARE = 32;
    private static final long DEFAULT_EPIC = 128;

    private ItemValues() {
        super(new Gson(), "item_values");
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ItemValues());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        ITEMS.clear();
        TAGS.clear();
        RARITY.clear();
        collateralFactor = 1.0;
        files.forEach((id, json) -> {
            try {
                merge(json.getAsJsonObject());
            } catch (RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("Item values {}: {}", id, e.getMessage());
            }
        });
        if (RARITY.isEmpty()) {
            RARITY.put(Rarity.COMMON, DEFAULT_COMMON);
            RARITY.put(Rarity.UNCOMMON, DEFAULT_UNCOMMON);
            RARITY.put(Rarity.RARE, DEFAULT_RARE);
            RARITY.put(Rarity.EPIC, DEFAULT_EPIC);
        }
        AeronauticsCouriers.LOGGER.info("Loaded item values: {} items, {} tags", ITEMS.size(), TAGS.size());
    }

    private static void merge(JsonObject root) {
        if (root.has("collateral_factor")) {
            collateralFactor = GsonHelper.getAsDouble(root, "collateral_factor");
        }
        if (root.has("items")) {
            JsonObject items = GsonHelper.getAsJsonObject(root, "items");
            for (String key : items.keySet()) {
                ResourceLocation id = ResourceLocation.parse(key);
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) {
                    ITEMS.put(item, GsonHelper.getAsLong(items, key));
                }
            }
        }
        if (root.has("tags")) {
            JsonObject tags = GsonHelper.getAsJsonObject(root, "tags");
            for (String key : tags.keySet()) {
                TAGS.put(ResourceLocation.parse(key), GsonHelper.getAsLong(tags, key));
            }
        }
        if (root.has("rarity")) {
            JsonObject rarities = GsonHelper.getAsJsonObject(root, "rarity");
            for (String key : rarities.keySet()) {
                Rarity rarity = rarityKey(key);
                if (rarity != null) {
                    RARITY.put(rarity, GsonHelper.getAsLong(rarities, key));
                }
            }
        }
    }

    public static double collateralFactor() {
        return collateralFactor;
    }

    public static long of(List<ItemStack> manifest) {
        long total = 0;
        for (ItemStack stack : manifest) {
            if (!stack.isEmpty()) {
                total += perItem(stack) * stack.getCount();
            }
        }
        return MoneyScale.fromSpurs(total);
    }

    private static @Nullable Rarity rarityKey(String key) {
        return switch (key.toLowerCase()) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> null;
        };
    }

    public static long perItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var override = WorldCargoOverrides.get(server)
                    .valueOverride(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (override.isPresent()) {
                return override.getAsLong();
            }
        }
        Long direct = ITEMS.get(stack.getItem());
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<ResourceLocation, Long> entry : TAGS.entrySet()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, entry.getKey());
            if (stack.is(tag)) {
                return entry.getValue();
            }
        }
        return RARITY.getOrDefault(stack.getRarity(), DEFAULT_COMMON);
    }
}
