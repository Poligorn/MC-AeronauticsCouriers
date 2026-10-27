package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractDistances;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.Weights;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NPC contracts between any two post stations in the atlas (GDD §6.2). Village-generated and admin-placed
 * stations are the same source — Ikaria has almost no villages, so admin stations must work.
 */
public final class StationContractSource implements ContractSource {
    public static final StationContractSource INSTANCE = new StationContractSource();
    public static final double MAX_PAIR_DISTANCE = 10_000;
    public static final int ZONE_RADIUS = 12;
    public static final int DEFAULT_DEADLINE_MINUTES = 60;

    private StationContractSource() {
    }

    @Override
    public ResourceLocation id() {
        return AeronauticsCouriers.id("post_stations");
    }

    @Override
    public void generate(ServerLevel level) {
        generate(level.getServer());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int interval = CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN.get() * 60 * 20;
        if (interval <= 0 || server.getTickCount() == 0 || server.getTickCount() % interval != 0) {
            return;
        }
        INSTANCE.generate(server);
    }

    /** Fills the board up to {@code village_board_max}; same-dimension pairs per level, then optional cross-dimension. */
    public int generate(MinecraftServer server) {
        int cap = CouriersServerConfig.VILLAGE_BOARD_MAX.get();
        ContractManager manager = ContractManager.get(server);
        int created = 0;
        for (ServerLevel level : server.getAllLevels()) {
            created += generateSameDimension(level, manager, cap - created, level.getRandom());
            if (created >= cap) {
                break;
            }
        }
        if (created < cap && CouriersServerConfig.ALLOW_CROSS_DIMENSION_ORDERS.get()) {
            created += generateCrossDimension(server, manager, cap - created, server.overworld().getRandom());
        }
        if (created > 0) {
            AeronauticsCouriers.LOGGER.info("[station] Published {} NPC contracts", created);
        }
        return created;
    }

    private int generateSameDimension(ServerLevel level, ContractManager manager, int remaining, RandomSource random) {
        if (remaining <= 0) {
            return 0;
        }
        long already = manager.all()
                .filter(c -> c.type() == ContractType.VILLAGE && c.state() == ContractState.OPEN)
                .count();
        int room = (int) Math.min(remaining, CouriersServerConfig.VILLAGE_BOARD_MAX.get() - already);
        if (room <= 0) {
            return 0;
        }
        List<StationAtlas.Station> stations = StationAtlas.get(level.getServer()).all().stream()
                .filter(s -> s.dimension().equals(level.dimension()))
                .toList();
        if (stations.size() < 2) {
            return 0;
        }
        List<StationAtlas.Station[]> pairs = new ArrayList<>();
        for (StationAtlas.Station from : stations) {
            for (StationAtlas.Station to : stations) {
                if (from.id().equals(to.id())) {
                    continue;
                }
                if (from.pos().distSqr(to.pos()) > MAX_PAIR_DISTANCE * MAX_PAIR_DISTANCE) {
                    continue;
                }
                if (hasOpenPair(manager, from, to)) {
                    continue;
                }
                pairs.add(new StationAtlas.Station[] {from, to});
            }
        }
        sortPairsForTrainee(pairs);
        Collections.shuffle(pairs, new java.util.Random(random.nextLong()));
        int created = 0;
        for (StationAtlas.Station[] pair : pairs) {
            if (created >= room) {
                break;
            }
            if (!level.isLoaded(pair[0].pos())) {
                continue;
            }
            if (publish(level, pair[0], pair[1], random).isPresent()) {
                created++;
            }
        }
        return created;
    }

