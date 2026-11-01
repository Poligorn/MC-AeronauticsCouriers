package dev.poligorn.aerocouriers.village;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.ItemValues;
import dev.poligorn.aerocouriers.contract.Weights;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
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
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Datapack cargo tables for NPC station orders (GDD §6.2): {@code data/<ns>/village_cargo/<biome>.json}.
 * Rare cargo: {@code data/<ns>/village_cargo/rare/<id>.json} (GDD §6.4).
 */
public final class VillageCargo extends SimpleJsonResourceReloadListener {
    public record Offer(
            Item item,
            int min,
            int max,
            int stacksMin,
            int stacksMax,
            int pickWeight,
            boolean valuable,
            double valueMultiplier,
            CargoSize minSize
    ) {
        static Offer of(Item item, int min, int max) {
            return new Offer(item, min, max, 1, 1, 1, false, 1.0, CargoSize.S);
        }

        ItemStack roll(RandomSource random) {
            int cap = Math.max(1, item.getDefaultMaxStackSize());
            int lo = Math.max(1, Math.min(min, cap));
            int hi = Math.max(lo, Math.min(max, cap));
            return new ItemStack(item, lo + random.nextInt(hi - lo + 1));
        }

        int rollStacks(RandomSource random) {
            int lo = Math.max(1, stacksMin);
            int hi = Math.max(lo, stacksMax);
            return lo + random.nextInt(hi - lo + 1);
        }

        double valueScale() {
            if (valueMultiplier > 0.0 && valueMultiplier != 1.0) {
                return valueMultiplier;
            }
            if (valuable) {
                return CouriersServerConfig.villageValuableMultiplier();
            }
            return 1.0;
        }

        int selectionWeight() {
            return Math.max(1, pickWeight);
        }
    }

    public record Table(List<Offer> produces, List<Offer> consumes) {
        static final Table EMPTY = new Table(List.of(), List.of());
    }

