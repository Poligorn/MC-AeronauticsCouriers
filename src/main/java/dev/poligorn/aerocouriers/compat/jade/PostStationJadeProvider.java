package dev.poligorn.aerocouriers.compat.jade;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.compat.JadeTooltipData;
import dev.poligorn.aerocouriers.compat.JadeTooltipData.StationSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

/** Post station tooltip: name, village, viewer-facing reputation, open NPC orders, refresh countdown. */
public enum PostStationJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = AeronauticsCouriers.id("post_station");

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        ServerLevel level = CargoContainerJadeProvider.serverLevel(accessor);
        if (level == null) {
            return;
        }
        JadeTooltipData.stationAt(level, accessor.getPosition()).ifPresent(snapshot -> snapshot.write(tag));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        StationSnapshot snapshot = StationSnapshot.read(accessor.getServerData()).orElse(null);
        if (snapshot == null) {
            return;
        }
        if (!snapshot.name().isEmpty()) {
            tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                    Component.translatable("jade.aerocouriers.station.title", snapshot.name()));
        }
        if (snapshot.village() && !snapshot.villageName().isEmpty()) {
            tooltip.add(Component.translatable("jade.aerocouriers.station.village", snapshot.villageName()));
        }
        tooltip.add(Component.translatable("jade.aerocouriers.station.reputation",
                snapshot.reputation(), snapshot.bonusPercent()));
        tooltip.add(Component.translatable("jade.aerocouriers.station.open_orders", snapshot.openOrders()));
        if (snapshot.refreshTicks() < 0) {
            return;
        }
        if (snapshot.refreshTicks() <= 20) {
            tooltip.add(Component.translatable("jade.aerocouriers.station.refresh_soon")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("jade.aerocouriers.station.refresh",
                    JadeTooltipData.formatDuration(snapshot.refreshTicks()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
