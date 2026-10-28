package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.item.CourierStampItem;
import dev.poligorn.aerocouriers.item.OrderTabletItem;
import dev.poligorn.aerocouriers.item.TransportWaybillItem;
import dev.poligorn.aerocouriers.license.PilotLicenseItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(AeronauticsCouriers.MOD_ID);

    public static final DeferredItem<PilotLicenseItem> PILOT_LICENSE = REGISTER.registerItem(
            "pilot_license",
            PilotLicenseItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<CourierStampItem> COURIER_STAMP = REGISTER.registerItem(
            "courier_stamp",
            CourierStampItem::new,
            new Item.Properties().stacksTo(16));

    public static final DeferredItem<TransportWaybillItem> TRANSPORT_WAYBILL = REGISTER.registerItem(
            "transport_waybill",
            TransportWaybillItem::new,
            new Item.Properties().stacksTo(16));

    public static final DeferredItem<OrderTabletItem> ORDER_TABLET = REGISTER.registerItem(
            "order_tablet",
            OrderTabletItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    // Operators only (creative "Operator Utilities" tab or /give); no recipe (GDD §6.0).
    public static final DeferredItem<BlockItem> POST_STATION = REGISTER.registerSimpleBlockItem(
            "post_station", ModBlocks.POST_STATION, new Item.Properties().rarity(Rarity.EPIC));

    private ModItems() {
    }
}
