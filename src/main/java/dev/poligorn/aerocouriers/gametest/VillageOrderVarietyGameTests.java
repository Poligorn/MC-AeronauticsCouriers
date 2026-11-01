package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.Weights;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import dev.poligorn.aerocouriers.village.VillageCargo;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Normal village-order mix: size by reputation tier, manifests that need that size, expensive share, ≥1 S.
 */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageOrderVarietyGameTests {
    private static final String BATCH = "aerocouriers.village.variety";
    private static final ResourceLocation PLAINS = ResourceLocation.withDefaultNamespace("plains");
    private static final ResourceLocation DESERT = ResourceLocation.withDefaultNamespace("desert");
    private static final int SAMPLES = 80;
    private static final int EXPENSIVE_SAMPLES = 240;
    private static final double[] DISTANCES = {180, 520, 1400, 3200, 5600, 8200};

    private VillageOrderVarietyGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void tier0OnlyRollsSmall(GameTestHelper helper) {
        assertAllowedSizes(helper, 0, EnumSet.of(CargoSize.S), 11L);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void tier1RollsSmallAndMedium(GameTestHelper helper) {
        assertAllowedSizes(helper, 10, EnumSet.of(CargoSize.S, CargoSize.M), 21L);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void tier2RollsUpToLarge(GameTestHelper helper) {
        assertAllowedSizes(helper, 25, EnumSet.of(CargoSize.S, CargoSize.M, CargoSize.L), 31L);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void tier3RollsAllSizes(GameTestHelper helper) {
        assertAllowedSizes(helper, 50, EnumSet.allOf(CargoSize.class), 41L);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void manifestNeedsRolledSize(GameTestHelper helper) {
        VillageBoardPolicy policy = VillageBoardPolicy.DEFAULT;
        for (CargoSize target : CargoSize.values()) {
            int seen = 0;
            for (int i = 0; i < SAMPLES; i++) {
                VillageCargo.ManifestRoll rolled = VillageCargo.rollManifest(
                        PLAINS, DESERT, 50, policy, Optional.of(target), enchantments(helper),
                        RandomSource.create(1000L + target.ordinal() * 100L + i));
                helper.assertFalse(rolled.stacks().isEmpty(), target + " roll " + i + " empty");
                helper.assertTrue(VillageCargo.matchesSize(rolled.stacks(), target),
                        target + " roll " + i + " needs " + actualSize(rolled.stacks())
                                + " stacks=" + rolled.stacks().size()
                                + " W=" + Weights.of(rolled.stacks()));
                seen++;
            }
            helper.assertTrue(seen == SAMPLES, target + " samples");
        }
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void expensiveShareAtMidAndTopTiers(GameTestHelper helper) {
        Count mid = expensiveCount(helper, 25, 2000L);
        Count top = expensiveCount(helper, 50, 3000L);
        AeronauticsCouriers.LOGGER.info("village variety expensive mid={} top={} midBySize={} topBySize={}",
                mid.summary(), top.summary(), mid.bySize(), top.bySize());
        assertShare(helper, "mid tier (rep 25) " + mid.bySize() + " | top was " + top.summary(),
                mid.expensive, EXPENSIVE_SAMPLES, 0.10, 0.15);
        assertShare(helper, "top tier (rep 50) " + top.bySize() + " | mid was " + mid.summary(),
                top.expensive, EXPENSIVE_SAMPLES, 0.10, 0.15);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH + ".generate", timeoutTicks = 200)
    public static void refreshKeepsAtLeastOneSmall(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "refresh");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    f.clearVillageOrders();
                    StationAtlas atlas = StationAtlas.get(f.server());
                    StationAtlas.Station from = f.register("from", helper.absolutePos(new BlockPos(2, 1, 2)), 50);
                    int[] offsets = {80, 240, 900, 1800, 3200, 4800, 6400, 8000};
                    for (int i = 0; i < offsets.length; i++) {
                        f.register("to" + i, from.pos().offset(offsets[i], 0, i * 16), 0);
                    }
                    int previousMax = CouriersServerConfig.VILLAGE_BOARD_MAX.get();
                    CouriersServerConfig.VILLAGE_BOARD_MAX.set(12);
                    int created = StationContractSource.INSTANCE.generate(f.server());
                    CouriersServerConfig.VILLAGE_BOARD_MAX.set(previousMax);
                    helper.assertTrue(created >= 1, "refresh published orders, got " + created);
                    long small = atlasOwnedSmall(f);
                    helper.assertTrue(small >= 1, "refresh must keep at least one S, small=" + small
                            + " created=" + created + " sizes=" + atlasOwnedSizes(f));
                    f.cleanup();
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void policyAllowsSizeIsRespected(GameTestHelper helper) {
        VillageBoardPolicy onlySmall = VillageBoardPolicy.DEFAULT.withSizes(VillageBoardPolicy.maskOf(CargoSize.S));
        EnumSet<CargoSize> seen = EnumSet.noneOf(CargoSize.class);
        for (int i = 0; i < SAMPLES; i++) {
            Optional<CargoSize> size = VillageCargo.rollTargetSize(50, onlySmall, RandomSource.create(77L + i));
            helper.assertTrue(size.isPresent() && size.get() == CargoSize.S, "policy S-only roll " + i + "=" + size);
            seen.add(size.orElse(CargoSize.S));
        }
        helper.assertTrue(seen.equals(EnumSet.of(CargoSize.S)), "only S: " + seen);
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = BATCH)
    public static void rareCombinesWithSize(GameTestHelper helper) {
        int rare = 0;
        int rareKeptSize = 0;
        for (int i = 0; i < SAMPLES; i++) {
            VillageCargo.ManifestRoll rolled = VillageCargo.rollManifest(
                    PLAINS, DESERT, 50, VillageBoardPolicy.DEFAULT, Optional.empty(),
                    enchantments(helper), RandomSource.create(9000L + i));
            helper.assertFalse(rolled.stacks().isEmpty(), "rare combo empty " + i);
            CargoSize size = actualSize(rolled.stacks());
            helper.assertTrue(size != null, "rare combo has size " + i);
            if (rolled.rare()) {
                rare++;
                if (VillageCargo.matchesSize(rolled.stacks(), size)) {
                    rareKeptSize++;
                }
            }
        }
        helper.assertTrue(rare > 0, "tier 3 should still roll rare cargo, got 0/" + SAMPLES);
        helper.assertValueEqual(rareKeptSize, rare, "rare stacks still need their container size");
        int none = 0;
        for (int i = 0; i < 40; i++) {
            if (VillageCargo.rollManifest(PLAINS, DESERT, 0, VillageBoardPolicy.DEFAULT, Optional.empty(),
                    enchantments(helper), RandomSource.create(91L + i)).rare()) {
                none++;
            }
        }
        helper.assertValueEqual(none, 0, "tier 0 never injects rare");
        helper.succeed();
    }

    private static void assertAllowedSizes(GameTestHelper helper, int reputation, EnumSet<CargoSize> allowed,
                                           long seed) {
        helper.assertValueEqual(VillageCargo.maxSizeForTier(
                        reputation < 10 ? 0 : reputation < 25 ? 1 : reputation < 50 ? 2 : 3),
                allowed.stream().max(Enum::compareTo).orElse(CargoSize.S), "max size for rep " + reputation);
        EnumMap<CargoSize, Integer> counts = new EnumMap<>(CargoSize.class);
        for (CargoSize size : CargoSize.values()) {
            counts.put(size, 0);
        }
        for (int i = 0; i < SAMPLES; i++) {
            Optional<CargoSize> rolled = VillageCargo.rollTargetSize(reputation, VillageBoardPolicy.DEFAULT,
                    RandomSource.create(seed + i));
            helper.assertTrue(rolled.isPresent(), "size roll " + i);
            helper.assertTrue(allowed.contains(rolled.get()),
                    "rep " + reputation + " rolled forbidden " + rolled.get());
            counts.merge(rolled.get(), 1, Integer::sum);
        }
        for (CargoSize size : allowed) {
            helper.assertTrue(counts.get(size) > 0,
                    "rep " + reputation + " never rolled " + size + " in " + SAMPLES + ": " + counts);
        }
    }

    private static Count expensiveCount(GameTestHelper helper, int reputation, long seed) {
        Count count = new Count();
        for (int i = 0; i < EXPENSIVE_SAMPLES; i++) {
            double distance = DISTANCES[i % DISTANCES.length];
            RandomSource random = RandomSource.create(seed + i);
            VillageCargo.ManifestRoll rolled = VillageCargo.rollManifest(
                    PLAINS, DESERT, reputation, VillageBoardPolicy.DEFAULT, Optional.empty(),
                    enchantments(helper), random);
            if (rolled.stacks().isEmpty()) {
                continue;
            }
            CargoSize size = actualSize(rolled.stacks());
            if (size == null) {
                continue;
            }
            count.seen.merge(size, 1, Integer::sum);
            long reward = Pricing.npcReward(distance, Weights.of(rolled.stacks()), rolled.valueSpurs(),
                    rolled.valuableSpurs(), size, random);
            if (MoneyScale.toSpurs(reward) >= VillageBoardPolicy.EXPENSIVE_REWARD) {
                count.expensive++;
                count.hits.merge(size, 1, Integer::sum);
            }
        }
        return count;
    }

    private static final class Count {
        int expensive;
        final EnumMap<CargoSize, Integer> seen = new EnumMap<>(CargoSize.class);
        final EnumMap<CargoSize, Integer> hits = new EnumMap<>(CargoSize.class);

        String summary() {
            return expensive + "/" + EXPENSIVE_SAMPLES
                    + " = " + String.format("%.3f", expensive / (double) EXPENSIVE_SAMPLES);
        }

        String bySize() {
            StringBuilder out = new StringBuilder();
            for (CargoSize size : CargoSize.values()) {
                int n = seen.getOrDefault(size, 0);
                int h = hits.getOrDefault(size, 0);
                if (n == 0 && h == 0) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(size).append('=').append(h).append('/').append(n);
            }
            return out.toString();
        }
    }

    private static void assertShare(GameTestHelper helper, String label, int hits, int total, double lo, double hi) {
        double share = hits / (double) total;
        helper.assertTrue(share + 1e-9 >= lo && share - 1e-9 <= hi,
                label + " expensive share " + hits + "/" + total + " = " + String.format("%.3f", share)
                        + " not in [" + lo + ", " + hi + "]");
    }

    private static CargoSize actualSize(List<ItemStack> stacks) {
        return CargoSize.smallestFor(stacks.size(), Weights.of(stacks)).orElse(null);
    }

    private static net.minecraft.core.HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment>
            enchantments(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    private static long atlasOwnedSmall(Fixture f) {
        return ContractManager.get(f.server()).open()
                .filter(c -> c.type() == ContractType.VILLAGE && f.owned.contains(c.issuer()) && c.size() == CargoSize.S)
                .count();
    }

    private static String atlasOwnedSizes(Fixture f) {
        StringBuilder out = new StringBuilder();
        ContractManager.get(f.server()).open()
                .filter(c -> c.type() == ContractType.VILLAGE && f.owned.contains(c.issuer()))
                .forEach(c -> out.append(c.size()).append(' '));
        return out.toString();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final java.util.Set<UUID> owned = new java.util.HashSet<>();
        private final String prefix;

        Fixture(GameTestHelper helper, String prefix) {
            this.helper = helper;
            this.prefix = prefix;
        }

        MinecraftServer server() {
            return helper.getLevel().getServer();
        }

        StationAtlas.Station register(String key, BlockPos pos, int reputation) {
            UUID id = UUID.nameUUIDFromBytes(("aerocouriers-variety:" + prefix + ":" + key)
                    .getBytes(StandardCharsets.UTF_8));
            StationAtlas atlas = StationAtlas.get(server());
            atlas.register(id, "Variety " + prefix + " " + key, helper.getLevel().dimension(), pos);
            if (reputation > 0) {
                atlas.setReputation(id, reputation);
            }
            owned.add(id);
            return atlas.byId(id).orElseThrow();
        }

        void clearVillageOrders() {
            ContractManager manager = ContractManager.get(server());
            for (Contract contract : List.copyOf(manager.all().toList())) {
                if (contract.type() == ContractType.VILLAGE && contract.state() == ContractState.OPEN) {
                    manager.adminCancelWithRefund(server(), contract.id());
                }
            }
        }

        void cleanup() {
            clearVillageOrders();
            StationAtlas atlas = StationAtlas.get(server());
            owned.forEach(atlas::remove);
        }
    }
}
