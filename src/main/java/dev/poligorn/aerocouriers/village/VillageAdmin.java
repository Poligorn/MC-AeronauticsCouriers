package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.net.BoardPackets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Server-side village / station admin. Permission is always re-checked here; the client is not trusted.
 */
public final class VillageAdmin {
    public static final int OP = Commands.LEVEL_GAMEMASTERS;
    public static final String HERE = "here";

    private VillageAdmin() {
    }

    public static boolean canManageVillage(ServerPlayer player) {
        if (player.hasPermissions(OP)) {
            return true;
        }
        MinecraftServer server = player.server;
        return server != null && !server.isDedicatedServer() && isCreative(player);
    }

    public static boolean isCreative(ServerPlayer player) {
        return player.isCreative() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    public static Optional<StationAtlas.Station> resolve(MinecraftServer server, ServerPlayer player, String token) {
        StationAtlas atlas = StationAtlas.get(server);
        if (token == null || token.isBlank() || HERE.equalsIgnoreCase(token.strip())) {
            if (player == null) {
                return Optional.empty();
            }
            return atlas.byPos(player.level().dimension(), BlockPos.containing(player.position()))
                    .or(() -> nearest(atlas, player));
        }
        String want = token.strip();
        return atlas.all().stream()
                .filter(s -> s.name().equalsIgnoreCase(want))
                .findFirst();
    }

    private static Optional<StationAtlas.Station> nearest(StationAtlas atlas, ServerPlayer player) {
        return atlas.all().stream()
                .filter(s -> s.dimension().equals(player.level().dimension()))
                .min((a, b) -> Double.compare(
                        a.pos().distSqr(player.blockPosition()),
                        b.pos().distSqr(player.blockPosition())))
                .filter(s -> s.pos().closerThan(player.blockPosition(), 16));
    }

    public static boolean nameTaken(StationAtlas atlas, UUID self, String name) {
        String want = name.strip();
        return atlas.all().stream()
                .anyMatch(s -> !s.id().equals(self) && s.name().equalsIgnoreCase(want));
    }

    public static Component rename(MinecraftServer server, StationAtlas.Station station, String rawName) {
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > VillageBoardPolicy.NAME_MAX) {
            return Component.translatable("station.aerocouriers.error.name", VillageBoardPolicy.NAME_MAX);
        }
        if (!VillageBoardPolicy.hasValidNameChars(name)) {
            return Component.translatable("station.aerocouriers.error.name_chars");
        }
        StationAtlas atlas = StationAtlas.get(server);
        if (nameTaken(atlas, station.id(), name)) {
            return Component.translatable("admin.aerocouriers.village.rename.taken", name);
        }
        String old = station.name();
        if (old.equals(name)) {
            return Component.translatable("admin.aerocouriers.village.rename.same", name);
        }
        applyName(server, station, name);
        return Component.translatable("station.aerocouriers.renamed", old, name);
    }

    public static void applyName(MinecraftServer server, StationAtlas.Station station, String name) {
        StationAtlas atlas = StationAtlas.get(server);
        atlas.rename(station.id(), name);
        ServerLevel level = server.getLevel(station.dimension());
        if (level != null && level.isLoaded(station.pos())) {
            PostStationBlockEntity be = PostStationBlock.stationEntity(level, station.pos());
            if (be != null) {
                be.rename(name);
            }
        }
    }

    public static Component setReputation(MinecraftServer server, StationAtlas.Station station, int value) {
        int next = VillageBoardPolicy.clampReputation(value);
        StationAtlas.get(server).setReputation(station.id(), next);
        return Component.translatable("admin.aerocouriers.village.rep.set", station.name(), next);
    }

    public static Component addReputation(MinecraftServer server, StationAtlas.Station station, int delta) {
        int before = StationAtlas.get(server).reputation(station.id());
        int next = VillageBoardPolicy.clampReputation(before + delta);
        StationAtlas.get(server).setReputation(station.id(), next);
        return Component.translatable("admin.aerocouriers.village.rep.add", station.name(), delta, next);
    }

    public static Component setInterval(MinecraftServer server, StationAtlas.Station station, int minutes) {
        StationAtlas atlas = StationAtlas.get(server);
        atlas.setPolicy(station.id(), station.policy().withInterval(minutes));
        return Component.translatable("admin.aerocouriers.village.interval.set", station.name(),
                minutes <= 0 ? CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN.get() : minutes);
    }

