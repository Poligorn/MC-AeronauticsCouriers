package dev.poligorn.aerocouriers.item;

import dev.poligorn.aerocouriers.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Legacy {@code aerocouriers:courier_stamp}. Kept registered so old worlds do not break; stacks migrate to
 * {@link TransportWaybillItem} on use or inventory tick.
 */
public class CourierStampItem extends Item {
    public CourierStampItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        if (level.isClientSide || stack.isEmpty() || !(entity instanceof Player player)) {
            return;
        }
        migrateStack(player, slotId, stack.getCount());
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        int count = context.getItemInHand().getCount();
        player.setItemInHand(context.getHand(), Waybills.stack(count));
        return ModItems.TRANSPORT_WAYBILL.get().useOn(context);
    }

    private static void migrateStack(Player player, int slotId, int count) {
        if (slotId < 0) {
            return;
        }
        player.getInventory().setItem(slotId, Waybills.stack(count));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerocouriers.courier_stamp.legacy").withStyle(ChatFormatting.GRAY));
    }
}
