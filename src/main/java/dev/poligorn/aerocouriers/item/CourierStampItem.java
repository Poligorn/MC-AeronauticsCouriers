package dev.poligorn.aerocouriers.item;

import dev.poligorn.aerocouriers.contract.CargoContainers;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.net.BoardPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.ChatFormatting;
import java.util.List;

/**
 * {@code aerocouriers:courier_stamp}: the only player-facing way to post a P2P order. Right-click any inventory
 * the player may open; the server then sends the stamp screen. The item is not consumed.
 */
public class CourierStampItem extends Item {
    public CourierStampItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!Licenses.isLicensedCourier(player)) {
            player.sendSystemMessage(Component.translatable("contract.aerocouriers.error.no_license"));
            return InteractionResult.FAIL;
        }
        if (!CargoContainers.canOpen(player, level, context.getClickedPos())) {
            player.sendSystemMessage(Component.translatable("contract.aerocouriers.error.no_access",
                    context.getClickedPos().toShortString()));
            return InteractionResult.FAIL;
        }
        if (CargoContainers.find(level, context.getClickedPos()).isEmpty()) {
            player.sendSystemMessage(Component.translatable("contract.aerocouriers.error.no_container",
                    context.getClickedPos().toShortString()));
            return InteractionResult.FAIL;
        }
        BoardPackets.openStamp(player, context.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerocouriers.courier_stamp.hint").withStyle(ChatFormatting.GRAY));
    }
}
