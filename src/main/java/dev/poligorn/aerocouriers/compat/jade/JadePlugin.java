package dev.poligorn.aerocouriers.compat.jade;

import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade 15.10.6 plugin. Loaded only by Jade via {@link WailaPlugin}; the rest of the mod never references this class. */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // Class<?> matches the looked-at block, so every S/M/L/XL segment (not only the controller BE) is covered.
        registration.registerBlockDataProvider(CargoContainerJadeProvider.INSTANCE, CargoContainerBlock.class);
        registration.registerBlockDataProvider(PostStationJadeProvider.INSTANCE, PostStationBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CargoContainerJadeProvider.INSTANCE, CargoContainerBlock.class);
        registration.registerBlockComponent(PostStationJadeProvider.INSTANCE, PostStationBlock.class);
    }
}
