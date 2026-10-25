package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Items and money held for a contract (GDD §7.1, §13): the manifest, the issuer's reward and the courier's
 * collateral. Containers in the world are empty shells, so the cargo exists exactly once — here.
 */
public final class Escrow extends SavedData {
    private static final String NAME = "aerocouriers_escrow";

    public record Holding(List<ItemStack> items, long reward, long collateral) {
        static final Codec<Holding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Holding::items),
                Codec.LONG.optionalFieldOf("reward", 0L).forGetter(Holding::reward),
                Codec.LONG.optionalFieldOf("collateral", 0L).forGetter(Holding::collateral)
        ).apply(instance, Holding::new));

        static final Holding EMPTY = new Holding(List.of(), 0, 0);

        public boolean isEmpty() {
            return items.isEmpty() && reward == 0 && collateral == 0;
        }
    }

    private static final Codec<Map<UUID, Holding>> HOLDINGS_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Holding.CODEC);

    private final Map<UUID, Holding> holdings = new HashMap<>();

    public static Escrow get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Escrow::new, Escrow::load, null), NAME);
    }

    public static Escrow load(CompoundTag tag, HolderLookup.Provider registries) {
        Escrow escrow = new Escrow();
        HOLDINGS_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("holdings"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Escrow: {}", error))
                .ifPresent(escrow.holdings::putAll);
        return escrow;
    }

    public Holding holding(UUID contractId) {
        return holdings.getOrDefault(contractId, Holding.EMPTY);
    }

    void open(UUID contractId, List<ItemStack> items, long reward) {
        if (holdings.containsKey(contractId)) {
            throw new IllegalStateException("Escrow already open for " + contractId);
        }
        holdings.put(contractId, new Holding(copy(items), reward, 0));
        setDirty();
    }

    void addCollateral(UUID contractId, long collateral) {
        Holding h = holding(contractId);
        put(contractId, new Holding(h.items(), h.reward(), h.collateral() + collateral));
    }

    long takeReward(UUID contractId) {
        Holding h = holding(contractId);
        put(contractId, new Holding(h.items(), 0, h.collateral()));
        return h.reward();
    }

    long takeCollateral(UUID contractId) {
        Holding h = holding(contractId);
        put(contractId, new Holding(h.items(), h.reward(), 0));
        return h.collateral();
    }

    List<ItemStack> takeItems(UUID contractId) {
        Holding h = holding(contractId);
        put(contractId, new Holding(List.of(), h.reward(), h.collateral()));
        return copy(h.items());
    }

    private void put(UUID contractId, Holding holding) {
        if (holding.isEmpty()) {
            holdings.remove(contractId);
        } else {
            holdings.put(contractId, holding);
        }
        setDirty();
    }

    private static List<ItemStack> copy(List<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        return List.copyOf(copy);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("holdings", HOLDINGS_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), holdings)
                .getOrThrow());
        return tag;
    }
}
