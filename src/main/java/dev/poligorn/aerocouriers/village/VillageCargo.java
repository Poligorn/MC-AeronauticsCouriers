package dev.poligorn.aerocouriers.village;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Datapack cargo tables for NPC station orders (GDD §6.2): {@code data/<ns>/village_cargo/<biome>.json}.
 * Rare cargo: {@code data/<ns>/village_cargo/rare/<id>.json} (GDD §6.4).
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

    public record ManifestRoll(List<ItemStack> stacks, boolean rare) {
    }

    interface RareEntry {
        ItemStack roll(HolderLookup.RegistryLookup<Enchantment> enchantments, RandomSource random);

        int minReputation();
    }

    record RareItemEntry(int minReputation, int weight, Offer offer) implements RareEntry {
        @Override
        public ItemStack roll(HolderLookup.RegistryLookup<Enchantment> enchantments, RandomSource random) {
            return offer.roll(random);
        }

        @Override
        public int minReputation() {
            return minReputation;
        }
    }

    record RareEnchantedBookEntry(int minReputation, int weight, List<ResourceLocation> enchantmentIds, int levelMin,
                                  int levelMax) implements RareEntry {
        @Override
        public ItemStack roll(HolderLookup.RegistryLookup<Enchantment> enchantments, RandomSource random) {
            ResourceLocation id = enchantmentIds.get(random.nextInt(enchantmentIds.size()));
            Optional<Holder.Reference<Enchantment>> holder = enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, id));
            if (holder.isEmpty()) {
                return new ItemStack(Items.BOOK);
            }
            int lo = Math.max(1, levelMin);
            int hi = Math.max(lo, levelMax);
            int level = lo + random.nextInt(hi - lo + 1);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(holder.get(), level);
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            return book;
        }

        @Override
        public int minReputation() {
            return minReputation;
        }
    }

    private static volatile Map<String, Table> tables = Map.of();
    private static volatile List<RareEntry> rareEntries = List.of();

    private VillageCargo() {
        super(new Gson(), "village_cargo");
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VillageCargo());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Table> loaded = new HashMap<>();
        List<RareEntry> rare = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                if (id.getPath().startsWith("rare/")) {
                    rare.add(parseRare(json.getAsJsonObject()));
                } else {
                    loaded.put(id.getPath(), parse(json.getAsJsonObject()));
                }
            } catch (RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("Village cargo {}: {}", id, e.getMessage());
            }
        });
        tables = Map.copyOf(loaded);
        rareEntries = List.copyOf(rare);
        AeronauticsCouriers.LOGGER.info("Loaded {} village cargo tables, {} rare entries", tables.size(), rareEntries.size());
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
     * When {@code originReputation} unlocks rare tables, may replace one stack with rare cargo.
     */
    public static ManifestRoll rollManifest(ResourceLocation originBiome, ResourceLocation destBiome,
                                            int originReputation, HolderLookup.RegistryLookup<Enchantment> enchantments,
                                            RandomSource random) {
        Table origin = tableFor(originBiome);
        Table dest = tableFor(destBiome);
        List<Offer> wanted = origin.produces.stream()
                .filter(offer -> dest.consumes.stream().anyMatch(need -> need.item() == offer.item()))
                .toList();
        List<Offer> pool = wanted.isEmpty() ? origin.produces : wanted;
        List<ItemStack> manifest = new ArrayList<>();
        if (pool.isEmpty()) {
            manifest.add(new ItemStack(Items.WHEAT, 8 + random.nextInt(9)));
        } else {
            int stacks = 1 + random.nextInt(Math.min(3, pool.size()));
            for (int i = 0; i < stacks; i++) {
                manifest.add(pool.get(random.nextInt(pool.size())).roll(random));
            }
        }
        boolean rare = tryInjectRare(originReputation, enchantments, random, manifest);
        return new ManifestRoll(List.copyOf(manifest), rare);
    }

    static boolean tryInjectRare(int originReputation, HolderLookup.RegistryLookup<Enchantment> enchantments,
                                 RandomSource random, List<ItemStack> manifest) {
        if (StationReputation.tier(originReputation) <= 0) {
            return false;
        }
        double chance = CouriersServerConfig.VILLAGE_RARE_ROLL_CHANCE.get();
        if (chance <= 0 || random.nextDouble() >= chance) {
            return false;
        }
        List<RareEntry> eligible = rareEntries.stream()
                .filter(e -> originReputation >= e.minReputation())
                .toList();
        if (eligible.isEmpty()) {
            return false;
        }
        int totalWeight = eligible.stream().mapToInt(VillageCargo::thisWeight).sum();
        if (totalWeight <= 0) {
            return false;
        }
        int pick = random.nextInt(totalWeight);
        RareEntry chosen = eligible.get(0);
        for (RareEntry entry : eligible) {
            pick -= thisWeight(entry);
            if (pick < 0) {
                chosen = entry;
                break;
            }
        }
        ItemStack stack = chosen.roll(enchantments, random);
        if (!stack.isEmpty()) {
            if (manifest.isEmpty()) {
                manifest.add(stack);
            } else {
                manifest.set(random.nextInt(manifest.size()), stack);
            }
            return true;
        }
        return false;
    }

    private static int thisWeight(RareEntry entry) {
        if (entry instanceof RareItemEntry item) {
            return Math.max(1, item.weight());
        }
        if (entry instanceof RareEnchantedBookEntry book) {
            return Math.max(1, book.weight());
        }
        return 1;
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

    private static RareEntry parseRare(JsonObject json) {
        int minRep = GsonHelper.getAsInt(json, "min_reputation", 10);
        int weight = GsonHelper.getAsInt(json, "weight", 1);
        if (json.has("enchanted_book")) {
            JsonObject book = json.getAsJsonObject("enchanted_book");
            List<ResourceLocation> ids = parseEnchantmentIds(book);
            int levelMin = GsonHelper.getAsInt(book, "level_min", 1);
            int levelMax = GsonHelper.getAsInt(book, "level_max", levelMin);
            return new RareEnchantedBookEntry(minRep, weight, ids, levelMin, levelMax);
        }
        JsonObject item = json.has("item") ? json.getAsJsonObject("item") : json;
        Optional<Item> resolved = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.parse(GsonHelper.getAsString(item, "id")));
        if (resolved.isEmpty() || resolved.get() == Items.AIR) {
            throw new IllegalArgumentException("missing item id");
        }
        Offer offer = new Offer(resolved.get(), GsonHelper.getAsInt(item, "min", 1), GsonHelper.getAsInt(item, "max", 1));
        return new RareItemEntry(minRep, weight, offer);
    }

    private static List<ResourceLocation> parseEnchantmentIds(JsonObject book) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (book.has("enchantments")) {
            JsonArray array = book.getAsJsonArray("enchantments");
            array.forEach(el -> ids.add(ResourceLocation.parse(el.getAsString())));
        } else if (book.has("enchantment")) {
            ids.add(ResourceLocation.parse(GsonHelper.getAsString(book, "enchantment")));
        }
        if (ids.isEmpty()) {
            ids.add(ResourceLocation.withDefaultNamespace("unbreaking"));
        }
        return List.copyOf(ids);
    }
}
