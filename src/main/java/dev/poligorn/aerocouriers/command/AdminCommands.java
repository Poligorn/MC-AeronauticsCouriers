package dev.poligorn.aerocouriers.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.admin.AdminActions;
import dev.poligorn.aerocouriers.config.CouriersConfigEditor;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ItemValues;
import dev.poligorn.aerocouriers.contract.ItemWeights;
import dev.poligorn.aerocouriers.contract.WorldCargoOverrides;
import dev.poligorn.aerocouriers.contract.Weights;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.item.Waybills;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** {@code /couriers admin ...} — mirrors the tablet admin panel (permission 2). */
public final class AdminCommands {
    private static final int OP = Commands.LEVEL_GAMEMASTERS;

    private AdminCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("admin")
                .requires(src -> src.hasPermission(OP))
                .then(contractBranch())
                .then(playerBranch())
                .then(Commands.literal("resetlicense")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(AdminCommands::playerResetLicense)))
                .then(economyBranch())
                .then(serverBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> contractBranch() {
        var id = Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ContractManager.get(ctx.getSource().getServer()).all()
                                .filter(c -> c.state().isActive() || c.state() == ContractState.OPEN)
                                .map(Contract::shortId), builder));
        return Commands.literal("contract")
                .then(Commands.literal("list").executes(AdminCommands::contractList))
                .then(Commands.literal("spawn").then(id.executes(ctx -> contractOp(ctx, AdminActions::forceSpawn))))
                .then(Commands.literal("deliver").then(id.executes(ctx -> contractOp(ctx, AdminActions::forceDeliver))))
                .then(Commands.literal("fail").then(id.executes(ctx -> contractOp(ctx, AdminActions::forceFail))))
                .then(Commands.literal("cancel").then(id.executes(ctx -> contractOp(ctx, AdminActions::adminCancel))))
                .then(Commands.literal("extend").then(id.executes(ctx -> contractOp(ctx, AdminActions::extend))))
                .then(Commands.literal("tp").then(id.executes(ctx -> contractOp(ctx, AdminActions::teleportToContract))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerBranch() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("player");
        root.then(Commands.literal("rank").then(target()
                .then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(LicenseRank.values()).map(LicenseRank::getSerializedName), b))
                        .executes(AdminCommands::playerRank))));
        LiteralArgumentBuilder<CommandSourceStack> xp = Commands.literal("xp");
        xp.then(Commands.literal("add").then(target().then(Commands.argument("amount", IntegerArgumentType.integer())
                .executes(ctx -> playerXp(ctx, true)))));
        xp.then(Commands.literal("set").then(target().then(Commands.argument("amount", IntegerArgumentType.integer())
                .executes(ctx -> playerXp(ctx, false)))));
        root.then(xp);
        root.then(Commands.literal("rep").then(target()
                .then(Commands.argument("station", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                StationAtlas.get(ctx.getSource().getServer()).all().stream().map(
                                        StationAtlas.Station::name), b))
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(AdminCommands::playerRep)))));
        root.then(Commands.literal("license").then(target()
                .then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(LicenseRank.values()).map(LicenseRank::getSerializedName), b))
                        .executes(AdminCommands::playerLicense))));
        root.then(Commands.literal("waybill").then(target().executes(AdminCommands::playerWaybill)));
        root.then(Commands.literal("money").then(target().then(Commands.argument("amount", LongArgumentType.longArg(0))
                .executes(AdminCommands::playerMoney))));
        return root;
    }

    // Brigadier merges children of a reused builder, so every branch needs its own argument node.
    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> target() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> economyBranch() {
        return Commands.literal("economy")
                .then(Commands.literal("reload").executes(AdminCommands::economyReload))
                .then(Commands.literal("value")
                        .executes(ctx -> economyHand(ctx, true))
                        .then(Commands.argument("spurs", LongArgumentType.longArg(0))
                                .executes(ctx -> economySetValue(ctx, LongArgumentType.getLong(ctx, "spurs")))))
                .then(Commands.literal("weight")
                        .executes(ctx -> economyHand(ctx, false))
                        .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0))
                                .executes(ctx -> economySetWeight(ctx, DoubleArgumentType.getDouble(ctx, "weight")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> serverBranch() {
        return Commands.literal("server")
                .then(Commands.literal("regen").executes(AdminCommands::serverRegen))
                .then(Commands.literal("config")
                        .then(Commands.literal("list").executes(AdminCommands::configList))
                        .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(AdminCommands::configGet)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(AdminCommands::configSet)))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("allow_self_accept")
                                .executes(ctx -> debugToggle(ctx, "allow_self_accept"))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSet(ctx, "allow_self_accept",
                                                BoolArgumentType.getBool(ctx, "value"))))));
    }

    private static int contractList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        var active = ContractManager.get(server).all()
                .filter(c -> c.state().isActive() || c.state() == ContractState.OPEN)
                .toList();
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("admin.aerocouriers.contract.list.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("admin.aerocouriers.contract.list.header", active.size()), false);
        for (Contract c : active) {
            String courier = c.courier().flatMap(id -> server.getProfileCache().get(id).map(p -> p.getName()))
                    .orElse("—");
            source.sendSuccess(() -> Component.translatable("admin.aerocouriers.contract.list.line",
                    c.shortId(), c.state().getSerializedName(), c.size().name(), courier), false);
        }
        return active.size();
    }

    @FunctionalInterface
    private interface ContractOp {
        ContractManager.Outcome run(MinecraftServer server, UUID id, ServerPlayer actor);
    }

    private static int contractOp(CommandContext<CommandSourceStack> ctx, ContractOp op) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer actor = source.getPlayerOrException();
        Optional<Contract> found = ContractManager.get(source.getServer()).find(StringArgumentType.getString(ctx, "id"));
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("contract.aerocouriers.error.unknown"));
            return 0;
        }
        return report(source, op.run(source.getServer(), found.get().id(), actor));
    }

    private static int playerRank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        LicenseRank rank = LicenseRank.bySerializedName(StringArgumentType.getString(ctx, "rank"));
        return report(ctx.getSource(), AdminActions.grantLicense(target, rank));
    }

    private static int playerXp(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CourierProfile profile = Licenses.profile(target);
        int next = add ? profile.xp() + amount : amount;
        Licenses.setProfile(target, profile.withXp(next));
        return report(ctx.getSource(), new ContractManager.Outcome(true,
                Component.translatable("commands.aerocouriers.xp.changed", target.getDisplayName(), next,
                        RankRules.rankFor(Licenses.profile(target)).displayName()),
                Optional.empty()));
    }

    private static int playerRep(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String stationName = StringArgumentType.getString(ctx, "station");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        StationAtlas atlas = StationAtlas.get(ctx.getSource().getServer());
        Optional<StationAtlas.Station> station = atlas.all().stream()
                .filter(s -> s.name().equalsIgnoreCase(stationName))
                .findFirst();
        if (station.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("admin.aerocouriers.player.station_unknown", stationName));
            return 0;
        }
        atlas.setReputation(station.get().id(), value);
        return report(ctx.getSource(), new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.player.rep", target.getDisplayName(), stationName, value),
                Optional.empty()));
    }

    private static int playerLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        LicenseRank rank = LicenseRank.bySerializedName(StringArgumentType.getString(ctx, "rank"));
        return report(ctx.getSource(), AdminActions.grantLicense(target, rank));
    }

    private static int playerWaybill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemStack stack = Waybills.stack(4);
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        return report(ctx.getSource(), new ContractManager.Outcome(true,
                Component.translatable("commands.aerocouriers.waybill.given", target.getDisplayName()),
                Optional.empty()));
    }

    private static int playerMoney(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        Economies.get(ctx.getSource().getServer()).deposit(target.getUUID(), amount, "admin");
        long balance = Economies.get(ctx.getSource().getServer()).balance(target.getUUID());
        return report(ctx.getSource(), new ContractManager.Outcome(true,
                Component.translatable("contract.aerocouriers.balance", target.getDisplayName(),
                        dev.poligorn.aerocouriers.economy.MoneyFormat.component(balance)),
                Optional.empty()));
    }

    private static int playerResetLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return report(ctx.getSource(), AdminActions.resetLicense(target));
    }

    private static int economyReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getServer().reloadResources(ctx.getSource().getServer().getPackRepository().getSelectedIds());
        return report(ctx.getSource(), AdminActions.reloadDatapacks(ctx.getSource().getServer()));
    }

    private static int economyHand(CommandContext<CommandSourceStack> ctx, boolean value) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("admin.aerocouriers.economy.empty_hand"));
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(hand.getItem());
        if (value) {
            long v = dev.poligorn.aerocouriers.economy.MoneyScale.fromSpurs(ItemValues.perItem(hand));
            ctx.getSource().sendSuccess(() -> Component.translatable("admin.aerocouriers.economy.value_show", id,
                    dev.poligorn.aerocouriers.economy.MoneyFormat.component(v)), false);
        } else {
            double w = Weights.perItem(hand);
            ctx.getSource().sendSuccess(() -> Component.translatable("admin.aerocouriers.economy.weight_show", id, w), false);
        }
        return 1;
    }

    private static int economySetValue(CommandContext<CommandSourceStack> ctx, long spurs) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("admin.aerocouriers.economy.empty_hand"));
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(hand.getItem());
        WorldCargoOverrides.get(ctx.getSource().getServer()).setValue(id, spurs);
        return report(ctx.getSource(), new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.economy.value_set", id, spurs), Optional.empty()));
    }

    private static int economySetWeight(CommandContext<CommandSourceStack> ctx, double weight) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("admin.aerocouriers.economy.empty_hand"));
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(hand.getItem());
        WorldCargoOverrides.get(ctx.getSource().getServer()).setWeight(id, weight);
        return report(ctx.getSource(), new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.economy.weight_set", id, weight), Optional.empty()));
    }

    private static int serverRegen(CommandContext<CommandSourceStack> ctx) {
        return report(ctx.getSource(), AdminActions.regenerateVillageBoards(ctx.getSource().getServer()));
    }

    private static int configList(CommandContext<CommandSourceStack> ctx) {
        for (CouriersConfigEditor.KeyInfo key : CouriersConfigEditor.listCommon()) {
            ctx.getSource().sendSuccess(() -> Component.literal(key.path() + " = " + key.current()), false);
        }
        return CouriersConfigEditor.listCommon().size();
    }

    private static int configGet(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        Optional<String> value = CouriersConfigEditor.get(key);
        if (value.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("admin.aerocouriers.config.unknown", key));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value.get()), false);
        return 1;
    }

    private static int configSet(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        Optional<Component> err = CouriersConfigEditor.set(key, value);
        if (err.isPresent()) {
            ctx.getSource().sendFailure(err.get());
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("admin.aerocouriers.config.set", key, value), true);
        return 1;
    }

    private static int debugToggle(CommandContext<CommandSourceStack> ctx, String key) {
        if (!"allow_self_accept".equals(key)) {
            ctx.getSource().sendFailure(Component.translatable("admin.aerocouriers.config.unknown", key));
            return 0;
        }
        CouriersConfigEditor.toggleDebugAllowSelfAccept();
        boolean on = dev.poligorn.aerocouriers.config.CouriersServerConfig.ALLOW_SELF_ACCEPT.get();
        ctx.getSource().sendSuccess(() -> Component.translatable("admin.aerocouriers.debug.toggled", key, on), true);
        return 1;
    }

    private static int debugSet(CommandContext<CommandSourceStack> ctx, String key, boolean value) {
        Optional<Component> err = CouriersConfigEditor.set("debug." + key, String.valueOf(value));
        if (err.isPresent()) {
            ctx.getSource().sendFailure(err.get());
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("admin.aerocouriers.debug.toggled", key, value), true);
        return 1;
    }

    private static int report(CommandSourceStack source, ContractManager.Outcome outcome) {
        if (outcome.success()) {
            source.sendSuccess(outcome::message, true);
            return 1;
        }
        source.sendFailure(outcome.message());
        return 0;
    }
}
