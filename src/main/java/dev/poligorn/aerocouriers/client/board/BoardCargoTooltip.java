package dev.poligorn.aerocouriers.client.board;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** S2C-safe cargo grid payload for board row crate hover (rendered client-side only). */
public record BoardCargoTooltip(List<ItemStack> items, int hiddenStacks, String weightLine) implements TooltipComponent {
    public static final int MAX_SHOWN = 20;
}
