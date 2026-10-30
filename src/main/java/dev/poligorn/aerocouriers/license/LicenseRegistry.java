package dev.poligorn.aerocouriers.license;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide license number counter, stored on the overworld.
 */
public final class LicenseRegistry extends SavedData {
    private static final String NAME = "aerocouriers_licenses";

    private int nextNumber = 1;
    /** Only this number is valid for the owner after a restore; owners without an entry accept any own license. */
    private final Map<UUID, Integer> current = new HashMap<>();

    public static LicenseRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LicenseRegistry::new, LicenseRegistry::load, null), NAME);
    }

    private static LicenseRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        LicenseRegistry registry = new LicenseRegistry();
        registry.nextNumber = Math.max(1, tag.getInt("next_number"));
        CompoundTag valid = tag.getCompound("current");
        for (String key : valid.getAllKeys()) {
            try {
                registry.current.put(UUID.fromString(key), valid.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // skip malformed keys
            }
        }
        return registry;
    }

    public int issueNumber() {
        setDirty();
        return nextNumber++;
    }

    /** Makes {@code number} the only valid license of {@code owner}; older numbers are annulled. */
    public void setCurrent(UUID owner, int number) {
        current.put(owner, number);
        setDirty();
    }

    public boolean isValid(UUID owner, int number) {
        Integer valid = current.get(owner);
        return valid == null || valid == number;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_number", nextNumber);
        CompoundTag valid = new CompoundTag();
        current.forEach((owner, number) -> valid.putInt(owner.toString(), number));
        tag.put("current", valid);
        return tag;
    }
}
