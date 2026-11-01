package dev.poligorn.aerocouriers.item;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.DecorCargoBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import java.util.List;

/**
 * Places a decorative container as one structure (M 1×1×2, L 2×2×2, XL 2×2×3). Refuses if any cell
 * is not replaceable. Footprint is the same +X/+Z as real cargo.
 */
public class DecorCargoBlockItem extends BlockItem {
    public DecorCargoBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public CargoSize size() {
        return getBlock() instanceof DecorCargoBlock decor ? decor.size() : CargoSize.S;
    }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (!DecorCargoBlock.canPlaceWhole(context.getLevel(), context.getClickedPos(), size(), context)) {
            return null;
        }
        return context;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.aerocouriers.decor_cargo_container.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
