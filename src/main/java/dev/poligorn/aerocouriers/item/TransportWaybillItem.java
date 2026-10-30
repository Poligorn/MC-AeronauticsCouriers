package dev.poligorn.aerocouriers.item;

import dev.poligorn.aerocouriers.contract.CargoContainers;
import dev.poligorn.aerocouriers.net.BoardPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

/**
 * {@code aerocouriers:transport_waybill}: single-use order form. Right-click a container to open the waybill
 * create screen; one waybill is consumed only after a successful publish on the server.
 */
public class TransportWaybillItem extends Item {
    public TransportWaybillItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
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
        BoardPackets.openWaybillCreate(player, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerocouriers.transport_waybill.hint").withStyle(ChatFormatting.GRAY));
    }
}
