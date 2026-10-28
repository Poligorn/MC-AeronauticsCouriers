package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world item value/weight overrides (admin panel), applied over datapacks. */
public final class WorldCargoOverrides extends SavedData {
    private static final String NAME = "aerocouriers_cargo_overrides";

    private record Entry(@Nullable Long valueSpurs, @Nullable Double weight) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("value").forGetter(e -> Optional.ofNullable(e.valueSpurs)),
                Codec.DOUBLE.optionalFieldOf("weight").forGetter(e -> Optional.ofNullable(e.weight))
        ).apply(instance, (value, weight) -> new Entry(value.orElse(null), weight.orElse(null))));
    }

    private final Map<ResourceLocation, Entry> entries = new HashMap<>();

    public static WorldCargoOverrides get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldCargoOverrides::new, WorldCargoOverrides::load, null), NAME);
    }

    public java.util.OptionalLong valueOverride(ResourceLocation itemId) {
        Entry e = entries.get(itemId);
        return e == null || e.valueSpurs == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(e.valueSpurs);
    }

    public java.util.OptionalDouble weightOverride(ResourceLocation itemId) {
        Entry e = entries.get(itemId);
        return e == null || e.weight == null ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(e.weight);
    }

    public void setValue(ResourceLocation itemId, long spurs) {
        Entry old = entries.getOrDefault(itemId, new Entry(null, null));
        entries.put(itemId, new Entry(spurs, old.weight));
        setDirty();
    }

    public void setWeight(ResourceLocation itemId, double weight) {
        Entry old = entries.getOrDefault(itemId, new Entry(null, null));
        entries.put(itemId, new Entry(old.valueSpurs, weight));
        setDirty();
    }

    public static WorldCargoOverrides load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldCargoOverrides data = new WorldCargoOverrides();
        Codec<Map<ResourceLocation, Entry>> mapCodec = Codec.unboundedMap(ResourceLocation.CODEC, Entry.CODEC);
        mapCodec.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("items"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Cargo overrides: {}", error))
                .ifPresent(data.entries::putAll);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Codec<Map<ResourceLocation, Entry>> mapCodec = Codec.unboundedMap(ResourceLocation.CODEC, Entry.CODEC);
        tag.put("items", mapCodec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), entries)
                .getOrThrow());
        return tag;
    }
}