    private int generateCrossDimension(MinecraftServer server, ContractManager manager, int remaining,
                                       RandomSource random) {
        if (remaining <= 0) {
            return 0;
        }
        double crossChance = CouriersServerConfig.CROSS_DIMENSION_ORDER_CHANCE.get();
        if (crossChance <= 0.0) {
            return 0;
        }
        long already = manager.all()
                .filter(c -> c.type() == ContractType.VILLAGE && c.state() == ContractState.OPEN)
                .count();
        int room = (int) Math.min(remaining, CouriersServerConfig.VILLAGE_BOARD_MAX.get() - already);
        if (room <= 0) {
            return 0;
        }
        List<StationAtlas.Station> stations = new ArrayList<>(StationAtlas.get(server).all());
        if (stations.size() < 2) {
            return 0;
        }
        List<StationAtlas.Station[]> pairs = new ArrayList<>();
        for (StationAtlas.Station from : stations) {
            for (StationAtlas.Station to : stations) {
                if (from.id().equals(to.id()) || from.dimension().equals(to.dimension())) {
                    continue;
                }
                if (random.nextDouble() >= crossChance) {
                    continue;
                }
                if (hasOpenPair(manager, from, to)) {
                    continue;
                }
                pairs.add(new StationAtlas.Station[] {from, to});
            }
        }
        Collections.shuffle(pairs, new java.util.Random(random.nextLong()));
        int created = 0;
        for (StationAtlas.Station[] pair : pairs) {
            if (created >= room) {
                break;
            }
            ServerLevel originLevel = server.getLevel(pair[0].dimension());
            if (originLevel == null || !originLevel.isLoaded(pair[0].pos())) {
                continue;
            }
            if (publish(originLevel, pair[0], pair[1], random).isPresent()) {
                created++;
            }
        }
        return created;
    }

    private static void sortPairsForTrainee(List<StationAtlas.Station[]> pairs) {
        int traineeMax = CouriersServerConfig.TRAINEE_MAX_DISTANCE.get();
        pairs.sort((a, b) -> {
            double da = Math.sqrt(a[0].pos().distSqr(a[1].pos()));
            double db = Math.sqrt(b[0].pos().distSqr(b[1].pos()));
            boolean shortA = traineeMax > 0 && da <= traineeMax;
            boolean shortB = traineeMax > 0 && db <= traineeMax;
            if (shortA != shortB) {
                return shortB ? 1 : -1;
            }
            return Double.compare(da, db);
        });
    }

    public static boolean hasOpenPair(ContractManager manager, StationAtlas.Station from, StationAtlas.Station to) {
        return manager.open()
                .filter(c -> c.type() == ContractType.VILLAGE)
                .anyMatch(c -> c.issuer().equals(from.id()) && c.recipient().equals(to.id()));
    }

    /**
     * Publishes one NPC order: cargo from the tables, a physical crate next to the origin station, reward minted
     * into escrow (GDD §11.2).
     */
    public static Optional<Contract> publish(ServerLevel level, StationAtlas.Station from, StationAtlas.Station to,
                                            RandomSource random) {
        StationAtlas atlas = StationAtlas.get(level.getServer());
        int originReputation = atlas.reputation(from.id());
        ServerLevel destinationLevel = level.getServer().getLevel(to.dimension());
        ResourceLocation destinationBiome = destinationLevel != null
                ? biomeId(destinationLevel, to.pos())
                : biomeId(level, to.pos());
        VillageCargo.ManifestRoll rolled = VillageCargo.rollManifest(
                biomeId(level, from.pos()),
                destinationBiome,
                originReputation,
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT),
                random);
        List<ItemStack> manifest = rolled.stacks();
        boolean villageRare = rolled.rare();
        double weight = Weights.of(manifest);
        Optional<CargoSize> size = CargoSize.smallestFor(manifest.size(), weight);
        if (size.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockPos> spot = CargoSpawner.findSpot(level, from.pos(),
                CouriersServerConfig.SPAWN_SEARCH_RADIUS.get(), size.get());
        if (spot.isEmpty()) {
            AeronauticsCouriers.LOGGER.debug("[station] No crate spot next to «{}»", from.name());
            return Optional.empty();
        }
        double distance = ContractDistances.pricingBlocks(
                new Destination(from.dimension(), from.pos(), ZONE_RADIUS),
                new Destination(to.dimension(), to.pos(), ZONE_RADIUS));
        long reward = Pricing.npcReward(distance, weight, random);
        long collateral = Pricing.npcCollateral(manifest, weight);
        return ContractManager.get(level.getServer()).publishVillage(level, from, to, spot.get(), manifest, size.get(),
                weight, reward, collateral, DEFAULT_DEADLINE_MINUTES, villageRare);
    }

    public static ResourceLocation biomeId(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.unwrapKey().map(key -> key.location()).orElse(ResourceLocation.withDefaultNamespace("plains"));
    }
}
