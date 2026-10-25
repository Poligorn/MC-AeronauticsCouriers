package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod(value = AeronauticsCouriers.MOD_ID, dist = Dist.CLIENT)
public final class AeroCouriersClient {

    public AeroCouriersClient(IEventBus modBus) {
        modBus.addListener(AeroCouriersClient::onClientSetup);
        modBus.addListener(BoardKeyMappings::register);
        NeoForge.EVENT_BUS.addListener(BoardKeyMappings::onClientTick);
        NeoForge.EVENT_BUS.addListener(XaeroMapPicker::onClientTick);
        NeoForge.EVENT_BUS.addListener(InventoryBoardButton::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ZoneParticles::onClientTick);
        NeoForge.EVENT_BUS.addListener(ZoneParticles::onLoggingOut);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CuriosRendererRegistry.register(ModItems.PILOT_LICENSE.get(), EpauletteRenderer::new);
            // Item icon per rank: models/item/pilot_license.json overrides on aerocouriers:rank (= level, admin = 0).
            ItemProperties.register(ModItems.PILOT_LICENSE.get(), AeronauticsCouriers.id("rank"),
                    (stack, level, entity, seed) ->
                            stack.getOrDefault(ModComponents.LICENSE_RANK.get(), LicenseRank.RANK1).level() / 10f);
        });
    }
}
