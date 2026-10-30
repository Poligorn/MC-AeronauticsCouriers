package dev.poligorn.aerocouriers.compat.jade;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.compat.JadeTooltipData;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.CargoSnapshot;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.CustomerKind;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.DisplayStatus;
import dev.poligorn.aerocouriers.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/** Cargo container tooltip: customer, short id, weight, status — same lines for every player. */
public enum CargoContainerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = AeronauticsCouriers.id("cargo_container");

    private static final int COLOR_PUBLISHED = 0xFFAA00;
    private static final int COLOR_IN_TRANSIT = 0x55FFFF;
    private static final int COLOR_DELIVERED = 0x55FF55;
    private static final int COLOR_OVERDUE = 0xFF6F61;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }
        JadeTooltipData.cargoAt(level, accessor.getPosition()).ifPresent(snapshot -> snapshot.write(tag));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(JadeTooltipData.CARGO_BOUND)) {
            return;
        }
        CargoSnapshot snapshot = CargoSnapshot.read(data);
        if (!snapshot.bound()) {
            tooltip.add(Component.translatable("jade.aerocouriers.container.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        IElementHelper helper = IElementHelper.get();
        tooltip.add(Component.translatable("jade.aerocouriers.container.order", snapshot.shortId()));

        ItemStack customerIcon = customerIcon(snapshot.customerKind());
        tooltip.add(helper.smallItem(customerIcon));
        tooltip.append(customerLine(snapshot));

        tooltip.add(helper.smallItem(new ItemStack(Items.CHEST)));
        tooltip.append(Component.translatable("jade.aerocouriers.container.weight",
                JadeTooltipData.formatWeight(snapshot.weight())));

        tooltip.add(Component.translatable("jade.aerocouriers.container.status",
                statusLine(snapshot.status())));
    }

    private static ItemStack customerIcon(CustomerKind kind) {
        return switch (kind) {
            case PLAYER -> new ItemStack(Items.PLAYER_HEAD);
            case VILLAGE -> new ItemStack(ModItems.POST_STATION.get());
            case TUTORIAL -> new ItemStack(Items.WRITABLE_BOOK);
        };
    }

    private static Component customerLine(CargoSnapshot snapshot) {
        Component name = switch (snapshot.customerKind()) {
            case TUTORIAL -> Component.translatable("ui.aerocouriers.tutorial.customer");
            case VILLAGE, PLAYER -> Component.literal(snapshot.customer());
        };
        return Component.translatable("jade.aerocouriers.container.customer", name);
    }

    private static MutableComponent statusLine(DisplayStatus status) {
        int color = switch (status) {
            case PUBLISHED -> COLOR_PUBLISHED;
            case IN_TRANSIT -> COLOR_IN_TRANSIT;
            case DELIVERED -> COLOR_DELIVERED;
            case OVERDUE -> COLOR_OVERDUE;
        };
        return Component.translatable("jade.aerocouriers.container.status." + status.key())
                .withStyle(Style.EMPTY.withColor(color));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
