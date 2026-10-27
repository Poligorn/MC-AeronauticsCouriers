package dev.poligorn.aerocouriers.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Escrow;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Command-driven contracts for iteration 2 (GDD §16): {@code /couriers contract|mail|balance|money}. These are the
 * server-side intents the UI packets will call later, with the same checks.
 */
final class ContractCommands {
    private static final int OP = Commands.LEVEL_GAMEMASTERS;
    private static final int DEFAULT_RADIUS = 8;

    private ContractCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> contract() {
        return Commands.literal("contract")
                .then(Commands.literal("create")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("reward", LongArgumentType.longArg(0))
                                                .then(Commands.argument("collateral", LongArgumentType.longArg(0))
                                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(
                                                                        Pricing.MIN_DEADLINE_MINUTES, Pricing.MAX_DEADLINE_MINUTES))
                                                                .executes(ctx -> create(ctx, DEFAULT_RADIUS))
                                                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                                                        .executes(ctx -> create(ctx,
                                                                                IntegerArgumentType.getInteger(ctx, "radius"))))))))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").then(idArgument().executes(ContractCommands::info)))
                .then(action("accept", ContractManager::accept))
                .then(action("abandon", ContractManager::abandon))
                .then(action("cancel", ContractManager::cancel))
                .then(operator("forcespawn", ContractManager::spawnContainer))
                .then(operator("forcedeliver", ContractManager::forceDeliver))
                .then(operator("fail", ContractManager::destroy));
    }

    private interface OperatorAction {
        ContractManager.Outcome apply(ContractManager manager, MinecraftServer server, UUID id);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> operator(String name, OperatorAction action) {
        return Commands.literal(name)
                .requires(src -> src.hasPermission(OP))
                .then(idArgument().executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    Optional<Contract> contract = resolve(ctx);
                    if (contract.isEmpty()) {
                        return unknown(ctx.getSource());
                    }
                    return report(ctx.getSource(), action.apply(ContractManager.get(server), server, contract.get().id()));
                }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> mail() {
        return Commands.literal("mail")
                .executes(ctx -> showMail(ctx.getSource()))
                .then(Commands.literal("claim").executes(ctx -> claimMail(ctx.getSource())));
    }

    static LiteralArgumentBuilder<CommandSourceStack> balance() {
        return Commands.literal("balance")
                .executes(ctx -> showBalance(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(src -> src.hasPermission(OP))
                        .executes(ctx -> showBalance(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> money() {
        return Commands.literal("money")
                .requires(src -> src.hasPermission(OP))
                .then(Commands.literal("add").then(moneyArgument(false)))
                .then(Commands.literal("set").then(moneyArgument(true)));
    }

    // ---- contract ----

    private interface Action {
        ContractManager.Outcome apply(ContractManager manager, ServerPlayer player, UUID id);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, Action action) {
        return Commands.literal(name).then(idArgument().executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Optional<Contract> contract = resolve(ctx);
            if (contract.isEmpty()) {
                return unknown(ctx.getSource());
            }
            return report(ctx.getSource(), action.apply(ContractManager.get(player.server), player, contract.get().id()));
        }));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ContractManager.get(ctx.getSource().getServer()).all()
                                .filter(c -> !c.state().isFinal())
                                .map(Contract::shortId), builder));
    }

    private static Optional<Contract> resolve(CommandContext<CommandSourceStack> ctx) {
        return ContractManager.get(ctx.getSource().getServer()).find(StringArgumentType.getString(ctx, "id"));
    }

    private static int create(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer issuer = source.getPlayerOrException();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");
        Container container = HopperBlockEntity.getContainerAt(issuer.level(), from);
        if (container == null) {
            source.sendFailure(Component.translatable("contract.aerocouriers.error.no_container", from.toShortString()));
            return 0;
        }
        Destination destination = new Destination(issuer.level().dimension(), to, radius);
        return report(source, ContractManager.get(issuer.server).create(issuer, container, from, destination,
                LongArgumentType.getLong(ctx, "reward"), LongArgumentType.getLong(ctx, "collateral"),
                IntegerArgumentType.getInteger(ctx, "minutes")));
    }

    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ContractManager manager = ContractManager.get(source.getServer());
        List<Contract> shown = manager.all()
                .filter(c -> !c.state().isFinal())
                .filter(c -> c.state() == ContractState.OPEN || c.isCourier(player.getUUID())
                        || c.issuer().equals(player.getUUID()))
                .toList();
        if (shown.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("contract.aerocouriers.list.none"), false);
            return 0;
        }
        for (Contract c : shown) {
            source.sendSuccess(() -> summary(c, player.getUUID()), false);
        }
        return shown.size();
    }

    private static Component summary(Contract c, UUID viewer) {
        String role = c.roleFor(viewer);
        return Component.translatable("contract.aerocouriers.list.line", c.shortId(), c.state().displayName(),
                c.origin().pos().toShortString(), c.destination().pos().toShortString(), (long) c.distance(),
                c.size().name(), c.reward(), c.collateral(), c.requiredRank().displayName(),
                Component.translatable("contract.aerocouriers.role." + role));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Contract> found = resolve(ctx);
        if (found.isEmpty()) {
            return unknown(source);
        }
        Contract c = found.get();
        MinecraftServer server = source.getServer();
        long now = ContractManager.now(server);
        source.sendSuccess(() -> summary(c, source.getPlayer() == null ? new UUID(0, 0) : source.getPlayer().getUUID()), false);
        Component timeLeft = c.state().isActive()
                ? minutesComponent(c.timing().dueAt() - now)
                : c.state() == ContractState.OPEN
                        ? minutesComponent(c.timing().expiresAt() - now)
                        : Component.translatable("ui.aerocouriers.profile.dash");
        source.sendSuccess(() -> Component.translatable("contract.aerocouriers.info.details",
                String.format(Locale.ROOT, "%.1f", c.weight()), c.size().maxWeight(),
                minutesComponent(c.timing().deadline()), timeLeft), false);
        source.sendSuccess(() -> ContractManager.riskLine(c), false);
        Escrow.Holding holding = Escrow.get(server).holding(c.id());
        for (ItemStack stack : holding.items()) {
            source.sendSuccess(() -> Component.translatable("commands.aerocouriers.manifest.line", stack.getCount())
                    .append(stack.getHoverName()), false);
        }
        return 1;
    }

    private static Component minutesComponent(long ticks) {
        long minutes = Math.max(0, ticks / Pricing.TICKS_PER_MINUTE);
        return Component.translatable("contract.aerocouriers.time.minutes", minutes);
    }

    private static int report(CommandSourceStack source, ContractManager.Outcome outcome) {
        if (outcome.success()) {
            source.sendSuccess(outcome::message, true);
            return 1;
        }
        source.sendFailure(outcome.message());
        return 0;
    }

    private static int unknown(CommandSourceStack source) {
        source.sendFailure(Component.translatable("contract.aerocouriers.error.unknown"));
        return 0;
    }

    // ---- mail ----

    private static int showMail(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<ItemStack> contents = Mailbox.get(source.getServer()).contents(player.getUUID());
        source.sendSuccess(() -> Component.translatable("contract.aerocouriers.mail.header", contents.size()), false);
        for (ItemStack stack : contents) {
            source.sendSuccess(() -> Component.translatable("commands.aerocouriers.manifest.line", stack.getCount())
                    .append(stack.getHoverName()), false);
        }
        return contents.size();
    }

    private static int claimMail(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Mailbox mailbox = Mailbox.get(source.getServer());
        int moved = mailbox.claim(player.getUUID(), player.getInventory());
        int left = mailbox.contents(player.getUUID()).size();
        source.sendSuccess(() -> Component.translatable("contract.aerocouriers.mail.claimed", moved, left), false);
        return moved;
    }

    // ---- money ----

    private static int showBalance(CommandSourceStack source, ServerPlayer player) {
        long balance = Economies.get(source.getServer()).balance(player.getUUID());
        source.sendSuccess(() -> Component.translatable("contract.aerocouriers.balance", player.getDisplayName(), balance), false);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> moneyArgument(boolean set) {
        return Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                        .executes(ctx -> changeMoney(ctx, set ? (current, amount) -> amount : Long::sum)));
    }

    private static int changeMoney(CommandContext<CommandSourceStack> ctx, BiFunction<Long, Long, Long> operation)
            throws CommandSyntaxException {
        long amount = LongArgumentType.getLong(ctx, "amount");
        Economy economy = Economies.get(ctx.getSource().getServer());
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            long current = economy.balance(target.getUUID());
            long wanted = operation.apply(current, amount);
            if (wanted > current) {
                economy.deposit(target.getUUID(), wanted - current, "admin");
            } else if (wanted < current) {
                economy.withdraw(target.getUUID(), current - wanted, "admin");
            }
            ctx.getSource().sendSuccess(() -> Component.translatable("contract.aerocouriers.balance",
                    target.getDisplayName(), economy.balance(target.getUUID())), true);
        }
        return targets.size();
    }
}
