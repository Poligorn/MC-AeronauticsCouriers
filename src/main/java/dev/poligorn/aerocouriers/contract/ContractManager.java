package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /** What the courier loses on failure — shown before and on acceptance (owner's decision, GDD §7.2). */
    public static Component riskLine(Contract contract) {
        return Component.translatable("contract.aerocouriers.risk", contract.collateral(), contract.reward());
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

        if (byIssuer(issuer.getUUID()).filter(c -> !c.state().isFinal()).count()
                >= CouriersServerConfig.MAX_OPEN_PER_PLAYER.get()) {
            return Outcome.error("too_many_open", CouriersServerConfig.MAX_OPEN_PER_PLAYER.get());
        }
        if (deadlineMinutes < Pricing.MIN_DEADLINE_MINUTES || deadlineMinutes > Pricing.MAX_DEADLINE_MINUTES) {
            return Outcome.error("deadline", Pricing.MIN_DEADLINE_MINUTES, Pricing.MAX_DEADLINE_MINUTES);
        }
        if (!origin.dimension().equals(destination.dimension())) {
            return Outcome.error("dimension");
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

        double distance = origin.distanceTo(destination);
        long minReward = Math.max(1, Pricing.minReward(distance));
        if (reward < minReward) {
            return Outcome.error("reward_too_low", minReward);
        }
        if (collateral < 0) {
            return Outcome.error("negative_collateral");
        }

        UUID id = UUID.randomUUID();
        long fee = Pricing.fee(reward);
        Economy economy = Economies.get(server);
        if (!economy.withdraw(issuer.getUUID(), reward + fee, "create:" + id)) {
            return Outcome.error("not_enough_money", reward + fee, economy.balance(issuer.getUUID()));
        }
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            source.setItem(slot, ItemStack.EMPTY);
        }
        source.setChanged();

        long deadline = deadlineMinutes * Pricing.TICKS_PER_MINUTE;
        Contract contract = new Contract(id, ContractType.PLAYER, issuer.getUUID(), issuer.getUUID(), Optional.empty(),
                origin, destination, size.get(), weight, reward, collateral,
                RankRules.requiredRank(collateral, size.get(), distance),
                new Contract.Timing(now, now + deadline, deadline, -1, 0), ContractState.OPEN);
        Escrow.get(server).open(id, manifest, reward);
        put(contract);
        AeronauticsCouriers.LOGGER.info("[contract] {} created by {}: {} stacks, W={}, D={}, reward={}, fee={}, collateral={}",
                id, issuer.getGameProfile().getName(), manifest.size(), weight, (long) distance, reward, fee, collateral);
        return Outcome.ok(contract, "created", contract.shortId(), reward, fee,
                contract.requiredRank().displayName());
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
        if (!RankRules.satisfies(rank, contract.requiredRank())) {
            return Outcome.error("rank_too_low", contract.requiredRank().displayName());
        }
        int maxActive = RankRules.limits(rank).maxActive();
        if (byCourier(courier.getUUID()).filter(c -> c.state().isActive()).count() >= maxActive) {
            return Outcome.error("too_many_active", maxActive);
        }
        Economy economy = Economies.get(server);
        if (!economy.withdraw(courier.getUUID(), contract.collateral(), "collateral:" + id)) {
            return Outcome.error("not_enough_money", contract.collateral(), economy.balance(courier.getUUID()));
        }
        Escrow.get(server).addCollateral(id, contract.collateral());
        Contract accepted = contract.accepted(courier.getUUID(), now(server));
        put(accepted);
        if (!self) {
            notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.accepted",
                    contract.shortId(), courier.getDisplayName()));
        }
        Component message = Component.translatable("contract.aerocouriers.accepted", contract.shortId(),
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
        if (contract.state() != ContractState.ACCEPTED) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        ServerLevel level = server.getLevel(contract.origin().dimension());
        if (level == null || !level.isLoaded(contract.origin().pos())) {
            return Outcome.error("pickup_not_loaded");
        }
        Optional<BlockPos> spot = CargoSpawner.findSpot(level, contract.origin().pos(),
                CouriersServerConfig.SPAWN_SEARCH_RADIUS.get());
        if (spot.isEmpty()) {
            put(contract.withPause(TICK_INTERVAL));
            long now = now(server);
            Long last = blockedNoticeAt.get(id);
            if (last == null || now - last >= BLOCKED_NOTICE_INTERVAL) {
                blockedNoticeAt.put(id, now);
                Component notice = Component.translatable("contract.aerocouriers.notify.pickup_blocked",
                        contract.shortId(), contract.origin().pos().toShortString());
                notify(server, contract.issuer(), notice);
                notify(server, contract.courier().orElseThrow(), notice);
            }
            return Outcome.error("pickup_blocked", contract.origin().pos().toShortString());
        }
        int fill = contract.size().fillFor(contract.weight());
        CargoSpawner.Result result = CargoSpawner.spawn(level, spot.get(), fill, id, false);
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
                "contract.aerocouriers.notify.container_arrived", contract.shortId(), spot.get().toShortString()));
        return Outcome.ok(inTransit, "container_spawned", contract.shortId(), spot.get().toShortString());
    }

    /**
     * Once a second from the container block entity (GDD §9.3). Delivers after {@code delivery_still_checks}
     * consecutive checks inside the destination zone, slower than {@code delivery_max_speed}, with the courier
     * within {@link #COURIER_REACH}; fails as destroyed below the world.
     */
    public Verdict checkContainer(ServerLevel level, UUID id, Vector3dc world, double speed) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.state() != ContractState.IN_TRANSIT) {
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
        completeDelivery(server, contract, courier);
        return Verdict.REMOVE;
    }

    /** Operator tool: delivers an in-transit contract wherever the container is; the courier must be online. */
    public Outcome forceDeliver(MinecraftServer server, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.state() != ContractState.IN_TRANSIT) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        ServerPlayer courier = findPlayer(server, contract.courier().orElseThrow());
        if (courier == null) {
            return Outcome.error("courier_offline");
        }
        return completeDelivery(server, contract, courier);
    }

    /** Successful delivery (GDD §9.3, §9.6). */
    private Outcome completeDelivery(MinecraftServer server, Contract contract, ServerPlayer courier) {
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
        economy.deposit(courier.getUUID(), paid, (late ? "reward_late:" : "reward:") + id);
        economy.deposit(courier.getUUID(), escrow.takeCollateral(id), "collateral_back:" + id);
        Mailbox.get(server).post(contract.recipient(), escrow.takeItems(id));

        int repeats = repeatsToday(courier.getUUID(), contract.issuer(), now);
        int xp = XpCalculator.xp(contract.distance(), contract.weight(), late, repeats);
        recentDeliveries.add(new Delivery(courier.getUUID(), contract.issuer(), now));
        CourierProfile profile = Licenses.profile(courier);
        Licenses.setProfile(courier, profile.withDelivery(xp, Math.round(contract.distance()), late));

        Contract delivered = contract.withState(ContractState.DELIVERED);
        put(delivered);
        CargoTracker.forget(id);
        Component message = Component.translatable(
                late ? "contract.aerocouriers.delivered_late" : "contract.aerocouriers.delivered",
                contract.shortId(), paid, xp);
        courier.sendSystemMessage(message);
        notify(server, contract.recipient(), Component.translatable("contract.aerocouriers.notify.delivered",
                contract.shortId(), courier.getDisplayName()));
        AeronauticsCouriers.LOGGER.info("[contract] {} delivered by {}: paid={}, late={}, xp={}, repeats={}",
                id, courier.getGameProfile().getName(), paid, late, xp, repeats);
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
     * The container was broken or blown up (GDD §9.7). Part of the manifest drops at {@code site}; nothing drops
     * when the courier broke it.
     */
    public Outcome damage(UUID id, DropSite site) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.state() != ContractState.IN_TRANSIT) {
            return Outcome.error("wrong_state", contract == null ? "—" : contract.state().displayName());
        }
        return fail(site.level().getServer(), contract, FailReason.DAMAGED, site);
    }

    /** A container block entity was removed without its chunk unloading (GDD §12: {@code /sable}, machines…). */
    public void containerRemoved(UUID id, long now) {
        Contract contract = contracts.get(id);
        if (contract != null && contract.state() == ContractState.IN_TRANSIT) {
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
        Escrow escrow = Escrow.get(server);
        Economy economy = Economies.get(server);
        economy.deposit(contract.issuer(), escrow.takeCollateral(id), "collateral_forfeit:" + id);
        economy.deposit(contract.issuer(), escrow.takeReward(id), "reward_refund:" + id);
        List<ItemStack> items = escrow.takeItems(id);
        Contract failed = contract.withState(ContractState.FAILED);
        put(failed);
        stillChecks.remove(id);
        missingSince.remove(id);
        blockedNoticeAt.remove(id);
        CargoTracker.forget(id);

        boolean courierBroke = site != null && site.breaker() != null && site.breaker().getUUID().equals(courier);
        int dropped = 0;
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
                contract.shortId(), cause));
        if (online != null && reason != FailReason.ABANDONED) {
            online.sendSystemMessage(Component.translatable("contract.aerocouriers.notify.failed.courier",
                    contract.shortId(), cause, contract.collateral()));
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

    /** GDD §5.2 {@code EXPIRED}: cargo and reward go back to the issuer. */
    Outcome expire(MinecraftServer server, Contract contract) {
        UUID id = contract.id();
        Escrow escrow = Escrow.get(server);
        Economies.get(server).deposit(contract.issuer(), escrow.takeReward(id), "reward_refund:" + id);
        Mailbox.get(server).post(contract.issuer(), escrow.takeItems(id));
        Contract expired = contract.withState(ContractState.EXPIRED);
        put(expired);
        notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.expired",
                contract.shortId()));
        AeronauticsCouriers.LOGGER.info("[contract] {} expired", id);
        return Outcome.ok(expired, "expired", contract.shortId());
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
                    return new ZoneSyncPayload.Zone(c.shortId(), zone.dimension(), zone.pos(), zone.radius(), delivery);
                })
                .toList();
        PacketDistributor.sendToPlayer(courier, new ZoneSyncPayload(zones));
    }

    private static void notify(MinecraftServer server, UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(message);
        }
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
