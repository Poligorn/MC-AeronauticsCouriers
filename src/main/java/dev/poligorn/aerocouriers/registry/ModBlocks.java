package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
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
    public static final DeferredBlock<CargoContainerBlock> CARGO_CONTAINER_S = REGISTER.registerBlock(
            "cargo_container_s",
            props -> new CargoContainerBlock(CargoSize.S, props),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    public static final DeferredBlock<PostStationBlock> POST_STATION = REGISTER.registerBlock(
            "post_station",
            PostStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    private ModBlocks() {
    }
}
