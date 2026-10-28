package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Container menus for a later Create-styled window (GDD §10.2, 27 cargo slots). The testing board is a
 * {@code Screen} plus packets, not a {@code Menu}.
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, AeronauticsCouriers.MOD_ID);

    private ModMenus() {
    }
}
