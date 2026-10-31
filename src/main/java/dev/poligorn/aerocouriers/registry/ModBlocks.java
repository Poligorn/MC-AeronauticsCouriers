package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerLargeBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerMediumBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerXlBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(AeronauticsCouriers.MOD_ID);

    // No BlockItem on purpose: the container only ever appears through CargoSpawner (GDD §1, pillar 3).
    private static BlockBehaviour.Properties cargoContainerProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(50.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK);
    }

    public static final DeferredBlock<CargoContainerBlock> CARGO_CONTAINER_S = REGISTER.registerBlock(
            "cargo_container_s",
            props -> new CargoContainerBlock(CargoSize.S, props),
            cargoContainerProps());

    public static final DeferredBlock<CargoContainerMediumBlock> CARGO_CONTAINER_M = REGISTER.registerBlock(
            "cargo_container_m",
            CargoContainerMediumBlock::new,
            cargoContainerProps());

    public static final DeferredBlock<CargoContainerLargeBlock> CARGO_CONTAINER_L = REGISTER.registerBlock(
            "cargo_container_l",
            CargoContainerLargeBlock::new,
            cargoContainerProps());

    public static final DeferredBlock<CargoContainerXlBlock> CARGO_CONTAINER_XL = REGISTER.registerBlock(
            "cargo_container_xl",
            CargoContainerXlBlock::new,
            cargoContainerProps());

    public static final DeferredBlock<PostStationBlock> POST_STATION = REGISTER.registerBlock(
            "post_station",
            PostStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .lightLevel(state -> PostStationBlock.LIGHT_LEVEL)
                    .pushReaction(PushReaction.BLOCK));

    private ModBlocks() {
    }
}
