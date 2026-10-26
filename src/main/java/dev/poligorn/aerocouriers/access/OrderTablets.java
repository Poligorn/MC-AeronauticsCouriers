package dev.poligorn.aerocouriers.access;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Order tablet possession for board access (GDD §10.1). Worn in Curios {@link #CURIOS_SLOT} or held in either hand.
 */
public final class OrderTablets {
    public static final String CURIOS_SLOT = "order_tablet";

    private OrderTablets() {
    }

    public static boolean hasTablet(Player player) {
        if (player.getMainHandItem().is(ModItems.ORDER_TABLET.get())
                || player.getOffhandItem().is(ModItems.ORDER_TABLET.get())) {
            return true;
        }
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.findFirstCurio(stack -> stack.is(ModItems.ORDER_TABLET.get())))
                .isPresent();
    }

    /** Server gate for opening the job board (stations, J, commands, tablet use). */
    public static boolean canAccessBoard(ServerPlayer player) {
        if (CouriersServerConfig.SPEC.isLoaded() && !CouriersServerConfig.REQUIRE_TABLET.get()) {
            return true;
        }
        return hasTablet(player);
    }
}
