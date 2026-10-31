package dev.poligorn.aerocouriers.compat.jade;

import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlockEntity;
import dev.poligorn.aerocouriers.cargo.CargoContainerLargeBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerMediumBlock;
import dev.poligorn.aerocouriers.cargo.CargoContainerXlBlock;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade 15.10.6 plugin. Loaded only by Jade via {@link WailaPlugin}; the rest of the mod never references this class. */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // BE for the controller; Block class for L/XL segments that have no block entity.
        registration.registerBlockDataProvider(CargoContainerJadeProvider.INSTANCE, CargoContainerBlockEntity.class);
        registration.registerBlockDataProvider(CargoContainerJadeProvider.INSTANCE, CargoContainerBlock.class);
        registration.registerBlockDataProvider(CargoContainerJadeProvider.INSTANCE, CargoContainerMediumBlock.class);
        registration.registerBlockDataProvider(CargoContainerJadeProvider.INSTANCE, CargoContainerLargeBlock.class);
        registration.registerBlockDataProvider(CargoContainerJadeProvider.INSTANCE, CargoContainerXlBlock.class);
        registration.registerBlockDataProvider(PostStationJadeProvider.INSTANCE, PostStationBlockEntity.class);
        registration.registerBlockDataProvider(PostStationJadeProvider.INSTANCE, PostStationBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CargoContainerJadeProvider.INSTANCE, CargoContainerBlock.class);
        registration.registerBlockComponent(CargoContainerJadeProvider.INSTANCE, CargoContainerMediumBlock.class);
        registration.registerBlockComponent(CargoContainerJadeProvider.INSTANCE, CargoContainerLargeBlock.class);
        registration.registerBlockComponent(CargoContainerJadeProvider.INSTANCE, CargoContainerXlBlock.class);
        registration.registerBlockComponent(PostStationJadeProvider.INSTANCE, PostStationBlock.class);
    }
}
