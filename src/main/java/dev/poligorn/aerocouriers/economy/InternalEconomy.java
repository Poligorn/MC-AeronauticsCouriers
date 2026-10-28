package dev.poligorn.aerocouriers.economy;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The mod's own balance, used when Numismatics is not installed (GDD §11.1). */
public final class InternalEconomy extends SavedData implements Economy {
    private static final String NAME = "aerocouriers_balances";
    private static final Codec<Map<UUID, Long>> BALANCES_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);

    private final Map<UUID, Long> balances = new HashMap<>();

    public static InternalEconomy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(InternalEconomy::new, InternalEconomy::load, null), NAME);
    }

    private static InternalEconomy load(CompoundTag tag, HolderLookup.Provider registries) {
        InternalEconomy economy = new InternalEconomy();
        BALANCES_CODEC.parse(NbtOps.INSTANCE, tag.get("balances")).result().ifPresent(economy.balances::putAll);
        return economy;
    }

    @Override
    public long balance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    @Override
    public boolean withdraw(UUID player, long spurs, String reason) {
        if (spurs < 0) {
            throw new IllegalArgumentException("Negative withdrawal: " + spurs);
        }
        long current = balance(player);
        if (current < spurs) {
            return false;
        }
        balances.put(player, current - spurs);
        setDirty();
        return true;
    }

    @Override
    public void deposit(UUID player, long spurs, String reason) {
        if (spurs < 0) {
            throw new IllegalArgumentException("Negative deposit: " + spurs);
        }
        balances.merge(player, spurs, Math::addExact);
        setDirty();
    }

    /** Operator tool. */
    public void set(UUID player, long spurs) {
        balances.put(player, Math.max(0, spurs));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("balances", BALANCES_CODEC.encodeStart(NbtOps.INSTANCE, balances).getOrThrow());
        return tag;
    }
}
