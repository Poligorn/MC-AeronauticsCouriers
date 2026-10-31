package dev.poligorn.aerocouriers.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import top.theillusivec4.curios.api.CuriosApi;

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

    /** Look-at cells for {@code /couriers dev look}. Rebuilt by {@link #jadeQa}. */
    private static final Map<String, BlockPos> LOOK = new LinkedHashMap<>();
    private static UUID sableContractId;

    /** QA hook: {@code -Daerocouriers.jadeqa=true} places the pad after the server starts. */
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("aerocouriers.jadeqa")) {
            return;
        }
        MinecraftServer server = event.getServer();
        server.tell(new TickTask(server.getTickCount() + 20, () ->
                jadeQa(server.createCommandSourceStack().withPermission(4))));
    }

    /** Drop {@code jadeqa-scene.txt} in the game dir (one scene id) to aim every player at that prop. */
    public static void pollLookFile(MinecraftServer server) {
        if (!Boolean.getBoolean("aerocouriers.jadeqa")) {
            return;
        }
        Path path = Path.of("jadeqa-scene.txt");
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String scene = Files.readString(path).strip();
            Files.deleteIfExists(path);
            if (scene.isEmpty()) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                look(player.createCommandSourceStack().withPermission(4), scene);
            }
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[jadeqa] look file: {}", e.toString());
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> dev() {
        return Commands.literal("dev")
                .requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("seed")
                        .executes(ctx -> seed(ctx.getSource(), 989))
                        .then(Commands.argument("balance", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                                .executes(ctx -> seed(ctx.getSource(),
                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "balance")))))
                .then(Commands.literal("jadeqa").executes(ctx -> jadeQa(ctx.getSource())))
                .then(Commands.literal("look")
                        .then(Commands.argument("scene", StringArgumentType.word())
                                .executes(ctx -> look(ctx.getSource(), StringArgumentType.getString(ctx, "scene")))));
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
        manager.publishDevPlayerOrder(server, player.getUUID(), point(north), point(pine),
                List.of(new ItemStack(Items.BOOK, 10), new ItemStack(Items.MAP, 2)),
                CargoSize.S, 300, 200, 45, 5);

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

    /**
     * Places locked Jade QA props on a pad east of spawn. Console-safe (no online player required).
     * Scenes: empty, published, transit, large, xl, village, station, sable.
     */
    private static int jadeQa(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        // Fixed offset: heightmap would climb each restart (stone pad + crates).
        BlockPos origin = new BlockPos(spawn.getX() + 16, spawn.getY() + 4, spawn.getZ());
        CouriersServerConfig.TUTORIAL_ENABLED.set(false);
        level.setDayTime(6000);
        flatten(level, origin);
        LOOK.clear();
        sableContractId = null;

        server.getProfileCache().add(new GameProfile(FAKE_ISSUER, FAKE_ISSUER_NAME));
        ContractManager manager = ContractManager.get(server);
        Destination dest = new Destination(level.dimension(), origin.offset(0, 0, 24), 4);

        BlockPos empty = origin;
        placeLocked(level, empty, CargoSize.S, 0, null);
        LOOK.put("empty", empty);

        BlockPos published = origin.offset(8, 0, 0);
        Contract open = manager.publishDevPlayerOrder(server, FAKE_ISSUER,
                new Destination(level.dimension(), published, 4), dest,
                List.of(new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.IRON_INGOT, 5)),
                CargoSize.S, 20, 24, 60, 0);
        placeLocked(level, published, CargoSize.S, 4, open.id());
        LOOK.put("published", published);

        BlockPos transit = origin.offset(16, 0, 0);
        Contract taken = manager.publishDevPlayerOrder(server, FAKE_ISSUER,
                new Destination(level.dimension(), transit, 4), dest,
                List.of(new ItemStack(Items.BREAD, 16)),
                CargoSize.S, 20, 0, 60, 0);
        ServerPlayer courier = fakeCourier(level, "jade_courier");
        ContractManager.Outcome accepted = manager.accept(courier, taken.id());
        if (!accepted.success()) {
            source.sendFailure(Component.literal("[jadeqa] accept failed: " + accepted.message().getString()));
        }
        placeLocked(level, transit, CargoSize.S, 4, taken.id());
        LOOK.put("transit", transit);

        BlockPos large = origin.offset(24, 0, 0);
        Contract largeOrder = manager.publishDevPlayerOrder(server, FAKE_ISSUER,
                new Destination(level.dimension(), large, 4), dest,
                List.of(new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64),
                        new ItemStack(Items.IRON_INGOT, 32)),
                CargoSize.L, 80, 100, 60, 0);
        placeLocked(level, large, CargoSize.L, 4, largeOrder.id());
        LOOK.put("large", large.offset(1, 0, 0));

        BlockPos xl = origin.offset(34, 0, 0);
        Contract xlOrder = manager.publishDevPlayerOrder(server, FAKE_ISSUER,
                new Destination(level.dimension(), xl, 4), dest,
                List.of(new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.COPPER_INGOT, 64),
                        new ItemStack(Items.GOLD_INGOT, 16)),
                CargoSize.XL, 200, 240, 60, 0);
        placeLocked(level, xl, CargoSize.XL, 6, xlOrder.id());
        LOOK.put("xl", xl.offset(1, 1, 0));

        BlockPos villageCrate = origin.offset(44, 0, 0);
        BlockPos stationPos = origin.offset(54, 0, 0);
        LOOK.put("village", villageCrate);
        LOOK.put("station", stationPos);

        BlockPos sable = origin.offset(64, 0, 0);
        Contract ship = manager.publishDevPlayerOrder(server, FAKE_ISSUER,
                new Destination(level.dimension(), sable, 4), dest,
                List.of(new ItemStack(Items.APPLE, 8)),
                CargoSize.S, 20, 24, 60, 0);
        sableContractId = ship.id();
        CargoSpawner.Result assembled = CargoSpawner.spawn(level, sable, CargoSize.S, 3, ship.id(), true,
                Direction.NORTH);
        LOOK.put("sable", sable);
        if (!(assembled instanceof CargoSpawner.Result.Spawned)) {
            source.sendFailure(Component.literal("[jadeqa] sable assemble: " + assembled.getClass().getSimpleName()));
        }

        UUID fromId = UUID.nameUUIDFromBytes("aerocouriers-jadeqa:from".getBytes(StandardCharsets.UTF_8));
        UUID toId = UUID.nameUUIDFromBytes("aerocouriers-jadeqa:to".getBytes(StandardCharsets.UTF_8));
        StationAtlas atlas = StationAtlas.get(server);
        atlas.register(fromId, "Ясная Поляна", level.dimension(), stationPos);
        atlas.register(toId, "Порт Сосны", level.dimension(), dest.pos());
        atlas.setReputation(fromId, 27);
        atlas.discover(fromId, FAKE_ISSUER);
        level.setBlock(stationPos, ModBlocks.POST_STATION.get().defaultBlockState(), 3);
        if (level.getBlockEntity(stationPos) instanceof PostStationBlockEntity be) {
            CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
            tag.putUUID("station_id", fromId);
            tag.putString("name", "Ясная Поляна");
            tag.putBoolean("village", true);
            be.loadWithComponents(tag, level.registryAccess());
            be.setChanged();
        }
        StationAtlas.Station from = atlas.byId(fromId).orElseThrow();
        StationAtlas.Station to = atlas.byId(toId).orElseThrow();
        Contract village = StationContractSource.publish(level, from, to, level.getRandom()).orElse(null);
        if (village == null) {
            source.sendFailure(Component.literal("[jadeqa] village publish failed"));
        } else {
            placeLocked(level, villageCrate, CargoSize.S, 2, village.id());
            AeronauticsCouriers.LOGGER.info("[jadeqa] village {} station {}", village.shortId(), from.name());
        }

        source.sendSuccess(() -> Component.literal(
                "[jadeqa] pad at " + origin.toShortString() + " scenes " + LOOK.keySet()), true);
        AeronauticsCouriers.LOGGER.info("[jadeqa] pad {} scenes {}", origin, LOOK.keySet());
        return 1;
    }

    private static int look(CommandSourceStack source, String scene) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos target = LOOK.get(scene);
        if ("sable".equals(scene) && sableContractId != null) {
            target = CargoTracker.get(sableContractId)
                    .map(s -> BlockPos.containing(s.position().x(), s.position().y(), s.position().z()))
                    .orElse(target);
        }
        if (target == null) {
            source.sendFailure(Component.literal("[jadeqa] unknown scene '" + scene + "'; have " + LOOK.keySet()));
            return 0;
        }
        BlockPos lookAt = target;
        // Creative + downward pitch: spectator hid Jade; horizontal eye sits above a 1-block crate.
        player.serverLevel().setDayTime(6000);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        double x = lookAt.getX() + 0.5;
        double y = lookAt.getY();
        // L/XL footprint is 2 deep: +2.4 stands inside the south face.
        double z = lookAt.getZ() + 5.2;
        player.connection.teleport(x, y, z, 180.0f, 12.0f);
        source.sendSuccess(() -> Component.literal("[jadeqa] look " + scene + " → " + lookAt.toShortString()), false);
        return 1;
    }

    private static void flatten(ServerLevel level, BlockPos origin) {
        int x0 = origin.getX() - 4;
        int x1 = origin.getX() + 72;
        int z0 = origin.getZ() - 6;
        int z1 = origin.getZ() + 8;
        int y0 = origin.getY() - 1;
        int y1 = origin.getY() + 6;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setBlock(new BlockPos(x, y0, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = y0 + 1; y <= y1; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void placeLocked(ServerLevel level, BlockPos pos, CargoSize size, int fill, UUID contractId) {
        CargoSpawner.Result result = CargoSpawner.spawn(level, pos, size, fill, contractId, true, Direction.NORTH, true);
        if (!(result instanceof CargoSpawner.Result.Placed)) {
            AeronauticsCouriers.LOGGER.warn("[jadeqa] place {} at {} → {}", size, pos, result.getClass().getSimpleName());
        }
    }

    private static ServerPlayer fakeCourier(ServerLevel level, String name) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        // Do not registerTestPlayer: the tick loop would spawn/unlock the crate next to this FakePlayer.
        CuriosApi.getCuriosInventory(player).ifPresent(inv ->
                inv.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get())));
        Licenses.findLicense(player).ifPresent(stack -> Licenses.tickWorn(player, stack));
        return player;
    }
}
