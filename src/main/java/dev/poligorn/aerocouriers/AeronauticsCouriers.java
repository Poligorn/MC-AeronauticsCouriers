package dev.poligorn.aerocouriers;

import com.mojang.logging.LogUtils;
import dev.poligorn.aerocouriers.cargo.CargoEvents;
import dev.poligorn.aerocouriers.command.CouriersCommands;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.CollateralProfiles;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.CouriersNotifications;
import dev.poligorn.aerocouriers.contract.ItemValues;
import dev.poligorn.aerocouriers.contract.ItemWeights;
import dev.poligorn.aerocouriers.gametest.CouriersGameTests;
import dev.poligorn.aerocouriers.economy.EconomyLoginMigration;
import dev.poligorn.aerocouriers.license.LicenseMigration;
import dev.poligorn.aerocouriers.registry.ModAttachments;
import dev.poligorn.aerocouriers.registry.ModBlockEntities;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.registry.ModMenus;
import dev.poligorn.aerocouriers.registry.ModPayloads;
import dev.poligorn.aerocouriers.registry.ModProcessors;
import dev.poligorn.aerocouriers.registry.ModSounds;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import dev.poligorn.aerocouriers.village.StationNames;
import dev.poligorn.aerocouriers.village.VillageCargo;
import dev.poligorn.aerocouriers.village.VillageStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.slf4j.Logger;

@Mod(AeronauticsCouriers.MOD_ID)
public final class AeronauticsCouriers {
    public static final String MOD_ID = "aerocouriers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeronauticsCouriers(IEventBus modBus, ModContainer container) {
        ModComponents.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModProcessors.REGISTER.register(modBus);
        ModMenus.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);

        modBus.addListener(ModPayloads::register);
        modBus.addListener(AeronauticsCouriers::addCreativeTabContents);

        container.registerConfig(ModConfig.Type.SERVER, CouriersServerConfig.SPEC, MOD_ID + "-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CouriersClientConfig.SPEC, MOD_ID + "-client.toml");

        NeoForge.EVENT_BUS.addListener(CouriersCommands::register);
        NeoForge.EVENT_BUS.addListener(LicenseMigration::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(EconomyLoginMigration::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ContractManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CouriersNotifications::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CouriersNotifications::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(dev.poligorn.aerocouriers.task.TaskSync::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(dev.poligorn.aerocouriers.tutorial.Tutorials::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.poligorn.aerocouriers.task.TaskSync::onServerTick);
        NeoForge.EVENT_BUS.addListener(ContractManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(StationAtlas::onServerTick);
        NeoForge.EVENT_BUS.addListener(StationContractSource::onServerTick);
        NeoForge.EVENT_BUS.addListener(StationNames::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(VillageCargo::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(ItemValues::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(ItemWeights::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(VillageStructures::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(CollateralProfiles::onServerStarted);
        CargoEvents.register();
        if (GameTestHooks.isGametestEnabled()) {
            NeoForge.EVENT_BUS.addListener(CouriersGameTests::createEmptyTemplate);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.PILOT_LICENSE.get());
            event.accept(ModItems.TRANSPORT_WAYBILL.get());
            event.accept(ModItems.ORDER_TABLET.get());
        }
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            event.accept(ModItems.POST_STATION.get());
        }
    }
}
