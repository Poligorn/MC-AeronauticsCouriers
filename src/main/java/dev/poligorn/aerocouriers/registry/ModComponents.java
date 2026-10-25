package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.license.LicenseOwner;
import dev.poligorn.aerocouriers.license.LicenseRank;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AeronauticsCouriers.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LicenseOwner>> LICENSE_OWNER =
            REGISTER.registerComponentType("license_owner", builder -> builder
                    .persistent(LicenseOwner.CODEC)
                    .networkSynchronized(LicenseOwner.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LicenseRank>> LICENSE_RANK =
            REGISTER.registerComponentType("license_rank", builder -> builder
                    .persistent(LicenseRank.CODEC)
                    .networkSynchronized(LicenseRank.STREAM_CODEC));

    private ModComponents() {
    }
}
