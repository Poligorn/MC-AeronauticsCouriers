package dev.poligorn.aerocouriers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.cargo.CargoContainerBlock;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseMigration;
import dev.poligorn.aerocouriers.license.LicenseOwner;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.registry.ModComponents;
import net.minecraft.core.Direction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.IntBinaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /couriers ...}. Player commands: {@code profile}, {@code ui}, {@code contract}, {@code mail}, {@code balance};
 * the rest are operator/debug tools.
 */
public final class CouriersCommands {
    private static final int OP = Commands.LEVEL_GAMEMASTERS;

    private CouriersCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("couriers");

        root.then(Commands.literal("profile")
                .executes(ctx -> showProfile(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(src -> src.hasPermission(OP))
                        .executes(ctx -> showProfile(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

        root.then(Commands.literal("license")
                .requires(src -> src.hasPermission(OP))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> giveLicense(ctx, null))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(LicenseRank.values()).map(LicenseRank::getSerializedName),
                                                builder))
                                        .executes(ctx -> giveLicense(ctx, StringArgumentType.getString(ctx, "rank"))))))
                .then(Commands.literal("migrate")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(CouriersCommands::migrate))));

        root.then(Commands.literal("xp")
                .requires(src -> src.hasPermission(OP))
                .then(Commands.literal("add").then(xpArgument(Integer::sum)))
                .then(Commands.literal("set").then(xpArgument((current, value) -> value))));

        root.then(Commands.literal("spawncargo")
                .requires(src -> src.hasPermission(OP))
                .executes(ctx -> spawnCargo(ctx, defaultCargoPos(ctx.getSource()), 0))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> spawnCargo(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), 0))
                        .then(Commands.argument("fill", IntegerArgumentType.integer(0, CargoContainerBlock.MAX_FILL))
                                .executes(ctx -> spawnCargo(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                        IntegerArgumentType.getInteger(ctx, "fill"))))));

        root.then(Commands.literal("cargo")
                .requires(src -> src.hasPermission(OP))
                .then(Commands.literal("list").executes(ctx -> listCargo(ctx.getSource()))));

        root.then(Commands.literal("ui")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BoardPackets.open(player);
                    return 1;
                }));

        var waybillGive = Commands.literal("give")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(CouriersCommands::giveWaybill));
        root.then(Commands.literal("waybill").requires(src -> src.hasPermission(OP)).then(waybillGive));
        root.then(Commands.literal("stamp").requires(src -> src.hasPermission(OP)).then(waybillGive));

        root.then(AdminCommands.admin());
        root.then(ContractCommands.contract());
        root.then(ContractCommands.mail());
        root.then(ContractCommands.balance());
        root.then(ContractCommands.money());
        root.then(StationCommands.station());

        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> xpArgument(
            IntBinaryOperator operation) {
        return Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("amount", IntegerArgumentType.integer())
                        .executes(ctx -> changeXp(ctx, operation)));
    }

    private static int showProfile(CommandSourceStack source, ServerPlayer player) {
        CourierProfile profile = Licenses.profile(player);
        LicenseRank rank = RankRules.rankFor(profile);
        OptionalInt next = RankRules.nextThreshold(rank);

        source.sendSuccess(() -> Component.translatable("commands.aerocouriers.profile.header",
                player.getDisplayName()), false);
        source.sendSuccess(() -> Component.translatable("commands.aerocouriers.profile.rank",
                rank.displayName(), profile.xp(),
                next.isPresent() ? String.valueOf(next.getAsInt()) : "—"), false);
        source.sendSuccess(() -> Component.translatable("commands.aerocouriers.profile.stats",
                profile.delivered(), profile.failed(), profile.late(),
                String.format(Locale.ROOT, "%.0f%%", profile.successRate() * 100), profile.distance()), false);

        Component licenseLine = Licenses.findLicense(player)
                .map(stack -> describeLicense(stack, player))
                .orElse(Component.translatable("commands.aerocouriers.profile.license.none"));
        source.sendSuccess(() -> licenseLine, false);
        source.sendSuccess(() -> Component.translatable("commands.aerocouriers.profile.issued",
                profile.licensesIssued(), profile.migrated() ? "✔" : "—"), false);
        return 1;
    }

    private static Component describeLicense(ItemStack stack, ServerPlayer wearer) {
        LicenseOwner owner = stack.get(ModComponents.LICENSE_OWNER.get());
        LicenseRank shown = stack.getOrDefault(ModComponents.LICENSE_RANK.get(), LicenseRank.RANK1);
        if (owner == null) {
            return Component.translatable("commands.aerocouriers.profile.license.unbound");
        }
        if (!owner.isOwnedBy(wearer.getUUID())) {
            return Component.translatable("commands.aerocouriers.profile.license.foreign", owner.name());
        }
        return Component.translatable("commands.aerocouriers.profile.license.own", owner.number(), shown.displayName());
    }

    private static int giveWaybill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            ItemStack waybills = dev.poligorn.aerocouriers.item.Waybills.stack(4);
            if (!target.getInventory().add(waybills)) {
                target.drop(waybills, false);
            }
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.aerocouriers.waybill.given",
                    target.getDisplayName()), true);
        }
        return targets.size();
    }

    private static int giveLicense(CommandContext<CommandSourceStack> ctx, String rankName) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            LicenseRank rank = rankName == null ? Licenses.rankOf(target) : LicenseRank.bySerializedName(rankName);
            ItemStack license = Licenses.issueLicense(target, rank, false);
            if (!target.getInventory().add(license)) {
                target.drop(license, false);
            }
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.aerocouriers.license.given",
                    target.getDisplayName(), rank.displayName()), true);
        }
        return targets.size();
    }

    private static int migrate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int total = 0;
        for (ServerPlayer target : EntityArgument.getPlayers(ctx, "targets")) {
            total += LicenseMigration.migrate(target);
        }
        int migrated = total;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.aerocouriers.license.migrated", migrated), true);
        return total;
    }

    private static int changeXp(CommandContext<CommandSourceStack> ctx, IntBinaryOperator operation)
            throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            CourierProfile profile = Licenses.profile(target);
            CourierProfile updated = profile.withXp(operation.applyAsInt(profile.xp(), amount));
            Licenses.setProfile(target, updated);
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.aerocouriers.xp.changed",
                    target.getDisplayName(), updated.xp(), RankRules.rankFor(updated).displayName()), true);
        }
        return targets.size();
    }

    private static BlockPos defaultCargoPos(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return player.blockPosition().relative(player.getDirection(), 3).above();
    }

    private static int spawnCargo(CommandContext<CommandSourceStack> ctx, BlockPos pos, int fill) {
        CommandSourceStack source = ctx.getSource();
        UUID contractId = UUID.randomUUID();
        ServerPlayer player = source.getPlayer();
        Direction facing = player != null ? player.getDirection().getOpposite() : Direction.NORTH;
        CargoSpawner.Result result = CargoSpawner.spawn(source.getLevel(), pos, CargoSize.S, fill, contractId, true,
                facing);
        return switch (result) {
            case CargoSpawner.Result.Spawned spawned -> {
                source.sendSuccess(() -> Component.translatable("commands.aerocouriers.spawncargo.success",
                        contractId.toString(), pos.toShortString(), fill, spawned.subLevelId().toString()), true);
                yield 1;
            }
            case CargoSpawner.Result.Occupied occupied -> {
                source.sendFailure(Component.translatable("commands.aerocouriers.spawncargo.occupied", pos.toShortString()));
                yield 0;
            }
            case CargoSpawner.Result.AssemblyFailed failed -> {
                source.sendFailure(Component.translatable("commands.aerocouriers.spawncargo.failed"));
                yield 0;
            }
        };
    }

    private static int listCargo(CommandSourceStack source) {
        var snapshots = CargoTracker.all();
        if (snapshots.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.aerocouriers.cargo.none"), false);
            return 0;
        }
        long now = source.getLevel().getGameTime();
        for (CargoTracker.Snapshot s : snapshots) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "%s%s %s (%.1f, %.1f, %.1f) |v|=%.2f b/s, %ds ago",
                    s.contractId().toString().substring(0, 8), s.debug() ? " [debug]" : "",
                    s.dimension().location(), s.position().x(), s.position().y(), s.position().z(),
                    s.velocity().length(), (now - s.gameTime()) / 20)), false);
        }
        return snapshots.size();
    }
}
