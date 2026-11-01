package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Own creative tab (0.3.2). Station appears only when the player has operator-tab permissions. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeronauticsCouriers.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aerocouriers.main"))
                    .icon(() -> new ItemStack(ModItems.TRANSPORT_WAYBILL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TRANSPORT_WAYBILL.get());
                        output.accept(ModItems.ORDER_TABLET.get());
                        output.accept(ModItems.PILOT_LICENSE.get());
                        if (parameters.hasPermissions()) {
                            output.accept(ModItems.POST_STATION.get());
                        }
                        output.accept(ModItems.DECOR_CARGO_CONTAINER_S.get());
                        output.accept(ModItems.DECOR_CARGO_CONTAINER_M.get());
                        output.accept(ModItems.DECOR_CARGO_CONTAINER_L.get());
                        output.accept(ModItems.DECOR_CARGO_CONTAINER_XL.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