    public record ManifestRoll(List<ItemStack> stacks, boolean rare, boolean valuable, long valueSpurs,
                               long valuableSpurs) {
        public ManifestRoll(List<ItemStack> stacks, boolean rare) {
            this(stacks, rare, false, 0L, 0L);
        }

        public ManifestRoll(List<ItemStack> stacks, boolean rare, boolean valuable, long valueSpurs) {
            this(stacks, rare, valuable, valueSpurs, 0L);
        }
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
    private static volatile Set<Item> rareItems = Set.of();

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
        Set<Item> items = new HashSet<>();
        for (RareEntry entry : rare) {
            if (entry instanceof RareItemEntry item) {
                items.add(item.offer().item());
            }
        }
        rareItems = Set.copyOf(items);
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

    /** Highest container size this reputation tier may publish (tier 0 = S … tier 3+ = XL). */
    public static CargoSize maxSizeForTier(int tier) {
        int index = Math.max(0, Math.min(CargoSize.XL.ordinal(), tier));
        return CargoSize.values()[index];
    }

    /**
     * Weighted size roll among sizes unlocked by {@code originReputation} and allowed by {@code policy}.
     * Missing weights are dropped and the rest are renormalised.
     */
    public static Optional<CargoSize> rollTargetSize(int originReputation, VillageBoardPolicy policy,
                                                     RandomSource random) {
        return rollTargetSize(originReputation, policy, Optional.empty(), random);
    }

    public static Optional<CargoSize> rollTargetSize(int originReputation, VillageBoardPolicy policy,
                                                     Optional<CargoSize> forceSize, RandomSource random) {
        CargoSize max = maxSizeForTier(StationReputation.tier(originReputation));
        if (forceSize.isPresent()) {
            CargoSize forced = forceSize.get();
            if (forced.ordinal() <= max.ordinal() && policy.allowsSize(forced)) {
                return Optional.of(forced);
            }
        }
        int total = 0;
        int[] weights = new int[CargoSize.values().length];
        for (CargoSize size : CargoSize.values()) {
            if (size.ordinal() > max.ordinal() || !policy.allowsSize(size)) {
                continue;
            }
            int weight = CouriersServerConfig.villageSizeWeight(size);
            if (weight <= 0) {
                continue;
            }
            weights[size.ordinal()] = weight;
            total += weight;
        }
        if (total <= 0) {
            for (CargoSize size : CargoSize.values()) {
                if (size.ordinal() <= max.ordinal() && policy.allowsSize(size)) {
                    return Optional.of(size);
                }
            }
            return Optional.empty();
        }
        int pick = random.nextInt(total);
        for (CargoSize size : CargoSize.values()) {
            pick -= weights[size.ordinal()];
            if (pick < 0) {
                return Optional.of(size);
            }
        }
        return Optional.of(CargoSize.S);
    }

    /**
     * What the origin produces that the destination wants; if the overlap is empty, what the origin produces.
     * Rolls a target container size for the origin reputation, then fills until {@link CargoSize#smallestFor}
     * actually returns that size. Rare cargo may replace one stack when the origin tier unlocks it.
     */
    public static ManifestRoll rollManifest(ResourceLocation originBiome, ResourceLocation destBiome,
                                            int originReputation, HolderLookup.RegistryLookup<Enchantment> enchantments,
                                            RandomSource random) {
        return rollManifest(originBiome, destBiome, originReputation, VillageBoardPolicy.DEFAULT, Optional.empty(),
                enchantments, random);
    }

    public static ManifestRoll rollManifest(ResourceLocation originBiome, ResourceLocation destBiome,
                                            int originReputation, VillageBoardPolicy policy,
                                            Optional<CargoSize> forceSize,
                                            HolderLookup.RegistryLookup<Enchantment> enchantments,
                                            RandomSource random) {
        Optional<CargoSize> targetOpt = rollTargetSize(originReputation, policy, forceSize, random);
        if (targetOpt.isEmpty()) {
            return new ManifestRoll(List.of(), false, false, 0L, 0L);
        }
        CargoSize target = targetOpt.get();
        List<Offer> pool = poolFor(originBiome, destBiome, target);
        List<ItemStack> manifest = new ArrayList<>();
        long[] value = {0L};
        long[] valuableValue = {0L};
        boolean[] valuable = {false};
        fillToSize(manifest, pool, target, random, value, valuableValue, valuable);
        boolean rare = false;
        if (policy.allowRare()) {
            List<ItemStack> beforeRare = copyStacks(manifest);
            long valueBefore = value[0];
            long valuableBefore = valuableValue[0];
            boolean flagBefore = valuable[0];
            rare = tryInjectRare(originReputation, enchantments, random, manifest);
            if (rare) {
                value[0] = valueOf(manifest, 1.0) + Math.max(0L, valuableValue[0]);
                padToSize(manifest, pool, target, random, value, valuableValue, valuable);
                trimToSize(manifest, target, value);
                if (!matchesSize(manifest, target)) {
                    manifest.clear();
                    manifest.addAll(beforeRare);
                    value[0] = valueBefore;
                    valuableValue[0] = valuableBefore;
                    valuable[0] = flagBefore;
                    rare = false;
                }
            }
        }
        maybeInjectValuable(manifest, originBiome, pool, target, random, value, valuableValue, valuable);
        if (!matchesSize(manifest, target)) {
            fillToSize(manifest, pool, target, random, value, valuableValue, valuable);
        }
        if (manifest.isEmpty()) {
            manifest.add(new ItemStack(Items.WHEAT, 8 + random.nextInt(9)));
            value[0] = valueOf(manifest, 1.0);
        }
        trimToSize(manifest, target, value);
        return new ManifestRoll(List.copyOf(manifest), rare, valuable[0], Math.max(0L, value[0]),
                Math.max(0L, valuableValue[0]));
    }

    public static boolean matchesSize(List<ItemStack> manifest, CargoSize target) {
        return CargoSize.smallestFor(manifest.size(), Weights.of(manifest)).orElse(null) == target;
    }

    /**
     * Index of the rare stack in a village-rare manifest, or -1. Enchanted books first, then items from
     * {@code village_cargo/rare}, then the first non-empty stack.
     */
    public static int rareStackIndex(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty() && stack.is(Items.ENCHANTED_BOOK)) {
                return i;
            }
        }
        Set<Item> rare = rareItems;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty() && rare.contains(stack.getItem())) {
                return i;
            }
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
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

    private static List<Offer> poolFor(ResourceLocation originBiome, ResourceLocation destBiome, CargoSize target) {
        Table origin = tableFor(originBiome);
        Table dest = tableFor(destBiome);
        List<Offer> wanted = origin.produces.stream()
                .filter(offer -> dest.consumes.stream().anyMatch(need -> need.item() == offer.item()))
                .toList();
        List<Offer> base = wanted.isEmpty() ? origin.produces : wanted;
        List<Offer> sized = base.stream()
                .filter(offer -> offer.minSize().ordinal() <= target.ordinal())
                .toList();
        List<Offer> bulk = sized.stream().filter(offer -> !isValuable(offer)).toList();
        if (!bulk.isEmpty()) {
            return bulk;
        }
        if (!sized.isEmpty()) {
            return sized;
        }
        if (!base.isEmpty()) {
            return base;
        }
        return List.of(new Offer(Items.WHEAT, 16, 64, 1, Math.max(4, target.slots()), 4, false, 1.0, CargoSize.S),
                new Offer(Items.OAK_LOG, 16, 64, 1, Math.max(4, target.slots()), 6, false, 1.0, CargoSize.S));
    }

    private static boolean isValuable(Offer offer) {
        return offer.valuable() || offer.valueScale() > 1.0;
    }

    private static List<Offer> valuableOffers(ResourceLocation originBiome, CargoSize target) {
        return tableFor(originBiome).produces.stream()
                .filter(offer -> isValuable(offer) && offer.minSize().ordinal() <= target.ordinal())
                .toList();
    }

    /**
     * One valuable origin row, after bulk fill. Frequency is {@code village_valuable_inject_chance} so a crate of
     * wheat does not pick gold on every stack roll.
     */
    private static void maybeInjectValuable(List<ItemStack> manifest, ResourceLocation originBiome, List<Offer> pool,
                                           CargoSize target, RandomSource random, long[] value, long[] valuableValue,
                                           boolean[] valuable) {
        if (target == CargoSize.S || manifest.isEmpty()) {
            return;
        }
        double chance = CouriersServerConfig.villageValuableInjectChance();
        if (chance <= 0.0 || random.nextDouble() >= chance) {
            return;
        }
        List<Offer> valuables = valuableOffers(originBiome, target);
        if (valuables.isEmpty()) {
            return;
        }
        Offer offer = pick(valuables, random);
        ItemStack candidate = offer.roll(random);
        if (candidate.isEmpty()) {
            return;
        }
        List<ItemStack> before = copyStacks(manifest);
        long valueBefore = value[0];
        long valuableBefore = valuableValue[0];
        boolean flagBefore = valuable[0];
        ItemStack added = fit(candidate, manifest, target);
        if (!added.isEmpty()) {
            manifest.add(added);
        } else {
            int index = random.nextInt(manifest.size());
            ItemStack fitted = fit(candidate, withoutIndex(manifest, index), target);
            if (fitted.isEmpty()) {
                return;
            }
            manifest.set(index, fitted);
            added = fitted;
        }
        padToSize(manifest, pool, target, random, value, valuableValue, valuable);
        trimToSize(manifest, target, value);
        if (!matchesSize(manifest, target)) {
            manifest.clear();
            manifest.addAll(before);
            value[0] = valueBefore;
            valuableValue[0] = valuableBefore;
            valuable[0] = flagBefore;
            return;
        }
        value[0] = Math.max(0L, valueBefore + lineValue(added, offer.valueScale()));
        valuableValue[0] = valuableBefore + lineValue(added, offer.valueScale());
        valuable[0] = true;
    }

    private static List<ItemStack> withoutIndex(List<ItemStack> manifest, int skip) {
        List<ItemStack> copy = new ArrayList<>(manifest.size());
        for (int i = 0; i < manifest.size(); i++) {
            if (i != skip) {
                copy.add(manifest.get(i));
            }
        }
        return copy;
    }

    private static void fillToSize(List<ItemStack> manifest, List<Offer> pool, CargoSize target, RandomSource random,
                                   long[] value, long[] valuableValue, boolean[] valuable) {
        if (pool.isEmpty()) {
            return;
        }
        int sCap = target == CargoSize.S
                ? 1 + random.nextInt(CouriersServerConfig.villageSStacksMax())
                : target.slots();
        int attempts = 0;
        int maxAttempts = Math.max(16, target.slots() * 6);
        while (attempts++ < maxAttempts) {
            if (target == CargoSize.S) {
                if (!manifest.isEmpty() && manifest.size() >= sCap) {
                    return;
                }
            } else if (matchesSize(manifest, target)) {
                return;
            }
            Offer offer = pick(pool, random);
            int burst = offer.rollStacks(random);
            boolean added = false;
            for (int i = 0; i < burst; i++) {
                if (target == CargoSize.S && manifest.size() >= sCap) {
                    break;
                }
                if (target != CargoSize.S && matchesSize(manifest, target)) {
                    break;
                }
                ItemStack stack = fit(offer.roll(random), manifest, target);
                if (stack.isEmpty()) {
                    break;
                }
                manifest.add(stack);
                long line = lineValue(stack, offer.valueScale());
                value[0] += line;
                if (isValuable(offer)) {
                    valuableValue[0] += line;
                    valuable[0] = true;
                }
                added = true;
            }
            if (!added && !canGrow(manifest, target)) {
                return;
            }
        }
    }

    private static void padToSize(List<ItemStack> manifest, List<Offer> pool, CargoSize target, RandomSource random,
                                  long[] value, long[] valuableValue, boolean[] valuable) {
        if (target == CargoSize.S || matchesSize(manifest, target) || pool.isEmpty()) {
            return;
        }
        int attempts = 0;
        while (attempts++ < target.slots() * 4 && !matchesSize(manifest, target) && canGrow(manifest, target)) {
            Offer offer = pick(pool, random);
            ItemStack stack = fit(offer.roll(random), manifest, target);
            if (stack.isEmpty()) {
                continue;
            }
            manifest.add(stack);
            long line = lineValue(stack, offer.valueScale());
            value[0] += line;
            if (isValuable(offer)) {
                valuableValue[0] += line;
                valuable[0] = true;
            }
        }
    }

    private static void trimToSize(List<ItemStack> manifest, CargoSize target, long[] value) {
        while (!manifest.isEmpty()) {
            Optional<CargoSize> actual = CargoSize.smallestFor(manifest.size(), Weights.of(manifest));
            if (actual.isPresent() && actual.get().ordinal() <= target.ordinal()) {
                return;
            }
            ItemStack removed = manifest.remove(manifest.size() - 1);
            value[0] = Math.max(0L, value[0] - lineValue(removed, 1.0));
        }
    }

    private static boolean canGrow(List<ItemStack> manifest, CargoSize target) {
        return manifest.size() < target.slots() && Weights.of(manifest) < target.maxWeight();
    }

    private static ItemStack fit(ItemStack stack, List<ItemStack> manifest, CargoSize target) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (fits(manifest, stack, target)) {
            return stack;
        }
        int count = stack.getCount();
        while (count > 0) {
            ItemStack next = stack.copyWithCount(count);
            if (fits(manifest, next, target)) {
                return next;
            }
            count--;
        }
        return ItemStack.EMPTY;
    }

    private static boolean fits(List<ItemStack> manifest, ItemStack extra, CargoSize target) {
        int stacks = manifest.size() + 1;
        double weight = Weights.of(manifest) + Weights.perItem(extra) * extra.getCount();
        return stacks <= target.slots() && weight <= target.maxWeight();
    }

    private static Offer pick(List<Offer> pool, RandomSource random) {
        int total = 0;
        for (Offer offer : pool) {
            total += offer.selectionWeight();
        }
        if (total <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        int pick = random.nextInt(total);
        for (Offer offer : pool) {
            pick -= offer.selectionWeight();
            if (pick < 0) {
                return offer;
            }
        }
        return pool.get(0);
    }

    private static long lineValue(ItemStack stack, double scale) {
        if (stack.isEmpty()) {
            return 0L;
        }
        return Math.round(ItemValues.perItem(stack) * (double) stack.getCount() * Math.max(0.0, scale));
    }

    private static long valueOf(List<ItemStack> manifest, double scale) {
        long total = 0L;
        for (ItemStack stack : manifest) {
            total += lineValue(stack, scale);
        }
        return total;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return copy;
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
            offers.add(parseOffer(item.get(), row));
        });
        return List.copyOf(offers);
    }

    private static Offer parseOffer(Item item, JsonObject row) {
        int min = GsonHelper.getAsInt(row, "min", 1);
        int max = GsonHelper.getAsInt(row, "max", 16);
        int stacksMin = GsonHelper.getAsInt(row, "stacks_min", 1);
        int stacksMax = GsonHelper.getAsInt(row, "stacks_max", 1);
        int pickWeight = GsonHelper.getAsInt(row, "weight", 1);
        boolean valuable = GsonHelper.getAsBoolean(row, "valuable", false);
        double multiplier = GsonHelper.getAsDouble(row, "value_multiplier", valuable ? 0.0 : 1.0);
        CargoSize minSize = CargoSize.S;
        if (row.has("min_size")) {
            try {
                minSize = CargoSize.valueOf(GsonHelper.getAsString(row, "min_size").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                minSize = CargoSize.S;
            }
        }
        return new Offer(item, min, max, stacksMin, stacksMax, pickWeight, valuable, multiplier, minSize);
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
        Offer offer = Offer.of(resolved.get(), GsonHelper.getAsInt(item, "min", 1), GsonHelper.getAsInt(item, "max", 1));
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
