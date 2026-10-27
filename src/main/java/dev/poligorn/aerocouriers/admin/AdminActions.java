package dev.poligorn.aerocouriers.admin;

import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.config.CouriersConfigEditor;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.ItemValues;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.WorldCargoOverrides;
import dev.poligorn.aerocouriers.contract.Weights;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.item.Waybills;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Shared admin intents for {@code /couriers admin} and the tablet admin panel. */
public final class AdminActions {
    public static final long DEFAULT_GRANT_MONEY = 500;

    private AdminActions() {
    }

    public static ContractManager.Outcome forceSpawn(MinecraftServer server, UUID id, ServerPlayer actor) {
        return ContractManager.get(server).spawnContainer(server, id);
    }

    public static ContractManager.Outcome forceDeliver(MinecraftServer server, UUID id, ServerPlayer actor) {
        return ContractManager.get(server).forceDeliver(server, id);
    }

    public static ContractManager.Outcome forceFail(MinecraftServer server, UUID id, ServerPlayer actor) {
        return ContractManager.get(server).destroy(server, id);
    }

    public static ContractManager.Outcome adminCancel(MinecraftServer server, UUID id, ServerPlayer actor) {
        return ContractManager.get(server).adminCancelWithRefund(server, id);
    }

    public static ContractManager.Outcome extend(MinecraftServer server, UUID id, ServerPlayer actor) {
        return ContractManager.get(server).extendDeadline(server, id, 30);
    }

