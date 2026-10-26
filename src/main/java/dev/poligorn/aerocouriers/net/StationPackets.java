package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationReputation;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class StationPackets {
    private static final int OP = Commands.LEVEL_GAMEMASTERS;
    private static final int MAX_NAME_LENGTH = 48;

    private StationPackets() {
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PostStationBlockEntity station) || station.stationId() == null) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station", pos.toShortString()));
            return;
        }
        UUID stationId = station.stationId();
        StationAtlas atlas = StationAtlas.get(player.server);
        int reputation = atlas.reputation(stationId);
        int bonus = StationReputation.bonusPercent(reputation);
        List<BoardSnapshotPayload.BoardEntry> orders = ContractManager.get(player.server).open()
                .filter(c -> c.type() == ContractType.VILLAGE)
                .filter(c -> c.issuer().equals(stationId) || c.recipient().equals(stationId))
                .sorted(Comparator.comparingLong(c -> c.timing().createdAt()))
                .limit(StationSnapshotPayload.MAX_ORDERS)
                .map(c -> BoardPackets.boardEntry(player, c))
                .toList();
        PacketDistributor.sendToPlayer(player, new StationSnapshotPayload(
                pos.immutable(),
                station.name(),
                reputation,
                bonus,
                player.hasPermissions(OP),
                orders));
    }

    public static void handleOpen(ServerPlayer player, OpenStationPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        open(player, level, payload.pos());
    }

    public static void handleRename(ServerPlayer player, RenameStationPayload payload) {
        if (!player.hasPermissions(OP)) {
            player.sendSystemMessage(Component.translatable("ui.aerocouriers.error.not_operator"));
            return;
        }
        String name = payload.name().strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.name", MAX_NAME_LENGTH));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(payload.pos()) instanceof PostStationBlockEntity station)) {
            player.sendSystemMessage(Component.translatable("station.aerocouriers.error.not_station",
                    payload.pos().toShortString()));
            return;
        }
        String old = station.name();
        station.rename(name);
        player.sendSystemMessage(Component.translatable("station.aerocouriers.renamed", old, name));
        open(player, level, payload.pos());
    }

}
