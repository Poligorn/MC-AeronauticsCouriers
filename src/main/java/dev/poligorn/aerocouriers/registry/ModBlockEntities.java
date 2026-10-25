package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeronauticsCouriers.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<CargoContainerBlockEntity>> CARGO_CONTAINER = REGISTER.register(
            "cargo_container",
            () -> BlockEntityType.Builder.of(CargoContainerBlockEntity::new, ModBlocks.CARGO_CONTAINER_S.get())
                    .build(null));

    private ModBlockEntities() {
    }
}