    public static ContractManager.Outcome teleportToContract(MinecraftServer server, UUID id, ServerPlayer actor) {
        Contract contract = ContractManager.get(server).byId(id).orElse(null);
        if (contract == null) {
            return new ContractManager.Outcome(false,
                    Component.translatable("contract.aerocouriers.error.unknown"), Optional.empty());
        }
        Optional<CargoTracker.Snapshot> tracked = CargoTracker.get(id);
        if (tracked.isPresent()) {
            CargoTracker.Snapshot snap = tracked.get();
            ServerLevel level = server.getLevel(snap.dimension());
            if (level == null) {
                return new ContractManager.Outcome(false,
                        Component.translatable("contract.aerocouriers.error.pickup_not_loaded"), Optional.empty());
            }
            Vec3 pos = new Vec3(snap.position().x(), snap.position().y(), snap.position().z());
            actor.teleportTo(level, pos.x, pos.y, pos.z, actor.getYRot(), actor.getXRot());
            return new ContractManager.Outcome(true,
                    Component.translatable("admin.aerocouriers.contract.tp_container", contract.shortId()),
                    Optional.of(contract));
        }
        ServerLevel origin = server.getLevel(contract.origin().dimension());
        if (origin == null || !origin.isLoaded(contract.origin().pos())) {
            return new ContractManager.Outcome(false,
                    Component.translatable("contract.aerocouriers.error.pickup_not_loaded"), Optional.empty());
        }
        Vec3 at = Vec3.atCenterOf(contract.origin().pos());
        actor.teleportTo(origin, at.x, at.y, at.z, actor.getYRot(), actor.getXRot());
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.contract.tp_pickup", contract.shortId()),
                Optional.of(contract));
    }

    public static ContractManager.Outcome reloadDatapacks(MinecraftServer server) {
        server.reloadResources(server.getPackRepository().getSelectedIds());
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.economy.reloaded"), Optional.empty());
    }

    public static ContractManager.Outcome regenerateVillageBoards(MinecraftServer server) {
        ContractManager manager = ContractManager.get(server);
        int cleared = 0;
        for (Contract contract : List.copyOf(manager.all().toList())) {
            if (contract.type() == ContractType.VILLAGE && contract.state() == ContractState.OPEN) {
                manager.adminCancelWithRefund(server, contract.id());
                cleared++;
            }
        }
        int created = StationContractSource.INSTANCE.generate(server);
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.server.regen", cleared, created), Optional.empty());
    }

    public static ContractManager.Outcome grantLicense(ServerPlayer target, LicenseRank rank) {
        if (!rank.isAdmin()) {
            CourierProfile profile = Licenses.profile(target);
            Licenses.setProfile(target, profile.withXp(minimumXp(rank)));
        }
        ItemStack license = Licenses.issueLicense(target, rank, false);
        if (!target.getInventory().add(license)) {
            target.drop(license, false);
        }
        return new ContractManager.Outcome(true,
                Component.translatable("commands.aerocouriers.license.given", target.getDisplayName(), rank.displayName()),
                Optional.empty());
    }

    public static ContractManager.Outcome grantMoney(ServerPlayer target, long amount) {
        Economies.get(target.server).deposit(target.getUUID(), amount, "admin");
        long balance = Economies.get(target.server).balance(target.getUUID());
        return new ContractManager.Outcome(true,
                Component.translatable("contract.aerocouriers.balance", target.getDisplayName(), balance),
                Optional.empty());
    }

    public static ContractManager.Outcome grantWaybill(ServerPlayer target) {
        ItemStack waybill = Waybills.stack(4);
        if (!target.getInventory().add(waybill)) {
            target.drop(waybill, false);
        }
        return new ContractManager.Outcome(true,
                Component.translatable("commands.aerocouriers.waybill.given", target.getDisplayName()),
                Optional.empty());
    }

    public static ContractManager.Outcome setXp(ServerPlayer target, int amount, boolean add) {
        CourierProfile profile = Licenses.profile(target);
        int next = add ? profile.xp() + amount : amount;
        Licenses.setProfile(target, profile.withXp(next));
        return new ContractManager.Outcome(true,
                Component.translatable("commands.aerocouriers.xp.changed", target.getDisplayName(), next,
                        RankRules.rankFor(Licenses.profile(target)).displayName()),
                Optional.empty());
    }

    public static ContractManager.Outcome setReputation(MinecraftServer server, String stationName, int value) {
        StationAtlas atlas = StationAtlas.get(server);
        Optional<StationAtlas.Station> station = atlas.all().stream()
                .filter(s -> s.name().equalsIgnoreCase(stationName))
                .findFirst();
        if (station.isEmpty()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("admin.aerocouriers.player.station_unknown", stationName), Optional.empty());
        }
        atlas.setReputation(station.get().id(), value);
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.player.rep_set", stationName, value), Optional.empty());
    }

    public static ContractManager.Outcome handValue(ServerPlayer admin, boolean set, long valueSpurs) {
        ItemStack hand = admin.getMainHandItem();
        if (hand.isEmpty()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("admin.aerocouriers.economy.empty_hand"), Optional.empty());
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(hand.getItem());
        if (set) {
            WorldCargoOverrides.get(admin.server).setValue(id, valueSpurs);
            return new ContractManager.Outcome(true,
                    Component.translatable("admin.aerocouriers.economy.value_set", id, valueSpurs), Optional.empty());
        }
        long shown = ItemValues.perItem(hand);
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.economy.value_show", id, shown), Optional.empty());
    }

    public static ContractManager.Outcome handWeight(ServerPlayer admin, boolean set, double weight) {
        ItemStack hand = admin.getMainHandItem();
        if (hand.isEmpty()) {
            return new ContractManager.Outcome(false,
                    Component.translatable("admin.aerocouriers.economy.empty_hand"), Optional.empty());
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(hand.getItem());
        if (set) {
            WorldCargoOverrides.get(admin.server).setWeight(id, weight);
            return new ContractManager.Outcome(true,
                    Component.translatable("admin.aerocouriers.economy.weight_set", id, weight), Optional.empty());
        }
        double shown = Weights.perItem(hand);
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.economy.weight_show", id, shown), Optional.empty());
    }

    public static ContractManager.Outcome setConfig(String key, String value) {
        Optional<Component> err = CouriersConfigEditor.set(key, value);
        if (err.isPresent()) {
            return new ContractManager.Outcome(false, err.get(), Optional.empty());
        }
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.config.set", key, value), Optional.empty());
    }

    public static ContractManager.Outcome toggleAllowSelfAccept() {
        CouriersConfigEditor.toggleDebugAllowSelfAccept();
        boolean on = dev.poligorn.aerocouriers.config.CouriersServerConfig.ALLOW_SELF_ACCEPT.get();
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.debug.toggled", "allow_self_accept", on), Optional.empty());
    }

    private static int minimumXp(LicenseRank rank) {
        if (rank.isAdmin()) {
            return 0;
        }
        return RankRules.nextThreshold(LicenseRank.byLevel(Math.max(1, rank.level() - 1)))
                .orElse(0);
    }
}
