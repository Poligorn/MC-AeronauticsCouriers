package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * All contracts and their state machine (GDD §5.2, §7, §9.5–9.6). The only class that moves cargo and money for
 * contracts; every transition checks the current state first, so repeating a command can never pay twice.
 *
 * <p>Until the container loop exists (iteration 3) the courier marks the pickup with {@link #pickup} instead of a
 * spawned container, and {@link #deliver} checks the courier's own position.
 */
public final class ContractManager extends SavedData {
    private static final String NAME = "aerocouriers_contracts";
    /** Finished contracts stay visible in "Мои заказы" for this long, then are pruned. */
    private static final long HISTORY_TICKS = 30L * 24 * 60 * 60 * 20;
    /** The issuer must stand this close to the chest the cargo is taken from. */
    public static final double ISSUER_REACH = 8.0;

    public enum FailReason {
        ABANDONED, TIMED_OUT, DESTROYED;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Outcome(boolean success, Component message, Optional<Contract> contract) {
        static Outcome ok(Contract contract, String key, Object... args) {
            return new Outcome(true, Component.translatable("contract.aerocouriers." + key, args), Optional.of(contract));
        }

        static Outcome error(String key, Object... args) {
            return new Outcome(false, Component.translatable("contract.aerocouriers.error." + key, args), Optional.empty());
        }
    }

    private record Delivery(UUID courier, UUID issuer, long time) {
        static final Codec<Delivery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("courier").forGetter(Delivery::courier),
                UUIDUtil.CODEC.fieldOf("issuer").forGetter(Delivery::issuer),
                Codec.LONG.fieldOf("time").forGetter(Delivery::time)
        ).apply(instance, Delivery::new));
    }

    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();
    /** Failures of couriers who were offline when their contract failed; applied to the profile on login. */
    private final Map<UUID, Integer> pendingFailures = new HashMap<>();
    private final List<Delivery> recentDeliveries = new ArrayList<>();

    public static ContractManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ContractManager::new, ContractManager::load, null), NAME);
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
                new Contract.Timing(now, now + deadline, deadline, -1), ContractState.OPEN);
        Escrow.get(server).open(id, manifest, reward);
        put(contract);
        AeronauticsCouriers.LOGGER.info("[contract] {} created by {}: {} stacks, W={}, D={}, reward={}, fee={}, collateral={}",
                id, issuer.getGameProfile().getName(), manifest.size(), weight, (long) distance, reward, fee, collateral);
        return Outcome.ok(contract, "created", contract.shortId(), reward, fee,
                contract.requiredRank().displayName());
    }

    /** GDD §7.2: own contracts cannot be taken, collateral is withdrawn atomically. */
    public Outcome accept(ServerPlayer courier, UUID id) {
        MinecraftServer server = courier.server;
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (contract.state() != ContractState.OPEN || now(server) >= contract.timing().expiresAt()) {
            return Outcome.error("not_open");
        }
        if (contract.issuer().equals(courier.getUUID())) {
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
        notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.accepted",
                contract.shortId(), courier.getDisplayName()));
        return Outcome.ok(accepted, "accepted", contract.shortId(), contract.collateral(),
                accepted.timing().deadline() / Pricing.TICKS_PER_MINUTE);
    }

    /** Stand-in for the lazy container spawn (GDD §9.1): the courier reports at the pickup point. */
    public Outcome pickup(ServerPlayer courier, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null || !contract.isCourier(courier.getUUID())) {
            return Outcome.error("not_your_contract");
        }
        if (contract.state() != ContractState.ACCEPTED) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        int radius = CouriersServerConfig.SPAWN_TRIGGER_RADIUS.get();
        if (!contract.origin().within(courier.level().dimension(), courier.position(), radius)) {
            return Outcome.error("too_far_from_pickup", radius);
        }
        Contract inTransit = contract.withState(ContractState.IN_TRANSIT);
        put(inTransit);
        return Outcome.ok(inTransit, "picked_up", contract.shortId());
    }

    /** Successful delivery (GDD §9.3, §9.6). */
    public Outcome deliver(ServerPlayer courier, UUID id) {
        MinecraftServer server = courier.server;
        Contract contract = contracts.get(id);
        if (contract == null || !contract.isCourier(courier.getUUID())) {
            return Outcome.error("not_your_contract");
        }
        if (contract.state() != ContractState.IN_TRANSIT) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        if (!contract.destination().contains(courier.level().dimension(), courier.position())) {
            return Outcome.error("not_at_destination", contract.destination().pos().toShortString(),
                    contract.destination().radius());
        }
        long now = now(server);
        if (now > contract.timing().lateUntil(Pricing.lateWindow())) {
            fail(server, contract, FailReason.TIMED_OUT);
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
        notify(server, contract.recipient(), Component.translatable("contract.aerocouriers.notify.delivered",
                contract.shortId(), courier.getDisplayName()));
        AeronauticsCouriers.LOGGER.info("[contract] {} delivered by {}: paid={}, late={}, xp={}, repeats={}",
                id, courier.getGameProfile().getName(), paid, late, xp, repeats);
        return Outcome.ok(delivered, late ? "delivered_late" : "delivered", contract.shortId(), paid, xp);
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
        return fail(courier.server, contract, FailReason.ABANDONED);
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

    /** Operator tool and the future "container destroyed" path: the cargo is lost. */
    public Outcome destroy(MinecraftServer server, UUID id) {
        Contract contract = contracts.get(id);
        if (contract == null) {
            return Outcome.error("unknown");
        }
        if (!contract.state().isActive()) {
            return Outcome.error("wrong_state", contract.state().displayName());
        }
        return fail(server, contract, FailReason.DESTROYED);
    }

    /**
     * GDD §9.5: collateral and the unpaid reward return to the issuer. Cargo goes to the courier if they already
     * picked it up, back to the issuer if it never left, and is lost if the container was destroyed.
     */
    Outcome fail(MinecraftServer server, Contract contract, FailReason reason) {
        UUID id = contract.id();
        UUID courier = contract.courier().orElseThrow();
        Escrow escrow = Escrow.get(server);
        Economy economy = Economies.get(server);
        economy.deposit(contract.issuer(), escrow.takeCollateral(id), "collateral_forfeit:" + id);
        economy.deposit(contract.issuer(), escrow.takeReward(id), "reward_refund:" + id);
        List<ItemStack> items = escrow.takeItems(id);
        if (reason != FailReason.DESTROYED) {
            UUID cargoTo = contract.state() == ContractState.IN_TRANSIT ? courier : contract.issuer();
            Mailbox.get(server).post(cargoTo, items);
        }

        ServerPlayer online = server.getPlayerList().getPlayer(courier);
        if (online != null) {
            Licenses.setProfile(online, Licenses.profile(online).withFailure());
        } else {
            pendingFailures.merge(courier, 1, Integer::sum);
        }

        Contract failed = contract.withState(ContractState.FAILED);
        put(failed);
        Component reasonText = Component.translatable("contract.aerocouriers.fail." + reason.key());
        notify(server, contract.issuer(), Component.translatable("contract.aerocouriers.notify.failed",
                contract.shortId(), reasonText));
        if (online != null && reason != FailReason.ABANDONED) {
            online.sendSystemMessage(Component.translatable("contract.aerocouriers.notify.failed",
                    contract.shortId(), reasonText));
        }
        AeronauticsCouriers.LOGGER.info("[contract] {} failed: {}", id, reason);
        return Outcome.ok(failed, "failed", contract.shortId(), reasonText);
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
        if (server.getTickCount() % 20 == 0) {
            get(server).tick(server, now(server));
        }
    }

    public void tick(MinecraftServer server, long now) {
        double lateWindow = Pricing.lateWindow();
        for (Contract contract : List.copyOf(contracts.values())) {
            if (contract.state() == ContractState.OPEN && now >= contract.timing().expiresAt()) {
                expire(server, contract);
            } else if (contract.state().isActive() && now > contract.timing().lateUntil(lateWindow)) {
                fail(server, contract, FailReason.TIMED_OUT);
            } else if (contract.state().isFinal() && now - contract.timing().createdAt() > HISTORY_TICKS
                    && Escrow.get(server).holding(contract.id()).isEmpty()) {
                contracts.remove(contract.id());
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
        contracts.put(contract.id(), contract);
        setDirty();
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
