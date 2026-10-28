package dev.poligorn.aerocouriers.item;

import dev.poligorn.aerocouriers.net.BoardPackets;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * {@code aerocouriers:order_tablet}: opens the job board on use. Flat item model for now;
 * 3D GeckoLib model will plug in via {@link dev.poligorn.aerocouriers.client.tablet.OrderTabletVisuals}.
 */
public class OrderTabletItem extends Item {
    public OrderTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BoardPackets.open(serverPlayer);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerocouriers.order_tablet.tooltip"));
    }
}
