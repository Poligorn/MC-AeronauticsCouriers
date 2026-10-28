package dev.poligorn.aerocouriers.license;

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

    public static LicenseRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LicenseRegistry::new, LicenseRegistry::load, null), NAME);
    }

    private static LicenseRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        LicenseRegistry registry = new LicenseRegistry();
        registry.nextNumber = Math.max(1, tag.getInt("next_number"));
        return registry;
    }

    public int issueNumber() {
        setDirty();
        return nextNumber++;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_number", nextNumber);
        return tag;
    }
}
