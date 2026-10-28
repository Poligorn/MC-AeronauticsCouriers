package dev.poligorn.aerocouriers.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Custom tooltip body listing spur denominations (header/footer are plain text lines). */
public record SpurBreakdownTooltip(long spurs) implements TooltipComponent {
}
