package dev.poligorn.aerocouriers.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code /couriers dev seed [balance]}: fills the board with stations, station orders and orders from a fake player
 * «Tester2» for UI checks. Registered only outside production (dev runs from the IDE / runClient).
 */
final class DevSeedCommands {
    static final String FAKE_ISSUER_NAME = "Tester2";
    private static final UUID FAKE_ISSUER = UUID.nameUUIDFromBytes(
            ("aerocouriers-dev:" + FAKE_ISSUER_NAME).getBytes(StandardCharsets.UTF_8));

    private DevSeedCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> dev() {
        return Commands.literal("dev")
                .requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("seed")
                        .executes(ctx -> seed(ctx.getSource(), 989))
                        .then(Commands.argument("balance", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                                .executes(ctx -> seed(ctx.getSource(),
                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "balance")))));
    }

    private static int seed(CommandSourceStack source, long balance) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        server.getProfileCache().add(new GameProfile(FAKE_ISSUER, FAKE_ISSUER_NAME));

        StationAtlas atlas = StationAtlas.get(server);
        StationAtlas.Station riverbend = station(atlas, player, "Ривербенд", base.offset(40, 0, 20));
        StationAtlas.Station pine = station(atlas, player, "Порт Сосны", base.offset(1_760, 0, 480));
        StationAtlas.Station dunes = station(atlas, player, "Гавань Дюн", base.offset(4_200, 0, 1_000));
        StationAtlas.Station north = station(atlas, player, "Станция Север", base.offset(300, 0, -900));

        ContractManager manager = ContractManager.get(server);
        manager.publishVillage(level, riverbend, pine,
                List.of(new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.BREAD, 16),
                        new ItemStack(Items.OAK_LOG, 24), new ItemStack(Items.TORCH, 48),
                        new ItemStack(Items.WHEAT, 20), new ItemStack(Items.LEATHER, 6)),
                CargoSize.M, 24.0, 512, 600, 15, false);
        manager.publishVillage(level, pine, riverbend,
                List.of(new ItemStack(Items.APPLE, 8), new ItemStack(Items.PAPER, 12)),
                CargoSize.S, 2.0, 64, 0, 20, false);
        manager.publishVillage(level, riverbend, dunes,
                List.of(new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.COPPER_INGOT, 64),
                        new ItemStack(Items.GOLD_INGOT, 16), new ItemStack(Items.BRICKS, 48)),
                CargoSize.L, 96.0, 1_900, 1_200, 40, true);
        manager.publishVillage(level, north, riverbend,
                List.of(new ItemStack(Items.COAL, 40), new ItemStack(Items.STRING, 12)),
                CargoSize.M, 12.0, 256, 300, 10, false);

        Destination home = new Destination(level.dimension(), base.offset(-60, 0, 30),
                StationContractSource.ZONE_RADIUS);
        manager.publishDevPlayerOrder(server, FAKE_ISSUER, home, point(pine),
                List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 9),
                        new ItemStack(Items.GLASS, 32)),
                CargoSize.L, 900, 1_000, 15, 13);
        manager.publishDevPlayerOrder(server, FAKE_ISSUER, home, point(north),
                List.of(new ItemStack(Items.REDSTONE, 24), new ItemStack(Items.ARROW, 32)),
                CargoSize.S, 128, 160, 30, 2);
        manager.publishDevPlayerOrder(server, FAKE_ISSUER, point(riverbend), home,
                List.of(new ItemStack(Items.COOKED_BEEF, 16)),
                CargoSize.S, 96, 120, 25, 40);

        Economy economy = Economies.get(server);
        long current = economy.balance(player.getUUID());
        if (balance > current) {
            economy.deposit(player.getUUID(), balance - current, "dev_seed");
        } else if (balance < current) {
            economy.withdraw(player.getUUID(), current - balance, "dev_seed");
        }
        BoardPackets.open(player);
        source.sendSuccess(() -> Component.literal("[dev] board seeded, balance " + balance), true);
        return 1;
    }

    private static StationAtlas.Station station(StationAtlas atlas, ServerPlayer player, String name, BlockPos pos) {
        UUID id = UUID.nameUUIDFromBytes(("aerocouriers-dev-station:" + name).getBytes(StandardCharsets.UTF_8));
        atlas.register(id, name, player.level().dimension(), pos);
        atlas.discover(id, player.getUUID());
        return atlas.byId(id).orElseThrow();
    }

    private static Destination point(StationAtlas.Station station) {
        return new Destination(station.dimension(), station.pos(), StationContractSource.ZONE_RADIUS);
    }
}
