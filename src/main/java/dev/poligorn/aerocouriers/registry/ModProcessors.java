package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.village.OncePerVillageStationProcessor;
import dev.poligorn.aerocouriers.village.VillageTownCenterStationProcessor;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModProcessors {
    public static final DeferredRegister<StructureProcessorType<?>> REGISTER =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, AeronauticsCouriers.MOD_ID);

    public static final Supplier<StructureProcessorType<OncePerVillageStationProcessor>> ONCE_PER_VILLAGE_STATION =
            REGISTER.register("once_per_village_station", () -> () -> OncePerVillageStationProcessor.CODEC);
    public static final Supplier<StructureProcessorType<VillageTownCenterStationProcessor>> TOWN_CENTER_STATION =
            REGISTER.register("town_center_station", () -> () -> VillageTownCenterStationProcessor.CODEC);

    private ModProcessors() {
    }
}
