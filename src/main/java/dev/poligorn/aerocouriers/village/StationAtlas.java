package dev.poligorn.aerocouriers.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.license.Licenses;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every post station in the world and who has discovered it (GDD §6.1). Stations register themselves when their
 * block entity loads; a licensed player discovers one by coming within {@code stations.register_radius}. On a
 * server the atlas is shared, but the board will only offer contracts between stations the viewer discovered.
 */
public final class StationAtlas extends SavedData {
    private static final String NAME = "aerocouriers_stations";
    private static final int TICK_INTERVAL = 20;

    public record Station(
            UUID id,
            String name,
            ResourceKey<Level> dimension,
            BlockPos pos,
            Set<UUID> discoveredBy,
            int reputation
    ) {
        static final Codec<Station> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Station::id),
                Codec.STRING.fieldOf("name").forGetter(Station::name),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Station::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Station::pos),
                UUIDUtil.STRING_CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                        .optionalFieldOf("discovered_by", Set.of()).forGetter(Station::discoveredBy),
                Codec.INT.optionalFieldOf("reputation", 0).forGetter(Station::reputation)
        ).apply(instance, Station::new));

        public boolean discoveredBy(UUID player) {
            return discoveredBy.contains(player);
        }
    }

    private final Map<UUID, Station> stations = new LinkedHashMap<>();

    public static StationAtlas get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StationAtlas::new, StationAtlas::load, null), NAME);
    }

    public Collection<Station> all() {
        return List.copyOf(stations.values());
    }

    public Optional<Station> byId(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    public Optional<Station> byPos(ResourceKey<Level> dimension, BlockPos pos) {
        return stations.values().stream()
                .filter(s -> s.dimension().equals(dimension) && s.pos().equals(pos))
                .findFirst();
    }

    public Set<String> names() {
        return stations.values().stream().map(Station::name).collect(Collectors.toSet());
    }

    public boolean hasStationNear(ResourceKey<Level> dimension, BlockPos pos, double distance) {
        return stations.values().stream()
                .anyMatch(s -> s.dimension().equals(dimension) && s.pos().closerThan(pos, distance));
    }

    /** Adds the station or updates its name and position, keeping who discovered it. */
    public void register(UUID id, String name, ResourceKey<Level> dimension, BlockPos pos) {
        Station old = stations.get(id);
        Set<UUID> discovered = old == null ? Set.of() : old.discoveredBy();
        int reputation = old == null ? 0 : old.reputation();
        Station updated = new Station(id, name, dimension, pos.immutable(), discovered, reputation);
        if (!updated.equals(old)) {
            stations.put(id, updated);
            setDirty();
            if (old == null) {
                AeronauticsCouriers.LOGGER.info("[station] Registered «{}» {} at {} {}", name, id, dimension.location(),
                        pos.toShortString());
            }
        }
    }

    public void remove(UUID id) {
        Station removed = stations.remove(id);
        if (removed != null) {
            setDirty();
            AeronauticsCouriers.LOGGER.info("[station] Removed «{}» {}", removed.name(), id);
        }
    }

    /** @return {@code true} if this is a new discovery for the player */
    public boolean discover(UUID station, UUID player) {
        Station s = stations.get(station);
        if (s == null || s.discoveredBy(player)) {
            return false;
        }
        Set<UUID> discovered = new HashSet<>(s.discoveredBy());
        discovered.add(player);
        stations.put(station, new Station(s.id(), s.name(), s.dimension(), s.pos(), Set.copyOf(discovered), s.reputation()));
        setDirty();
        return true;
    }

    public int reputation(UUID stationId) {
        Station s = stations.get(stationId);
        return s == null ? 0 : s.reputation();
    }

    public void adjustReputation(UUID stationId, int delta) {
        Station s = stations.get(stationId);
        if (s == null || delta == 0) {
            return;
        }
        stations.put(stationId, new Station(s.id(), s.name(), s.dimension(), s.pos(), s.discoveredBy(),
                s.reputation() + delta));
        setDirty();
    }

    public void setReputation(UUID stationId, int value) {
        Station s = stations.get(stationId);
        if (s == null) {
            return;
        }
        stations.put(stationId, new Station(s.id(), s.name(), s.dimension(), s.pos(), s.discoveredBy(), value));
        setDirty();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK_INTERVAL == 0) {
            get(server).discoverNearby(server);
        }
    }

    private void discoverNearby(MinecraftServer server) {
        if (stations.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            discoverNearby(player);
        }
    }

    /** @return how many stations the player discovered just now */
    public int discoverNearby(ServerPlayer player) {
        if (player.isSpectator() || !Licenses.isLicensedCourier(player)) {
            return 0;
        }
        double radius = CouriersServerConfig.STATION_REGISTER_RADIUS.get();
        int found = 0;
        for (Station station : List.copyOf(stations.values())) {
            if (station.dimension().equals(player.level().dimension())
                    && player.position().closerThan(station.pos().getCenter(), radius)
                    && discover(station.id(), player.getUUID())) {
                player.sendSystemMessage(Component.translatable("station.aerocouriers.discovered", station.name()));
                found++;
            }
        }
        return found;
    }

    public static StationAtlas load(CompoundTag tag, HolderLookup.Provider registries) {
        StationAtlas atlas = new StationAtlas();
        Station.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("stations"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Station atlas: {}", error))
                .ifPresent(list -> list.forEach(s -> atlas.stations.put(s.id(), s)));
        return atlas;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("stations", Station.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(stations.values()))
                .getOrThrow());
        return tag;
    }
}
