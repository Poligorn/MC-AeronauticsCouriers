package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlockEntity;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.item.Waybills;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import dev.poligorn.aerocouriers.village.StationReputation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.poligorn.aerocouriers.sound.CouriersSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * All contracts and their state machine (GDD §5.2, §7, §9). The only class that moves cargo and money for
 * contracts; every transition checks the current state first, so a repeated call can never pay or drop twice.
 *
 * <p>Container loop (GDD §9.1–9.3): an accepted contract spawns its container when the courier comes within
 * {@code spawn_trigger_radius} of the pickup point; the container block entity reports its world position once a
 * second through {@link #checkContainer}, which delivers, fails or tells the block to remove itself.
 */
public final class ContractManager extends SavedData {
    private static final String NAME = "aerocouriers_contracts";
    /** Finished contracts stay visible in "Мои заказы" for this long, then are pruned. */
    private static final long HISTORY_TICKS = 30L * 24 * 60 * 60 * 20;
    /** The issuer must stand this close to the chest the cargo is taken from. */
    public static final double ISSUER_REACH = 8.0;
    /** GDD §12: only the contract's courier within this radius of the container can deliver it. */
    public static final double COURIER_REACH = 64.0;
    /** A container block that disappeared and did not report from anywhere else for this long is destroyed. */
    public static final long MISSING_GRACE_TICKS = 60;
    private static final long TICK_INTERVAL = 20;
    private static final long BLOCKED_NOTICE_INTERVAL = 60L * 20;

    public enum FailReason {
        ABANDONED, TIMED_OUT, DESTROYED, DAMAGED;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Verdict {
        KEEP, REMOVE
    }

    public record Outcome(boolean success, Component message, Optional<Contract> contract) {
        static Outcome ok(Contract contract, Component message) {
            return new Outcome(true, message, Optional.of(contract));
        }

        static Outcome ok(Contract contract, String key, Object... args) {
            return ok(contract, Component.translatable("contract.aerocouriers." + key, args));
        }

        static Outcome error(String key, Object... args) {
            return new Outcome(false, Component.translatable("contract.aerocouriers.error." + key, args), Optional.empty());
        }
    }

    /** Where the manifest of a damaged container goes (GDD §9.7). */
    public record DropSite(ServerLevel level, Vec3 position, @Nullable Player breaker) {
    }

    private record Delivery(UUID courier, UUID issuer, long time) {
        static final Codec<Delivery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("courier").forGetter(Delivery::courier),
                UUIDUtil.CODEC.fieldOf("issuer").forGetter(Delivery::issuer),
                Codec.LONG.fieldOf("time").forGetter(Delivery::time)
        ).apply(instance, Delivery::new));
    }

    /** FakePlayers used by GameTests are not in the player list; only filled when GameTests are enabled. */
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new ConcurrentHashMap<>();

    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();
    /** Failures of couriers who were offline when their contract failed; applied to the profile on login. */
    private final Map<UUID, Integer> pendingFailures = new HashMap<>();
    private final List<Delivery> recentDeliveries = new ArrayList<>();

    // Transient, rebuilt after a restart.
    private final Map<UUID, Integer> stillChecks = new HashMap<>();
    private final Map<UUID, Long> missingSince = new HashMap<>();
    private final Map<UUID, Long> blockedNoticeAt = new HashMap<>();
    private @Nullable MinecraftServer server;

    public static ContractManager get(MinecraftServer server) {
        ContractManager manager = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ContractManager::new, ContractManager::load, null), NAME);
        manager.server = server;
        return manager;
    }

    public static void registerTestPlayer(ServerPlayer player) {
        if (GameTestHooks.isGametestEnabled()) {
            TEST_PLAYERS.put(player.getUUID(), player);
        }
    }

    static @Nullable ServerPlayer findPlayer(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? player : TEST_PLAYERS.get(id);
    }

    // ---- queries ----

    public Optional<Contract> byId(UUID id) {
        return Optional.ofNullable(contracts.get(id));
    }

    /** Exact UUID or a unique prefix (commands show the first 8 characters). */
    public Optional<Contract> find(String token) {
        try {
            return byId(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            List<Contract> matches = contracts.values().stream()
                    .filter(c -> c.id().toString().startsWith(token))
                    .limit(2)
                    .toList();
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        }
    }

    public Stream<Contract> all() {
        return contracts.values().stream();
    }

    public Stream<Contract> open() {
        return all().filter(c -> c.state() == ContractState.OPEN);
    }

    public Stream<Contract> byCourier(UUID courier) {
        return all().filter(c -> c.isCourier(courier));
    }

    public Stream<Contract> byIssuer(UUID issuer) {
        return all().filter(c -> c.issuer().equals(issuer));
    }

    /**
     * Jobs this player is currently hauling (GDD §4.2 {@code maxActive}). Only {@code ACCEPTED}/{@code IN_TRANSIT}
     * with this UUID in {@code courier}. Own {@code OPEN} orders, finished/failed/cancelled/expired rows and
     * pending offline-failure bookkeeping do not occupy the slot — otherwise self-accept (issuer == courier)
     * would treat the very order being taken as an already-active delivery.
     */
    public List<Contract> activeJobs(UUID courier) {
        return all()
                .filter(c -> c.courier().filter(courier::equals).isPresent())
                .filter(c -> c.state() == ContractState.ACCEPTED || c.state() == ContractState.IN_TRANSIT)
                .toList();
    }

    /** Unfinished orders this player published; used only by {@code max_open_per_player} on create. */
    public long unfinishedIssued(UUID issuer) {
        return byIssuer(issuer).filter(c -> !c.state().isFinal()).count();
    }

    /** What the courier loses on failure — shown before and on acceptance (owner's decision, GDD §7.2 / §6.3). */
    public static Component riskLine(Contract contract) {
        String key = contract.type().isNpc() ? "contract.aerocouriers.risk.npc" : "contract.aerocouriers.risk";
        return Component.translatable(key, contract.collateral(), contract.reward());
    }

    // ---- transitions ----

    /**
     * P2P creation (GDD §7.1): the whole content of {@code source} becomes the manifest and goes into escrow, the
     * reward and the 5% fee are taken from the issuer. Nothing changes unless every check passes.
     */
    public Outcome create(ServerPlayer issuer, Container source, BlockPos sourcePos, Destination destination,
                          long reward, long collateral, int deadlineMinutes) {
        MinecraftServer server = issuer.server;
        long now = now(server);
        Destination origin = new Destination(issuer.level().dimension(), sourcePos.immutable(), destination.radius());

        if (unfinishedIssued(issuer.getUUID()) >= CouriersServerConfig.MAX_OPEN_PER_PLAYER.get()) {
            return Outcome.error("too_many_open", CouriersServerConfig.MAX_OPEN_PER_PLAYER.get());
        }
        if (deadlineMinutes < Pricing.MIN_DEADLINE_MINUTES || deadlineMinutes > Pricing.MAX_DEADLINE_MINUTES) {
            return Outcome.error("deadline", Pricing.MIN_DEADLINE_MINUTES, Pricing.MAX_DEADLINE_MINUTES);
        }
        if (!origin.within(issuer.level().dimension(), issuer.position(), ISSUER_REACH)) {
            return Outcome.error("too_far_from_cargo", (int) ISSUER_REACH);
        }

        List<ItemStack> manifest = new ArrayList<>();
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (Weights.isForbidden(stack)) {
                return Outcome.error("forbidden_item", stack.getHoverName());
            }
            manifest.add(stack.copy());
        }
        if (manifest.isEmpty()) {
            return Outcome.error("empty_cargo");
        }
        double weight = Weights.of(manifest);
        Optional<CargoSize> size = CargoSize.smallestFor(manifest.size(), weight);
        if (size.isEmpty()) {
            return Outcome.error("cargo_too_big", manifest.size(), String.format(Locale.ROOT, "%.1f", weight));
        }
        double distance = ContractDistances.pricingBlocks(origin, destination);
        long minReward = Math.max(1, Pricing.minReward(distance, weight));
        if (reward < minReward) {
            return Outcome.error("reward_too_low", minReward);
        }
        if (collateral < 0) {
            return Outcome.error("negative_collateral");
        }
        long minCollateral = Pricing.minP2pCollateral(reward, manifest);
        if (collateral < minCollateral) {
            return Outcome.error("collateral_below_minimum", minCollateral);
        }

        UUID id = UUID.randomUUID();
        long fee = Pricing.fee(reward, size.get());
        Economy economy = Economies.get(server);
        if (!economy.withdraw(issuer.getUUID(), reward + fee, "create:" + id)) {
            return Outcome.error("not_enough_money", reward + fee, economy.balance(issuer.getUUID()));
        }
        Optional<OriginContainerSnapshot> originSnapshot = Optional.empty();
        if (issuer.level() instanceof ServerLevel originLevel) {
            originSnapshot = OriginContainerSnapshot.capture(originLevel, sourcePos);
        }
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            source.setItem(slot, ItemStack.EMPTY);
        }
        source.setChanged();

        long deadline = deadlineMinutes * Pricing.TICKS_PER_MINUTE;
        Contract contract = new Contract(id, ContractType.PLAYER, issuer.getUUID(), issuer.getUUID(), Optional.empty(),
                origin, destination, size.get(), weight, reward, collateral,
                ContractDistances.routeRequiredRank(collateral, size.get(), origin, destination),
                new Contract.Timing(now, now + deadline, deadline, -1, 0), ContractState.OPEN, false,
                originSnapshot);
        Escrow.get(server).open(id, manifest, reward);
        put(contract);
        recordIssuerOrder(issuer, size.get(), reward, weight);
        AeronauticsCouriers.LOGGER.info("[contract] {} created by {}: {} stacks, W={}, D={}, reward={}, fee={}, collateral={}",
                id, issuer.getGameProfile().getName(), manifest.size(), weight, (long) distance, reward, fee, collateral);
        return Outcome.ok(contract, "created", contract.shortId(), reward, fee,
                contract.requiredRank().displayName());
    }

    /**
     * Player-facing create: transport waybill on a container. Same money/cargo checks as {@link #create}, plus a
     * worn license and the right to open the block. The source is emptied without drops, then replaced in-place
     * by a Sable cargo container that already stands at pickup while the order is {@code OPEN}. One waybill is
     * consumed on success (not in creative).
     */
    public Outcome createFromWaybill(ServerPlayer issuer, BlockPos sourcePos, Destination destination,
                                   long reward, long collateral, int deadlineMinutes) {
        if (!Licenses.isLicensedCourier(issuer)) {
            return Outcome.error("no_license");
        }
        if (!issuer.isCreative() && !Waybills.hasOne(issuer)) {
            return Outcome.error("no_waybill");
        }
        if (!(issuer.level() instanceof ServerLevel level)) {
            return Outcome.error("no_container", sourcePos.toShortString());
        }
        if (!CargoContainers.canOpen(issuer, level, sourcePos)) {
            return Outcome.error("no_access", sourcePos.toShortString());
        }
        Optional<CargoContainers.Access> found = CargoContainers.find(level, sourcePos);
        if (found.isEmpty()) {
            return Outcome.error("no_container", sourcePos.toShortString());
        }
        CargoContainers.Access access = found.get();
        MinecraftServer server = issuer.server;
        long now = now(server);
        Destination origin = new Destination(level.dimension(), sourcePos.immutable(), destination.radius());

        if (unfinishedIssued(issuer.getUUID()) >= CouriersServerConfig.MAX_OPEN_PER_PLAYER.get()) {
            return Outcome.error("too_many_open", CouriersServerConfig.MAX_OPEN_PER_PLAYER.get());
        }
        if (deadlineMinutes < Pricing.MIN_DEADLINE_MINUTES || deadlineMinutes > Pricing.MAX_DEADLINE_MINUTES) {
            return Outcome.error("deadline", Pricing.MIN_DEADLINE_MINUTES, Pricing.MAX_DEADLINE_MINUTES);
        }
        if (!origin.within(level.dimension(), issuer.position(), ISSUER_REACH)) {
            return Outcome.error("too_far_from_cargo", (int) ISSUER_REACH);
        }

        List<ItemStack> manifest = new ArrayList<>();
        for (ItemStack stack : access.items()) {
            if (Weights.isForbidden(stack)) {
                return Outcome.error("forbidden_item", stack.getHoverName());
            }
            manifest.add(stack.copy());
        }
        if (manifest.isEmpty()) {
            return Outcome.error("empty_cargo");
        }
        double weight = Weights.of(manifest);
        Optional<CargoSize> size = CargoSize.smallestFor(manifest.size(), weight);
        if (size.isEmpty()) {
            return Outcome.error("cargo_too_big", manifest.size(), String.format(Locale.ROOT, "%.1f", weight));
        }
        double distance = ContractDistances.pricingBlocks(origin, destination);
        long minReward = Math.max(1, Pricing.minReward(distance, weight));
        if (reward < minReward) {
            return Outcome.error("reward_too_low", minReward);
        }
        if (collateral < 0) {
            return Outcome.error("negative_collateral");
        }
        long minCollateral = Pricing.minP2pCollateral(reward, manifest);
        if (collateral < minCollateral) {
            return Outcome.error("collateral_below_minimum", minCollateral);
        }

        UUID id = UUID.randomUUID();
        long fee = Pricing.fee(reward, size.get());
        Economy economy = Economies.get(server);
        if (!economy.withdraw(issuer.getUUID(), reward + fee, "create:" + id)) {
            return Outcome.error("not_enough_money", reward + fee, economy.balance(issuer.getUUID()));
        }

        Optional<OriginContainerSnapshot> originSnapshot = OriginContainerSnapshot.capture(level, access);
        Direction facing = CargoSpawner.facingFromReplacedBlock(level, sourcePos, issuer.getDirection().getOpposite());
        CargoContainers.clear(access);
        CargoContainers.removeWithoutDrop(level, access);

        Optional<BlockPos> anchor = CargoSpawner.resolveAnchor(level, sourcePos, size.get());
        if (anchor.isEmpty()) {
            economy.deposit(issuer.getUUID(), reward + fee, "create_refund:" + id);
            Mailbox.get(server).post(issuer.getUUID(), manifest);
            return Outcome.error("stamp_spawn_failed");
        }
        BlockPos pickup = anchor.get();
        origin = new Destination(level.dimension(), pickup, destination.radius());

        int fill = size.get().fillFor(weight);
        CargoSpawner.Result spawned = CargoSpawner.spawn(level, pickup, size.get(), fill, id, false, facing, true);
        if (!(spawned instanceof CargoSpawner.Result.Placed)) {
            economy.deposit(issuer.getUUID(), reward + fee, "create_refund:" + id);
            Mailbox.get(server).post(issuer.getUUID(), manifest);
            return Outcome.error("stamp_spawn_failed");
        }

        long deadline = deadlineMinutes * Pricing.TICKS_PER_MINUTE;
        Contract contract = new Contract(id, ContractType.PLAYER, issuer.getUUID(), issuer.getUUID(), Optional.empty(),
                origin, destination, size.get(), weight, reward, collateral,
                ContractDistances.routeRequiredRank(collateral, size.get(), origin, destination),
                new Contract.Timing(now, now + deadline, deadline, -1, 0), ContractState.OPEN, true,
                originSnapshot);
        Escrow.get(server).open(id, manifest, reward);
        put(contract);
        recordIssuerOrder(issuer, size.get(), reward, weight);
        CouriersSounds.playWaybillSealed(level, sourcePos);
        if (!issuer.isCreative()) {
            Waybills.consumeOne(issuer);
        }
        AeronauticsCouriers.LOGGER.info(
                "[contract] {} waybill by {}: {} stacks, W={}, D={}, reward={}, fee={}, collateral={}",
                id, issuer.getGameProfile().getName(), manifest.size(), weight, (long) distance, reward, fee,
                collateral);
        return Outcome.ok(contract, "created_waybill", contract.shortId(), reward, fee,
                contract.requiredRank().displayName(), sourcePos.toShortString());
    }

    /** Issuer profile counters (playtest §9): updated when the order is created, not on completion. */
    private static void recordIssuerOrder(ServerPlayer issuer, CargoSize size, long reward, double weight) {
        Licenses.setProfile(issuer, Licenses.profile(issuer).withIssuerOrder(size, reward, weight));
    }

    /**
     * NPC station order (GDD §6): reward is minted into escrow, manifest is generated in escrow. The physical crate
     * appears when a courier accepts (lazy spawn, §9.1). Issuer/recipient are station ids, not players.
     */
    public Optional<Contract> publishVillage(ServerLevel level, StationAtlas.Station from, StationAtlas.Station to,
                                            List<ItemStack> manifest, CargoSize size, double weight, long reward,
                                            long collateral, int deadlineMinutes, boolean villageRare) {
        MinecraftServer server = level.getServer();
        long now = now(server);
        Destination origin = new Destination(from.dimension(), from.pos().immutable(), StationContractSource.ZONE_RADIUS);
        Destination destination = new Destination(to.dimension(), to.pos().immutable(), StationContractSource.ZONE_RADIUS);
        UUID id = UUID.randomUUID();
        double distance = ContractDistances.pricingBlocks(origin, destination);
        Contract contract = new Contract(id, ContractType.VILLAGE, from.id(), to.id(), Optional.empty(),
                origin, destination, size, weight, reward, collateral,
                ContractDistances.routeRequiredRank(collateral, size, origin, destination),
                new Contract.Timing(now, now + deadlineMinutes * Pricing.TICKS_PER_MINUTE,
                        deadlineMinutes * Pricing.TICKS_PER_MINUTE, -1, 0),
                ContractState.OPEN, false, Optional.empty());
        Escrow.get(server).open(id, manifest, reward, villageRare);
        put(contract);
        AeronauticsCouriers.LOGGER.info(
                "[contract] {} NPC «{}» → «{}»: {} stacks, W={}, D={}, reward={}, collateral={}, rare={}",
                id, from.name(), to.name(), manifest.size(), weight, (long) distance, reward, collateral, villageRare);
        return Optional.of(contract);
    }

    /** GDD §7.2: own contracts cannot be taken unless {@code debug.allow_self_accept}; collateral is atomic. */
    public Outcome accept(ServerPlayer courier, UUID id) {
        MinecraftServer server = courier.server;
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.state() != ContractState.OPEN || now(server) >= contract.timing().expiresAt()) {
            return Outcome.error("not_open");
        }
        boolean self = contract.issuer().equals(courier.getUUID());
        if (self && !CouriersServerConfig.ALLOW_SELF_ACCEPT.get()) {
            return Outcome.error("own_contract");
        }
        if (!Licenses.isLicensedCourier(courier)) {
            return Outcome.error("no_license");
        }
        LicenseRank rank = Licenses.rankOf(courier);
        CourierProfile profile = Licenses.profile(courier);
        int traineeFree = CouriersServerConfig.TRAINEE_FREE_CONTRACTS.get();
        int traineeMax = CouriersServerConfig.TRAINEE_MAX_DISTANCE.get();
        Optional<AcceptEligibility.Denial> denial = AcceptEligibility.check(
                rank,
                contract.requiredRank(),
                contract.collateral(),
                contract.size(),
                contract.distance(),
                contract.crossDimension(),
                contract.type().isNpc(),
                profile.villageDelivered(),
                traineeFree,
                traineeMax);
        if (denial.isPresent()) {
            return denial.get().toOutcome();
        }
        int maxActive = RankRules.limits(rank).maxActive();
        List<Contract> active = activeJobs(courier.getUUID());
        if (active.size() >= maxActive) {
            String ids = active.stream().map(Contract::shortId).collect(Collectors.joining(", "));
            return Outcome.error("too_many_active", maxActive, ids);
        }
        long collateralDue = contract.collateral();
        if (contract.type().isNpc() && profile.villageDelivered() < traineeFree) {
            collateralDue = 0;
        }
        Economy economy = Economies.get(server);
        if (collateralDue > 0 && !economy.withdraw(courier.getUUID(), collateralDue, "collateral:" + id)) {
            return Outcome.error("not_enough_money", collateralDue, economy.balance(courier.getUUID()));
        }
        if (collateralDue > 0) {
            Escrow.get(server).addCollateral(id, collateralDue);
        }
        Contract accepted = contract.accepted(courier.getUUID(), now(server));
        // Persist ACCEPTED before block updates: unlock touches block entities while the map still said OPEN,
        // which made containerRemoved expire the stamp order and delete the crate.
        put(accepted);
        // Stamp / NPC crates already stand at pickup; leaving ACCEPTED until the next tick let a courier
        // fly the crate away and then never reach IN_TRANSIT, so the rank-1 slot stayed occupied.
        if (accepted.preplaced()) {
            ServerLevel pickup = server.getLevel(accepted.origin().dimension());
            if (pickup != null && pickup.isLoaded(accepted.origin().pos())) {
                CargoSpawner.unlockAndAssemble(pickup, accepted.origin().pos());
            }
            accepted = accepted.withState(ContractState.IN_TRANSIT);
            put(accepted);
        }
        if (!self) {
            notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.accepted",
                    contract.shortId(), courier.getDisplayName()), id);
        }
        String acceptKey = contract.preplaced() ? "accepted_preplaced" : "accepted";
        Component message = Component.translatable("contract.aerocouriers." + acceptKey, contract.shortId(),
                        contract.collateral(), accepted.timing().deadline() / Pricing.TICKS_PER_MINUTE,
                        contract.origin().pos().toShortString())
                .append("\n").append(riskLine(contract));
        if (self) {
            Component warning = Component.translatable("contract.aerocouriers.debug.self_accept");
            courier.sendSystemMessage(warning);
            message = message.copy().append("\n").append(warning);
        }
        return Outcome.ok(accepted, message);
    }

    /**
     * Lazy container spawn (GDD §9.1): on a free 3×3×3 spot with a solid base near the pickup point. If none is
     * free the deadline is paused and both parties are told (GDD §12).
     */
    public Outcome spawnContainer(MinecraftServer server, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.preplaced() && contract.state() == ContractState.IN_TRANSIT) {
            return Outcome.ok(contract, "container_ready", contract.shortId(),
                    contract.origin().pos().toShortString());
        }
        if (contract.state() != ContractState.ACCEPTED) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        if (contract.preplaced()) {
            blockedNoticeAt.remove(id);
            ServerLevel pickup = server.getLevel(contract.origin().dimension());
            if (pickup != null && pickup.isLoaded(contract.origin().pos())) {
                CargoSpawner.unlockAndAssemble(pickup, contract.origin().pos());
            }
            Contract inTransit = contract.withState(ContractState.IN_TRANSIT);
            put(inTransit);
            notify(server, contract.courier().orElseThrow(), Component.translatable(
                    "contract.aerocouriers.notify.container_waiting", contract.shortId(),
                    contract.origin().pos().toShortString()), id);
            return Outcome.ok(inTransit, "container_ready", contract.shortId(),
                    contract.origin().pos().toShortString());
        }
        ServerLevel level = server.getLevel(contract.origin().dimension());
        if (level == null || !level.isLoaded(contract.origin().pos())) {
            return Outcome.error("pickup_not_loaded");
        }
        Optional<BlockPos> spot = CargoSpawner.findSpot(level, contract.origin().pos(),
                CouriersServerConfig.SPAWN_SEARCH_RADIUS.get(), contract.size());
        if (spot.isEmpty()) {
            put(contract.withPause(TICK_INTERVAL));
            long now = now(server);
            Long last = blockedNoticeAt.get(id);
            if (last == null || now - last >= BLOCKED_NOTICE_INTERVAL) {
                blockedNoticeAt.put(id, now);
                Component notice = Component.translatable("contract.aerocouriers.notify.pickup_blocked",
                        contract.shortId(), contract.origin().pos().toShortString());
                notify(server, contract.issuer(), notice, id);
                notify(server, contract.courier().orElseThrow(), notice, id);
            }
            return Outcome.error("pickup_blocked", contract.origin().pos().toShortString());
        }
        int fill = contract.size().fillFor(contract.weight());
        CargoSpawner.Result result = CargoSpawner.spawn(level, spot.get(), contract.size(), fill, id, false,
                Direction.NORTH);
        if (!(result instanceof CargoSpawner.Result.Spawned)) {
            return Outcome.error("spawn_failed");
        }
        blockedNoticeAt.remove(id);
        Contract inTransit = contract.withState(ContractState.IN_TRANSIT);
        put(inTransit);
        Vec3 at = Vec3.atCenterOf(spot.get());
        level.playSound(null, spot.get(), SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 20, 0.6, 0.6, 0.6, 0.02);
        notify(server, contract.courier().orElseThrow(), Component.translatable(
                "contract.aerocouriers.notify.container_arrived", contract.shortId(), spot.get().toShortString()), id);
        return Outcome.ok(inTransit, "container_spawned", contract.shortId(), spot.get().toShortString());
    }

    /**
     * Once a second from the container block entity (GDD §9.3). Delivers after {@code delivery_still_checks}
     * consecutive checks inside the destination zone, slower than {@code delivery_max_speed}, with the courier
     * within {@link #COURIER_REACH}; fails as destroyed below the world.
     */
    public Verdict checkContainer(ServerLevel level, UUID id, Vector3dc world, double speed) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            stillChecks.remove(id);
            return Verdict.REMOVE;
        }
        // Stamp / NPC crates already stand while OPEN; do not delete them.
        if (contract.preplaced() && contract.state() == ContractState.OPEN) {
            missingSince.remove(id);
            return Verdict.KEEP;
        }
        // A leftover ACCEPTED stamp job: the courier next to the crate has picked it up.
        if (contract.preplaced() && contract.state() == ContractState.ACCEPTED) {
            missingSince.remove(id);
            ServerPlayer holder = findPlayer(level.getServer(), contract.courier().orElseThrow());
            Vec3 crate = new Vec3(world.x(), world.y(), world.z());
            if (holder != null && holder.level() == level
                    && holder.position().distanceToSqr(crate) <= COURIER_REACH * COURIER_REACH) {
                contract = contract.withState(ContractState.IN_TRANSIT);
                put(contract);
            } else {
                return Verdict.KEEP;
            }
        }
        if (contract.state() != ContractState.IN_TRANSIT) {
            stillChecks.remove(id);
            return Verdict.REMOVE;
        }
        missingSince.remove(id);
        MinecraftServer server = level.getServer();
        if (world.y() < level.getMinBuildHeight()) {
            fail(server, contract, FailReason.DESTROYED, null);
            return Verdict.REMOVE;
        }
        Vec3 position = new Vec3(world.x(), world.y(), world.z());
        ServerPlayer courier = findPlayer(server, contract.courier().orElseThrow());
        boolean ready = contract.destination().contains(level.dimension(), position)
                && speed < CouriersServerConfig.DELIVERY_MAX_SPEED.get()
                && courier != null && courier.level() == level
                && courier.position().distanceToSqr(position) <= COURIER_REACH * COURIER_REACH;
        if (!ready) {
            stillChecks.remove(id);
            return Verdict.KEEP;
        }
        if (stillChecks.merge(id, 1, Integer::sum) < CouriersServerConfig.DELIVERY_STILL_CHECKS.get()) {
            return Verdict.KEEP;
        }
        stillChecks.remove(id);
        completeDelivery(server, contract, courier, position);
        return Verdict.REMOVE;
    }

    /** Operator tool: delivers an in-transit contract wherever the container is; the courier must be online. */
    public Outcome forceDeliver(MinecraftServer server, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.preplaced() && contract.state() == ContractState.ACCEPTED) {
            ServerLevel pickup = server.getLevel(contract.origin().dimension());
            if (pickup != null && pickup.isLoaded(contract.origin().pos())) {
                CargoSpawner.unlockAndAssemble(pickup, contract.origin().pos());
            }
            contract = contract.withState(ContractState.IN_TRANSIT);
            put(contract);
        }
        if (contract.state() != ContractState.IN_TRANSIT) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        ServerPlayer courier = findPlayer(server, contract.courier().orElseThrow());
        if (courier == null) {
            return Outcome.error("courier_offline");
        }
        Vec3 at = CargoTracker.get(id)
                .map(s -> new Vec3(s.position().x(), s.position().y(), s.position().z()))
                .orElse(Vec3.atCenterOf(contract.destination().pos()));
        return completeDelivery(server, contract, courier, at);
    }

    /** Successful delivery (GDD §9.3, §9.6). */
    private Outcome completeDelivery(MinecraftServer server, Contract contract, ServerPlayer courier, Vec3 deliveryAt) {
        UUID id = contract.id();
        long now = now(server);
        if (now > contract.timing().lateUntil(Pricing.lateWindow())) {
            fail(server, contract, FailReason.TIMED_OUT, null);
            return Outcome.error("too_late");
        }
        boolean late = now > contract.timing().dueAt();

        Escrow escrow = Escrow.get(server);
        Economy economy = Economies.get(server);
        long reward = escrow.takeReward(id);
        long paid = late ? Pricing.lateReward(reward) : reward;
        if (contract.type().isNpc()) {
            StationAtlas atlas = StationAtlas.get(server);
            atlas.adjustReputation(contract.issuer(), StationReputation.DELIVERY_DELTA);
            atlas.adjustReputation(contract.recipient(), StationReputation.DELIVERY_DELTA);
            int originRep = atlas.reputation(contract.issuer());
            int destRep = atlas.reputation(contract.recipient());
            paid = Math.max(1, Math.round(paid * StationReputation.rewardMultiplier(originRep, destRep)));
        }
        economy.deposit(courier.getUUID(), paid, (late ? "reward_late:" : "reward:") + id);
        economy.deposit(courier.getUUID(), escrow.takeCollateral(id), "collateral_back:" + id);
        List<ItemStack> deliveredCargo = escrow.takeItems(id);
        Optional<BlockPos> placedAt = Optional.empty();
        if (contract.type().isNpc()) {
            DeliveryPlacement.consumeNpcDelivery(server, contract);
        } else {
            DeliveryPlacement.Outcome placement =
                    DeliveryPlacement.restorePlayerDelivery(server, contract, deliveryAt, deliveredCargo);
            placedAt = placement.anchor();
        }

        int repeats = contract.type().isNpc() ? 0 : repeatsToday(courier.getUUID(), contract.issuer(), now);
        int xp = XpCalculator.xp(contract.distance(), contract.weight(), late, repeats);
        recentDeliveries.add(new Delivery(courier.getUUID(), contract.issuer(), now));
        CourierProfile profile = Licenses.profile(courier);
        LicenseRank rankBefore = RankRules.rankFor(profile);
        profile = contract.type().isNpc()
                ? profile.withVillageDelivery(xp, Math.round(contract.distance()), late, paid)
                : profile.withDelivery(xp, Math.round(contract.distance()), late, paid);
        if (contract.type().isNpc() && !profile.traineeAdvancePaid()
                && profile.villageDelivered() >= CouriersServerConfig.TRAINEE_FREE_CONTRACTS.get()) {
            long bonus = CouriersServerConfig.TRAINEE_BONUS.get();
            if (bonus > 0) {
                economy.deposit(courier.getUUID(), bonus, "trainee_advance:" + id);
                CouriersNotifications.notifyOrder(server, courier.getUUID(),
                        Component.translatable("contract.aerocouriers.trainee.advance", bonus), Optional.of(id));
            }
            profile = profile.withTraineeAdvancePaid();
        }
        Licenses.setProfile(courier, profile);
        LicenseRank rankAfter = RankRules.rankFor(profile);

        Contract delivered = contract.withState(ContractState.DELIVERED);
        put(delivered);
        Component message = Component.translatable(
                late ? "contract.aerocouriers.delivered_late" : "contract.aerocouriers.delivered",
                contract.shortId(), paid, xp);
        CouriersNotifications.notifyOrder(server, courier.getUUID(), message, Optional.of(id));
        if (rankAfter.level() > rankBefore.level() && !rankAfter.isAdmin()) {
            CouriersNotifications.notifyOrder(server, courier.getUUID(),
                    Component.translatable("contract.aerocouriers.notify.rank_up", rankAfter.displayName()),
                    Optional.of(id));
        }
        if (contract.type().isNpc()) {
            String stationName = StationAtlas.get(server).byId(contract.recipient())
                    .map(StationAtlas.Station::name)
                    .orElse("?");
            CouriersNotifications.notifyOrder(server, courier.getUUID(),
                    Component.translatable("contract.aerocouriers.notify.delivered_village",
                            contract.shortId(), stationName),
                    Optional.of(id));
        } else {
            String where = placedAt.map(BlockPos::toShortString).orElse("?");
            notify(server, contract.recipient(), Component.translatable("contract.aerocouriers.notify.delivered",
                    contract.shortId(), courier.getDisplayName(), where), id);
        }
        AeronauticsCouriers.LOGGER.info("[contract] {} delivered by {}: paid={}, late={}, xp={}, repeats={}",
                id, courier.getGameProfile().getName(), paid, late, xp, repeats);
        BoardPackets.syncSnapshot(courier);
        return Outcome.ok(delivered, message);
    }

    /** "Отказаться" (GDD §9.5). */
    public Outcome abandon(ServerPlayer courier, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null || !contract.isCourier(courier.getUUID())) {
            return Outcome.error("not_your_contract");
        }
        if (!contract.state().isActive()) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        return fail(courier.server, contract, FailReason.ABANDONED, null);
    }

    /** Issuer cancels; only while {@code OPEN} (GDD §5.2). The fee is not refunded. */
    public Outcome cancel(ServerPlayer issuer, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null || !contract.issuer().equals(issuer.getUUID())) {
            return Outcome.error("not_your_contract");
        }
        if (contract.state() != ContractState.OPEN) {
            return Outcome.error("cannot_cancel", contract.state().displayName());
        }
        return expire(issuer.server, contract);
    }

    /** Operator tool, same as a container that vanished: the cargo is lost. */
    public Outcome destroy(MinecraftServer server, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (!contract.state().isActive()) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        return fail(server, contract, FailReason.DESTROYED, null);
    }

    /**
     * Operator cancel with full refund: reward to the customer, collateral to the courier, cargo to the customer's
     * mailbox (player orders only).
     */
    public Outcome adminCancelWithRefund(MinecraftServer server, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.state().isFinal()) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        removePreplacedContainer(server, contract);
        CargoTracker.forget(id);
        Escrow escrow = Escrow.get(server);
        Economy economy = Economies.get(server);
        long reward = escrow.takeReward(id);
        long collateral = escrow.takeCollateral(id);
        List<ItemStack> items = escrow.takeItems(id);
        if (!contract.type().isNpc()) {
            economy.deposit(contract.issuer(), reward, "admin_cancel_reward:" + id);
            contract.courier().ifPresent(courier -> economy.deposit(courier, collateral, "admin_cancel_collateral:" + id));
            Mailbox.get(server).post(contract.issuer(), items);
        }
        Contract ended = adminEnded(contract);
        put(ended);
        Component message = Component.translatable("admin.aerocouriers.contract.cancelled", contract.shortId());
        contract.courier().ifPresent(courier -> notify(server, courier, message, id));
        if (!contract.type().isNpc()) {
            notify(server, contract.issuer(), message, id);
        }
        return Outcome.ok(ended, message);
    }

    /** Operator: add minutes to the open expiry or the active delivery deadline. */
    public Outcome extendDeadline(MinecraftServer server, UUID id, int minutes) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.state().isFinal()) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        long extra = minutes * Pricing.TICKS_PER_MINUTE;
        Contract.Timing timing = contract.timing();
        Contract.Timing next;
        if (contract.state() == ContractState.OPEN) {
            next = new Contract.Timing(timing.createdAt(), timing.expiresAt() + extra, timing.deadline(),
                    timing.acceptedAt(), timing.paused());
        } else if (contract.state().isActive()) {
            next = new Contract.Timing(timing.createdAt(), timing.expiresAt(), timing.deadline() + extra,
                    timing.acceptedAt(), timing.paused());
        } else {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        Contract updated = new Contract(contract.id(), contract.type(), contract.issuer(), contract.recipient(),
                contract.courier(), contract.origin(), contract.destination(), contract.size(), contract.weight(),
                contract.reward(), contract.collateral(), contract.requiredRank(), next, contract.state(),
                contract.preplaced(), contract.originSnapshot());
        put(updated);
        return Outcome.ok(updated, Component.translatable("admin.aerocouriers.contract.extended", contract.shortId(),
                minutes));
    }

    private static Contract adminEnded(Contract contract) {
        if (contract.state() == ContractState.OPEN) {
            return contract.withState(ContractState.EXPIRED);
        }
        return new Contract(contract.id(), contract.type(), contract.issuer(), contract.recipient(), contract.courier(),
                contract.origin(), contract.destination(), contract.size(), contract.weight(), contract.reward(),
                contract.collateral(), contract.requiredRank(), contract.timing(), ContractState.FAILED,
                contract.preplaced(), contract.originSnapshot());
    }

    /**
     * The container was broken or blown up (GDD §9.7). Part of the manifest drops at {@code site}; nothing drops
     * when the courier broke it.
     */
    public Outcome damage(UUID id, DropSite site) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("wrong_state", "—");
        }
        if (contract.preplaced() && contract.state() == ContractState.OPEN) {
            return expire(site.level().getServer(), contract);
        }
        if (contract.preplaced() && contract.state() == ContractState.ACCEPTED) {
            return fail(site.level().getServer(), contract, FailReason.DAMAGED, site);
        }
        if (contract.state() != ContractState.IN_TRANSIT) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        return fail(site.level().getServer(), contract, FailReason.DAMAGED, site);
    }

    /** A container block entity was removed without its chunk unloading (GDD §12: {@code /sable}, machines…). */
    public void containerRemoved(UUID id, long now) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return;
        }
        if (contract.preplaced() && contract.state() == ContractState.OPEN && server != null) {
            expire(server, contract);
            return;
        }
        if (contract.preplaced() && contract.state() == ContractState.ACCEPTED && server != null) {
            fail(server, contract, FailReason.DESTROYED, null);
            return;
        }
        if (contract.state() == ContractState.IN_TRANSIT) {
            missingSince.putIfAbsent(id, now);
        }
    }

    /**
     * GDD §9.5: collateral and the reward return to the issuer (owner's decision). Cargo goes to the courier if the
     * container already appeared, back to the issuer if it never did, is lost if destroyed, and partly drops into
     * the world if damaged. The state changes and escrow empties before anything reaches the world.
     */
    Outcome fail(MinecraftServer server, Contract contract, FailReason reason, @Nullable DropSite site) {
        UUID id = contract.id();
        UUID courier = contract.courier().orElseThrow();
        removePreplacedContainer(server, contract);
        Escrow escrow = Escrow.get(server);
        Economy economy = Economies.get(server);
        long seized = escrow.takeCollateral(id);
        long refunded = escrow.takeReward(id);
        List<ItemStack> items = escrow.takeItems(id);
        if (!contract.type().isNpc()) {
            economy.deposit(contract.issuer(), seized, "collateral_forfeit:" + id);
            economy.deposit(contract.issuer(), refunded, "reward_refund:" + id);
        } else {
            StationAtlas atlas = StationAtlas.get(server);
            atlas.adjustReputation(contract.issuer(), StationReputation.FAILURE_DELTA);
            atlas.adjustReputation(contract.recipient(), StationReputation.FAILURE_DELTA);
        }
        Contract failed = contract.withState(ContractState.FAILED);
        put(failed);
        stillChecks.remove(id);
        missingSince.remove(id);
        blockedNoticeAt.remove(id);
        CargoTracker.forget(id);

        boolean courierBroke = site != null && site.breaker() != null && site.breaker().getUUID().equals(courier);
        int dropped = 0;
        if (!contract.type().isNpc()) {
            switch (reason) {
                case DAMAGED -> {
                    if (site != null && !courierBroke) {
                        dropped = drop(site, items);
                    }
                }
                case DESTROYED -> {
                }
                case ABANDONED, TIMED_OUT -> {
                    UUID cargoTo = contract.state() == ContractState.IN_TRANSIT ? courier : contract.issuer();
                    Mailbox.get(server).post(cargoTo, items);
                }
            }
        }

        ServerPlayer online = findPlayer(server, courier);
        if (online != null) {
            Licenses.setProfile(online, Licenses.profile(online).withFailure());
        } else {
            pendingFailures.merge(courier, 1, Integer::sum);
        }

        Component cause = reason == FailReason.DAMAGED
                ? Component.translatable("contract.aerocouriers.fail.damaged",
                        site != null && site.breaker() != null ? site.breaker().getDisplayName()
                                : Component.translatable("contract.aerocouriers.fail.damaged.explosion"))
                : Component.translatable("contract.aerocouriers.fail." + reason.key());
        notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.failed.issuer",
                contract.shortId(), cause), id);
        if (online != null && reason != FailReason.ABANDONED) {
            CouriersNotifications.notifyOrder(server, courier,
                    Component.translatable("contract.aerocouriers.notify.failed.courier",
                            contract.shortId(), cause, contract.collateral()),
                    Optional.of(id));
        }
        AeronauticsCouriers.LOGGER.info("[contract] {} failed: {} (dropped {} items{})", id, reason, dropped,
                courierBroke ? ", broken by the courier" : "");
        return Outcome.ok(failed, "failed", contract.shortId(), cause);
    }

    private static int drop(DropSite site, List<ItemStack> items) {
        double ratio = CouriersServerConfig.PIRACY_DROP_RATIO.get();
        int dropped = 0;
        for (ItemStack stack : items) {
            int keep = (int) Math.floor(stack.getCount() * ratio);
            if (keep > 0) {
                Containers.dropItemStack(site.level(), site.position().x, site.position().y, site.position().z,
                        stack.copyWithCount(keep));
                dropped += keep;
            }
        }
        return dropped;
    }

    /**
     * GDD §5.2 {@code EXPIRED}: cargo and reward go back to the issuer. Stamp orders already converted the
     * source chest, so cargo always returns through the mailbox (the original container type is gone).
     */
    Outcome expire(MinecraftServer server, Contract contract) {
        UUID id = contract.id();
        removePreplacedContainer(server, contract);
        Escrow escrow = Escrow.get(server);
        long refund = escrow.takeReward(id);
        List<ItemStack> items = escrow.takeItems(id);
        if (!contract.type().isNpc()) {
            Economies.get(server).deposit(contract.issuer(), refund, "reward_refund:" + id);
            Mailbox.get(server).post(contract.issuer(), items);
        }
        Contract expired = contract.withState(ContractState.EXPIRED);
        put(expired);
        notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.expired",
                contract.shortId()), id);
        AeronauticsCouriers.LOGGER.info("[contract] {} expired", id);
        return Outcome.ok(expired, "expired", contract.shortId());
    }

    /** Drops the physical Sable crate of a stamp order without treating it as a destroyed in-transit container. */
    void removePreplacedContainer(MinecraftServer server, Contract contract) {
        if (!contract.preplaced()) {
            return;
        }
        UUID id = contract.id();
        CargoTracker.get(id).ifPresent(snapshot -> {
            ServerLevel level = server.getLevel(snapshot.dimension());
            if (level != null) {
                CargoSpawner.removeQuietly(level, snapshot.plotPos());
            }
        });
        ServerLevel origin = server.getLevel(contract.origin().dimension());
        if (origin != null && origin.isLoaded(contract.origin().pos())
                && origin.getBlockEntity(contract.origin().pos()) instanceof CargoContainerBlockEntity be
                && id.equals(be.contractId())) {
            CargoSpawner.removeQuietly(origin, contract.origin().pos());
        }
        CargoTracker.forget(id);
        missingSince.remove(id);
    }

    // ---- time ----

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK_INTERVAL == 0) {
            get(server).tick(server, now(server));
        }
    }

    public void tick(MinecraftServer server, long now) {
        double lateWindow = Pricing.lateWindow();
        int spawnRadius = CouriersServerConfig.SPAWN_TRIGGER_RADIUS.get();
        for (Contract contract : List.copyOf(contracts.values())) {
            UUID id = contract.id();
            if (contract.state() == ContractState.OPEN && now >= contract.timing().expiresAt()) {
                expire(server, contract);
            } else if (contract.state().isActive() && now > contract.timing().lateUntil(lateWindow)) {
                fail(server, contract, FailReason.TIMED_OUT, null);
            } else if (contract.state() == ContractState.ACCEPTED) {
                ServerPlayer courier = findPlayer(server, contract.courier().orElseThrow());
                if (courier != null
                        && contract.origin().within(courier.level().dimension(), courier.position(), spawnRadius)) {
                    spawnContainer(server, id);
                }
            } else if (contract.state() == ContractState.IN_TRANSIT && missingSince.containsKey(id)) {
                long since = missingSince.get(id);
                boolean reportedSince = CargoTracker.get(id).filter(s -> s.gameTime() > since).isPresent();
                if (reportedSince) {
                    missingSince.remove(id);
                } else if (now - since >= MISSING_GRACE_TICKS) {
                    fail(server, contract, FailReason.DESTROYED, null);
                }
            } else if (contract.state().isFinal() && now - contract.timing().createdAt() > HISTORY_TICKS
                    && Escrow.get(server).holding(id).isEmpty()) {
                contracts.remove(id);
                setDirty();
            }
        }
        if (recentDeliveries.removeIf(d -> now - d.time() > XpCalculator.REPEAT_WINDOW_TICKS)) {
            setDirty();
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContractManager manager = get(player.server);
            Integer failures = manager.pendingFailures.remove(player.getUUID());
            if (failures != null) {
                CourierProfile profile = Licenses.profile(player);
                for (int i = 0; i < failures; i++) {
                    profile = profile.withFailure();
                }
                Licenses.setProfile(player, profile);
                manager.setDirty();
            }
            manager.syncZones(player);
            CouriersNotifications.sync(player);
        }
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private int repeatsToday(UUID courier, UUID issuer, long now) {
        return (int) recentDeliveries.stream()
                .filter(d -> d.courier().equals(courier) && d.issuer().equals(issuer)
                        && now - d.time() <= XpCalculator.REPEAT_WINDOW_TICKS)
                .count();
    }

    private void put(Contract contract) {
        Contract previous = contracts.put(contract.id(), contract);
        setDirty();
        boolean changed = previous == null || previous.state() != contract.state();
        if (changed && server != null && contract.courier().isPresent()) {
            ServerPlayer courier = server.getPlayerList().getPlayer(contract.courier().get());
            if (courier != null) {
                syncZones(courier);
            }
        }
    }

    /** Sends the courier the zones of their active contracts for the particle outline (GDD §10.3). */
    public void syncZones(ServerPlayer courier) {
        if (!courier.connection.hasChannel(ZoneSyncPayload.TYPE)) {
            return;
        }
        List<ZoneSyncPayload.Zone> zones = byCourier(courier.getUUID())
                .filter(c -> c.state().isActive())
                .map(c -> {
                    boolean delivery = c.state() == ContractState.IN_TRANSIT;
                    Destination zone = delivery ? c.destination() : c.origin();
                    return new ZoneSyncPayload.Zone(c.id(), c.shortId(), zone.dimension(), zone.pos(), zone.radius(),
                            delivery);
                })
                .toList();
        PacketDistributor.sendToPlayer(courier, new ZoneSyncPayload(zones));
    }

    private static void notify(MinecraftServer server, UUID player, Component message, UUID contractId) {
        CouriersNotifications.notifyOrder(server, player, message, Optional.of(contractId));
    }

    // ---- persistence ----

    private static final Codec<Map<UUID, Integer>> PENDING_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

    public static ContractManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ContractManager manager = new ContractManager();
        Contract.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("contracts"))
                .resultOrPartial(error -> AeronauticsCouriers.LOGGER.error("Contracts: {}", error))
                .ifPresent(list -> list.forEach(c -> manager.contracts.put(c.id(), c)));
        PENDING_CODEC.parse(NbtOps.INSTANCE, tag.get("pending_failures")).result()
                .ifPresent(manager.pendingFailures::putAll);
        Delivery.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("recent_deliveries")).result()
                .ifPresent(manager.recentDeliveries::addAll);
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("contracts", Contract.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(contracts.values()))
                .getOrThrow());
        tag.put("pending_failures", PENDING_CODEC.encodeStart(NbtOps.INSTANCE, pendingFailures).getOrThrow());
        tag.put("recent_deliveries", Delivery.CODEC.listOf().encodeStart(NbtOps.INSTANCE, recentDeliveries).getOrThrow());
        return tag;
    }
}
