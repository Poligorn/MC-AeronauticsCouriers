package dev.poligorn.aerocouriers.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.access.OrderTablets;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.DecorCargoBlock;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.admin.AdminActions;
import dev.poligorn.aerocouriers.contract.Contract;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.economy.Economies;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardPackets;
import dev.poligorn.aerocouriers.net.StationPackets;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.tutorial.TutorialData;
import dev.poligorn.aerocouriers.tutorial.Tutorials;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
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
    private static BlockPos corkStationPos;
    private static UUID corkInProgressId;
    private static boolean tabletAutoSeeded;

    /** QA hook: {@code -Daerocouriers.jadeqa=true} / {@code -Daerocouriers.corkqa=true}. */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (Boolean.getBoolean("aerocouriers.jadeqa")) {
            server.tell(new TickTask(server.getTickCount() + 20, () ->
                    jadeQa(server.createCommandSourceStack().withPermission(4))));
        }
        if (Boolean.getBoolean("aerocouriers.corkqa")) {
            server.tell(new TickTask(server.getTickCount() + 20, () ->
                    corkQa(server.createCommandSourceStack().withPermission(4))));
        }
        if (Boolean.getBoolean("aerocouriers.tabletsqa")) {
            server.tell(new TickTask(server.getTickCount() + 40, () -> seedWhenPlayerReady(server, 0)));
        }
    }

    private static void seedWhenPlayerReady(MinecraftServer server, int attempt) {
        if (tabletAutoSeeded) {
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            if (attempt < 200) {
                server.tell(new TickTask(server.getTickCount() + 20, () -> seedWhenPlayerReady(server, attempt + 1)));
            }
            return;
        }
        tabletAutoSeeded = true;
        try {
            seed(server.getPlayerList().getPlayers().getFirst().createCommandSourceStack().withPermission(4), 989);
        } catch (CommandSyntaxException e) {
            tabletAutoSeeded = false;
            AeronauticsCouriers.LOGGER.warn("[tabletsqa] seed: {}", e.toString());
        }
    }

    /** Drop {@code jadeqa-scene.txt} / {@code corkqa-cmd.txt} in the game dir. */
    public static void pollLookFile(MinecraftServer server) {
        if (Boolean.getBoolean("aerocouriers.jadeqa")) {
            pollFile(server, Path.of("jadeqa-scene.txt"), (player, scene) -> {
                try {
                    look(player.createCommandSourceStack().withPermission(4), scene);
                } catch (CommandSyntaxException e) {
                    AeronauticsCouriers.LOGGER.warn("[jadeqa] look: {}", e.toString());
                }
            });
        }
        pollFile(server, Path.of("corkqa-cmd.txt"), DevSeedCommands::runCorkCommand);
        pollFile(server, Path.of("tabletsqa-cmd.txt"), DevSeedCommands::runTabletCommand);
        pollFile(server, Path.of("tabqa-world.txt"), DevSeedCommands::runTabQaWorld);
    }

    private static void pollFile(MinecraftServer server, Path path,
                                 java.util.function.BiConsumer<ServerPlayer, String> action) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String value = Files.readString(path).strip();
            Files.deleteIfExists(path);
            if (value.isEmpty()) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                action.accept(player, value);
            }
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[qa] {}: {}", path, e.toString());
        }
    }

    /** QA: {@code tabqa-world.txt} — place a row of decorative crates and look at them. */
    private static void runTabQaWorld(ServerPlayer player, String cmd) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = new BlockPos(player.blockPosition().getX() + 4, player.blockPosition().getY(),
                player.blockPosition().getZ() + 4);
        if (cmd.startsWith("look")) {
            lookDecor(player, origin);
            return;
        }
        flattenDecor(level, origin);
        placeDecor(level, origin, CargoSize.S);
        placeDecor(level, origin.offset(3, 0, 0), CargoSize.M);
        placeDecor(level, origin.offset(6, 0, 0), CargoSize.L);
        placeDecor(level, origin.offset(10, 0, 0), CargoSize.XL);
        lookDecor(player, origin);
        AeronauticsCouriers.LOGGER.info("[tabqa] placed decor S/M/L/XL at {}", origin);
    }

    private static void flattenDecor(ServerLevel level, BlockPos origin) {
        for (int x = -1; x <= 14; x++) {
            for (int z = -1; z <= 4; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y <= 4; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void placeDecor(ServerLevel level, BlockPos pos, CargoSize size) {
        var state = DecorCargoBlock.stateForPart(size, Direction.NORTH, BlockPos.ZERO);
        level.setBlock(pos, state, 3);
        DecorCargoBlock.blockFor(size).setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
    }

    private static void lookDecor(ServerPlayer player, BlockPos origin) {
        player.serverLevel().setDayTime(6000);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.connection.teleport(origin.getX() + 6.5, origin.getY() + 1.0, origin.getZ() + 8.5, 180.0f, 12.0f);
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
                .then(Commands.literal("corkqa").executes(ctx -> corkQa(ctx.getSource())))
                .then(Commands.literal("tabletsqa").executes(ctx -> seed(ctx.getSource(), 989)))
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

        CouriersServerConfig.TUTORIAL_ENABLED.set(false);
        if (!OrderTablets.hasTablet(player)) {
            if (!player.getInventory().add(new ItemStack(ModItems.ORDER_TABLET.get()))) {
                player.drop(new ItemStack(ModItems.ORDER_TABLET.get()), false);
            }
        }
        AdminActions.grantLicense(player, LicenseRank.RANK3);

        ContractManager manager = ContractManager.get(server);
        ItemStack rareBook = rareBook(level);
        Contract rareOnly = manager.publishVillage(level, riverbend, pine,
                List.of(new ItemStack(Items.WHEAT, 64), new ItemStack(Items.CARROT, 32),
                        new ItemStack(Items.POTATO, 48), new ItemStack(Items.BREAD, 16), rareBook.copy()),
                CargoSize.S, 3.0, 420, 80, 90, true).orElse(null);
        Contract expensiveOnly = manager.publishVillage(level, pine, riverbend,
                List.of(new ItemStack(Items.GOLD_INGOT, 12)),
                CargoSize.S, 6.0, 1_200, 150, 70, false).orElse(null);
        Contract both = manager.publishVillage(level, riverbend, dunes,
                List.of(new ItemStack(Items.WHEAT, 64), new ItemStack(Items.CARROT, 32),
                        new ItemStack(Items.POTATO, 48), new ItemStack(Items.BREAD, 16), rareBook.copy()),
                CargoSize.M, 18.0, 1_450, 200, 85, true).orElse(null);
        Contract plain = manager.publishVillage(level, north, riverbend,
                List.of(new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.IRON_INGOT, 32)),
                CargoSize.L, 90.0, 800, 400, 120, false).orElse(null);
        writeTabletIds(rareOnly, expensiveOnly, both, plain);

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

    /** QA: enable the tutorial, issue the training order, reopen the board. */
    private static void seedTutorial(ServerPlayer player) {
        MinecraftServer server = player.server;
        CouriersServerConfig.TUTORIAL_ENABLED.set(true);
        TutorialData.get(server).put(player.getUUID(), TutorialData.Entry.EMPTY);
        Component message = Tutorials.start(player);
        BoardPackets.open(player);
        AeronauticsCouriers.LOGGER.info("[tabletsqa] tutorial {}", message.getString());
    }

    private static void runTabletCommand(ServerPlayer player, String raw) {
        String cmd = raw.strip().toLowerCase(java.util.Locale.ROOT);
        try {
            if ("seed".equals(cmd) || "tabletsqa".equals(cmd) || "board".equals(cmd)) {
                seed(player.createCommandSourceStack().withPermission(4), 989);
            } else if ("tutorial".equals(cmd) || "tutorial-seed".equals(cmd)) {
                seedTutorial(player);
            } else {
                AeronauticsCouriers.LOGGER.warn("[tabletsqa] unknown cmd {}", cmd);
            }
        } catch (CommandSyntaxException e) {
            AeronauticsCouriers.LOGGER.warn("[tabletsqa] cmd {}: {}", cmd, e.toString());
        }
    }

    private static void writeTabletIds(Contract rare, Contract expensive, Contract both, Contract plain) {
        try {
            String line = "rare=" + idOrEmpty(rare)
                    + "\nexpensive=" + idOrEmpty(expensive)
                    + "\nboth=" + idOrEmpty(both)
                    + "\nplain=" + idOrEmpty(plain)
                    + "\n";
            Files.writeString(Path.of("tabletsqa-ids.txt"), line);
            AeronauticsCouriers.LOGGER.info("[tabletsqa] ids {}", line.replace('\n', ' '));
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[tabletsqa] ids: {}", e.toString());
        }
    }

    private static String idOrEmpty(Contract contract) {
        return contract == null ? "" : contract.id().toString();
    }

    private static StationAtlas.Station station(StationAtlas atlas, ServerPlayer player, String name, BlockPos pos) {
        UUID id = UUID.nameUUIDFromBytes(("aerocouriers-dev-station:" + name).getBytes(StandardCharsets.UTF_8));
        atlas.register(id, name, player.level().dimension(), pos);
        atlas.discover(id, player.getUUID());
        return atlas.byId(id).orElseThrow();
    }

    private static ItemStack rareBook(ServerLevel level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        level.registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT,
                        ResourceLocation.withDefaultNamespace("unbreaking"))))
                .ifPresent(holder -> {
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mutable.set(holder, 2);
                    book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
                });
        return book;
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

    private static int corkQa(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos origin = new BlockPos(spawn.getX() + 8, spawn.getY() + 4, spawn.getZ());
        CouriersServerConfig.TUTORIAL_ENABLED.set(false);
        CouriersServerConfig.REQUIRE_TABLET.set(true);
        CouriersServerConfig.VILLAGE_BOARD_MAX.set(16);
        level.setDayTime(6000);
        flattenCork(level, origin);

        BlockPos stationPos = origin.offset(4, 0, 4);
        BlockPos far = origin.offset(80, 0, 240);
        corkStationPos = stationPos;
        UUID fromId = UUID.nameUUIDFromBytes("aerocouriers-corkqa:from".getBytes(StandardCharsets.UTF_8));
        UUID toNear = UUID.nameUUIDFromBytes("aerocouriers-corkqa:near".getBytes(StandardCharsets.UTF_8));
        UUID toFar = UUID.nameUUIDFromBytes("aerocouriers-corkqa:far".getBytes(StandardCharsets.UTF_8));
        UUID toRare = UUID.nameUUIDFromBytes("aerocouriers-corkqa:rare".getBytes(StandardCharsets.UTF_8));
        clearVillageOrders(server, fromId);
        StationAtlas atlas = StationAtlas.get(server);
        atlas.register(fromId, "Ясная Поляна", level.dimension(), stationPos);
        atlas.register(toNear, "Речной Берег", level.dimension(), origin.offset(48, 0, 12));
        atlas.register(toFar, "Гавань Дюн", level.dimension(), far);
        atlas.register(toRare, "Порт Сосны", level.dimension(), origin.offset(2_000, 0, 800));
        atlas.setReputation(fromId, 27);
        atlas.discover(fromId, FAKE_ISSUER);
        placeStation(level, stationPos, fromId, "Ясная Поляна");
        PostStationBlock.tryPlaceUpperHalf(level, stationPos);

        StationAtlas.Station from = atlas.byId(fromId).orElseThrow();
        StationAtlas.Station near = atlas.byId(toNear).orElseThrow();
        StationAtlas.Station dunes = atlas.byId(toFar).orElseThrow();
        StationAtlas.Station pine = atlas.byId(toRare).orElseThrow();
        ContractManager manager = ContractManager.get(server);

        publish(level, from, near, CargoSize.S, 4.0, 180, 90, 80, false,
                List.of(new ItemStack(Items.BREAD, 16), new ItemStack(Items.APPLE, 8)));
        publish(level, from, dunes, CargoSize.S, 6.0, 1_200, 150, 70, false,
                List.of(new ItemStack(Items.GOLD_INGOT, 12)));
        ItemStack rareBook = rareBook(level);
        publish(level, from, pine, CargoSize.S, 3.0, 420, 80, 90, true,
                List.of(new ItemStack(Items.WHEAT, 64), new ItemStack(Items.CARROT, 32),
                        new ItemStack(Items.POTATO, 48), new ItemStack(Items.BREAD, 16), rareBook.copy()));
        publish(level, from, pine, CargoSize.M, 18.0, 1_450, 200, 85, true,
                List.of(new ItemStack(Items.WHEAT, 64), new ItemStack(Items.CARROT, 32),
                        new ItemStack(Items.POTATO, 48), new ItemStack(Items.BREAD, 16), rareBook.copy()));
        publish(level, from, dunes, CargoSize.L, 90.0, 800, 400, 120, false,
                List.of(new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.IRON_INGOT, 32)));
        publish(level, from, dunes, CargoSize.XL, 180.0, 2_400, 900, 150, false,
                List.of(new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.GOLD_INGOT, 16)));
        Contract expiring = publish(level, from, near, CargoSize.S, 2.5, 220, 70, 100, false,
                List.of(new ItemStack(Items.PAPER, 12)));
        if (expiring != null) {
            manager.qaSetRemaining(server, expiring.id(), 8, 80);
        }
        Contract progress = publish(level, from, near, CargoSize.M, 12.0, 360, 120, 90, false,
                List.of(new ItemStack(Items.LEATHER, 8), new ItemStack(Items.STRING, 16)));
        corkInProgressId = progress == null ? null : progress.id();
        if (progress != null) {
            UUID miraId = UUID.nameUUIDFromBytes("aerocouriers-corkqa:mira".getBytes(StandardCharsets.UTF_8));
            GameProfile mira = new GameProfile(miraId, "Mira");
            if (server.getProfileCache() != null) {
                server.getProfileCache().add(mira);
            }
            manager.qaAccept(server, progress.id(), miraId);
        }
        for (int i = 0; i < 4; i++) {
            publish(level, from, i % 2 == 0 ? near : dunes, CargoSize.S, 3.0 + i, 90 + i * 20L, 40, 60 + i * 5,
                    false, List.of(new ItemStack(Items.WHEAT, 8 + i)));
        }

        source.sendSuccess(() -> Component.literal("[corkqa] station " + stationPos.toShortString()), true);
        AeronauticsCouriers.LOGGER.info("[corkqa] station {} in-progress {}", stationPos, corkInProgressId);
        return 1;
    }

    private static void runCorkCommand(ServerPlayer player, String raw) {
        String cmd = raw.strip().toLowerCase(java.util.Locale.ROOT);
        MinecraftServer server = player.server;
        if ("setup".equals(cmd) || "corkqa".equals(cmd)) {
            corkQa(player.createCommandSourceStack().withPermission(4));
            preparePlayer(player, true, LicenseRank.RANK1);
            return;
        }
        if (corkStationPos == null) {
            corkQa(player.createCommandSourceStack().withPermission(4));
        }
        switch (cmd) {
            case "board" -> {
                preparePlayer(player, true, LicenseRank.RANK1);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            case "board-op" -> {
                server.getPlayerList().op(player.getGameProfile());
                preparePlayer(player, true, LicenseRank.RANK3);
                player.setGameMode(GameType.CREATIVE);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            case "admin" -> {
                preparePlayer(player, true, LicenseRank.RANK3);
                player.setGameMode(GameType.CREATIVE);
                StationPackets.openAdmin(player, player.serverLevel(), corkStationPos);
            }
            case "survival" -> {
                player.setGameMode(GameType.SURVIVAL);
                server.getPlayerList().deop(player.getGameProfile());
                preparePlayer(player, true, LicenseRank.RANK1);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            case "notablet" -> {
                preparePlayer(player, false, LicenseRank.RANK1);
                CouriersServerConfig.REQUIRE_TABLET.set(true);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            case "tablet" -> {
                preparePlayer(player, true, LicenseRank.RANK1);
                CouriersServerConfig.REQUIRE_TABLET.set(true);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            case "empty" -> {
                UUID fromId = UUID.nameUUIDFromBytes("aerocouriers-corkqa:from".getBytes(StandardCharsets.UTF_8));
                clearVillageOrders(server, fromId);
                corkInProgressId = null;
                preparePlayer(player, true, LicenseRank.RANK3);
                player.setGameMode(GameType.CREATIVE);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            case "progress" -> {
                preparePlayer(player, true, LicenseRank.RANK3);
                player.setGameMode(GameType.CREATIVE);
                StationPackets.open(player, player.serverLevel(), corkStationPos);
            }
            default -> AeronauticsCouriers.LOGGER.warn("[corkqa] unknown cmd {}", cmd);
        }
    }

    private static void preparePlayer(ServerPlayer player, boolean tablet, LicenseRank rank) {
        if (corkStationPos != null) {
            player.connection.teleport(corkStationPos.getX() + 0.5, corkStationPos.getY(),
                    corkStationPos.getZ() + 3.5, 180.0f, 10.0f);
        }
        player.serverLevel().setDayTime(6000);
        player.getInventory().clearContent();
        if (tablet) {
            if (!player.getInventory().add(new ItemStack(ModItems.ORDER_TABLET.get()))) {
                player.drop(new ItemStack(ModItems.ORDER_TABLET.get()), false);
            }
        }
        AdminActions.grantLicense(player, rank);
        Economy economy = Economies.get(player.server);
        if (economy.balance(player.getUUID()) < 5_000) {
            economy.deposit(player.getUUID(), 5_000, "corkqa");
        }
        if (corkInProgressId != null) {
            ContractManager manager = ContractManager.get(player.server);
            manager.byId(corkInProgressId).ifPresent(c -> {
                if (c.state() == ContractState.OPEN) {
                    manager.qaAccept(player.server, c.id(), player.getUUID());
                }
            });
        }
    }

    private static void clearVillageOrders(MinecraftServer server, UUID stationId) {
        ContractManager manager = ContractManager.get(server);
        for (Contract contract : List.copyOf(manager.all().toList())) {
            if (contract.type() == ContractType.VILLAGE
                    && contract.issuer().equals(stationId)
                    && !contract.state().isFinal()) {
                manager.adminCancelWithRefund(server, contract.id());
            }
        }
    }

    private static Contract publish(ServerLevel level, StationAtlas.Station from, StationAtlas.Station to,
                                    CargoSize size, double weight, long reward, long collateral,
                                    int deadlineMinutes, boolean rare, List<ItemStack> manifest) {
        return ContractManager.get(level.getServer())
                .publishVillage(level, from, to, manifest, size, weight, reward, collateral, deadlineMinutes, rare)
                .orElse(null);
    }

    private static void placeStation(ServerLevel level, BlockPos pos, UUID id, String name) {
        level.setBlock(pos, ModBlocks.POST_STATION.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof PostStationBlockEntity be) {
            CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
            tag.putUUID("station_id", id);
            tag.putString("name", name);
            tag.putBoolean("village", true);
            be.loadWithComponents(tag, level.registryAccess());
            be.setChanged();
        }
    }

    private static void flattenCork(ServerLevel level, BlockPos origin) {
        int x0 = origin.getX() - 2;
        int x1 = origin.getX() + 16;
        int z0 = origin.getZ() - 2;
        int z1 = origin.getZ() + 16;
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
}