    public static Component setExpensive(MinecraftServer server, StationAtlas.Station station, boolean allow) {
        StationAtlas.get(server).setPolicy(station.id(), station.policy().withExpensive(allow));
        return Component.translatable("admin.aerocouriers.village.toggle.expensive",
                station.name(), yn(allow));
    }

    public static Component setRare(MinecraftServer server, StationAtlas.Station station, boolean allow) {
        StationAtlas.get(server).setPolicy(station.id(), station.policy().withRare(allow));
        return Component.translatable("admin.aerocouriers.village.toggle.rare",
                station.name(), yn(allow));
    }

    public static Component setSizes(MinecraftServer server, StationAtlas.Station station, int mask) {
        VillageBoardPolicy next = station.policy().withSizes(mask);
        StationAtlas.get(server).setPolicy(station.id(), next);
        return Component.translatable("admin.aerocouriers.village.sizes.set", station.name(), sizeList(next));
    }

    public static Component refreshNow(MinecraftServer server, StationAtlas.Station station) {
        int created = StationContractSource.generateFor(server, station);
        return Component.translatable("admin.aerocouriers.village.orders.refresh", station.name(), created);
    }

    public static Component regenerate(MinecraftServer server, StationAtlas.Station station) {
        int cleared = clearOpen(server, station);
        int created = StationContractSource.generateFor(server, station);
        return Component.translatable("admin.aerocouriers.village.orders.regen", station.name(), cleared, created);
    }

    public static Component clearBoard(MinecraftServer server, StationAtlas.Station station) {
        int cleared = clearOpen(server, station);
        return Component.translatable("admin.aerocouriers.village.orders.clear", station.name(), cleared);
    }

    public static int clearOpen(MinecraftServer server, StationAtlas.Station station) {
        ContractManager manager = ContractManager.get(server);
        int cleared = 0;
        for (Contract contract : List.copyOf(manager.all().toList())) {
            if (contract.type() == ContractType.VILLAGE
                    && contract.state() == ContractState.OPEN
                    && contract.issuer().equals(station.id())) {
                manager.adminCancelWithRefund(server, contract.id());
                cleared++;
            }
        }
        return cleared;
    }

    public static Stats stats(MinecraftServer server, StationAtlas.Station station) {
        ContractManager manager = ContractManager.get(server);
        int onBoard = 0;
        int active = 0;
        int expensive = 0;
        int rare = 0;
        for (Contract contract : manager.all().toList()) {
            if (contract.type() != ContractType.VILLAGE || !contract.issuer().equals(station.id())) {
                continue;
            }
            if (contract.state() == ContractState.OPEN) {
                onBoard++;
                if (contract.reward() >= VillageBoardPolicy.EXPENSIVE_REWARD) {
                    expensive++;
                }
                if (Escrow.get(server).holding(contract.id()).villageRare()) {
                    rare++;
                }
            } else if (contract.state().isActive()) {
                active++;
            }
        }
        return new Stats(station.completed(), active, onBoard, expensive, rare,
                CouriersServerConfig.VILLAGE_BOARD_MAX.get());
    }

    public static Component statsMessage(MinecraftServer server, StationAtlas.Station station) {
        Stats s = stats(server, station);
        int rep = station.reputation();
        return Component.translatable("admin.aerocouriers.village.stats",
                station.name(),
                StationReputation.tier(rep),
                StationReputation.tier(VillageBoardPolicy.REPUTATION_MAX),
                rep,
                VillageBoardPolicy.REPUTATION_MAX,
                s.completed(),
                s.active(),
                s.onBoard() + s.active(),
                s.boardMax(),
                s.expensive(),
                s.rare());
    }

    public static String courierName(MinecraftServer server, Contract contract) {
        return contract.courier()
                .map(id -> BoardPackets.playerName(server, id).orElseGet(() -> liveName(server, id)))
                .orElse("");
    }

    private static String liveName(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        ServerPlayer test = ContractManager.findPlayer(server, id);
        return test != null ? test.getGameProfile().getName() : "";
    }

    public static boolean hasTablet(ServerPlayer player) {
        return OrderTablets.canAccessBoard(player);
    }

    private static Component yn(boolean value) {
        return Component.translatable(value ? "admin.aerocouriers.village.toggle.on" : "admin.aerocouriers.village.toggle.off");
    }

    private static String sizeList(VillageBoardPolicy policy) {
        StringBuilder out = new StringBuilder();
        for (CargoSize size : policy.sizes()) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(size.name());
        }
        return out.toString();
    }

    public record Stats(int completed, int active, int onBoard, int expensive, int rare, int boardMax) {
    }
}
