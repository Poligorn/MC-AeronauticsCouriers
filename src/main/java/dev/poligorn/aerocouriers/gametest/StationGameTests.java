package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.VillageStationDedup;
import dev.poligorn.aerocouriers.village.VillageStructures;
import dev.poligorn.aerocouriers.village.VillageTownCenterStationProcessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/** Post stations (GDD §6.0–6.1): the village structure file, naming, the atlas and discovery. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {
    private StationGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.near", timeoutTicks = 200)
    public static void villageStructurePlacesOneNamedStationThatLicensedPlayersDiscover(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        StructureTemplate template = level.getStructureManager().get(VillageStructures.STRUCTURE).orElse(null);
        helper.assertTrue(template != null, "Structure " + VillageStructures.STRUCTURE + " is missing");
        // Two pieces of one "village": only the first station to load survives (GDD §6.0, one station per village).
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0)).offset(1, 1, 1);
        BlockPos second = helper.absolutePos(new BlockPos(4, 1, 4)).offset(1, 1, 1);
        for (BlockPos stationPos : List.of(first, second)) {
            BlockPos origin = stationPos.offset(-1, -1, -1);
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(stationPos).is(ModBlocks.POST_STATION.get()), "No station in the structure");
            helper.assertTrue(PostStationBlock.isController(level.getBlockState(stationPos)),
                    "structure lower half is the controller");
            helper.assertValueEqual(level.getBlockState(stationPos).getValue(PostStationBlock.FACING), Direction.WEST,
                    "the station faces the street jigsaw");
            helper.assertTrue(PostStationBlock.isUpper(level.getBlockState(stationPos.above())),
                    "structure places the upper half");
            helper.assertTrue(level.getBlockEntity(stationPos.above()) == null, "upper half has no block entity");
            if (level.getBlockEntity(stationPos) instanceof PostStationBlockEntity placed) {
                placed.markVillage();
            }
        }

        StationAtlas atlas = StationAtlas.get(level.getServer());
        AtomicReference<UUID> id = new AtomicReference<>();
        AtomicReference<BlockPos> kept = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    boolean firstKept = level.getBlockState(first).is(ModBlocks.POST_STATION.get());
                    BlockPos stationPos = firstKept ? first : second;
                    BlockPos replaced = firstKept ? second : first;
                    helper.assertTrue(level.getBlockState(replaced).is(Blocks.CARTOGRAPHY_TABLE),
                            "The second station of a village must become a cartography table");
                    PostStationBlockEntity station = level.getBlockEntity(stationPos) instanceof PostStationBlockEntity s
                            ? s : null;
                    helper.assertTrue(station != null && station.stationId() != null, "The station has not loaded yet");
                    helper.assertTrue(!station.name().isEmpty(), "The station got no name");
                    StationAtlas.Station registered = atlas.byId(station.stationId()).orElse(null);
                    helper.assertTrue(registered != null, "The station is not in the atlas");
                    helper.assertValueEqual(registered.name(), station.name(), "name in the atlas");
                    helper.assertValueEqual(registered.pos(), stationPos, "position in the atlas");
                    helper.assertTrue(PostStationBlock.isUpper(level.getBlockState(stationPos.above())),
                            "kept village station is 2-tall");
                    id.set(station.stationId());
                    kept.set(stationPos);
                })
                .thenExecute(() -> {
                    BlockPos stationPos = kept.get();
                    ServerPlayer stranger = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "no_license"));
                    stranger.setPos(Vec3.atCenterOf(stationPos));
                    helper.assertValueEqual(atlas.discoverNearby(stranger), 0, "unlicensed players discover nothing");

                    ServerPlayer courier = licensed(helper);
                    courier.setPos(Vec3.atCenterOf(stationPos).add(200, 0, 0));
                    helper.assertValueEqual(atlas.discoverNearby(courier), 0, "too far away");
                    courier.setPos(Vec3.atCenterOf(stationPos).add(10, 0, 0));
                    helper.assertTrue(atlas.discoverNearby(courier) >= 1, "A licensed courier nearby must discover it");
                    helper.assertTrue(atlas.byId(id.get()).orElseThrow().discoveredBy(courier.getUUID()), "discovered");
                    helper.assertValueEqual(atlas.discoverNearby(courier), 0, "a station is discovered once");

                    level.removeBlock(stationPos, false);
                    helper.assertTrue(atlas.byId(id.get()).isEmpty(), "A removed station must leave the atlas");
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.far", timeoutTicks = 200)
    public static void villageStationsEightyBlocksApartKeepOnlyOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos second = first.offset(80, 0, 0);
        level.getChunk(second);
        level.setBlock(first.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(second.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        PostStationBlockEntity.placingVillage(() -> {
            level.setBlock(first, ModBlocks.POST_STATION.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(first) instanceof PostStationBlockEntity a) {
                a.markVillage();
            }
            level.setBlock(second, ModBlocks.POST_STATION.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(second) instanceof PostStationBlockEntity b) {
                b.markVillage();
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    boolean firstKept = level.getBlockState(first).is(ModBlocks.POST_STATION.get());
                    boolean secondKept = level.getBlockState(second).is(ModBlocks.POST_STATION.get());
                    helper.assertTrue(firstKept ^ secondKept, "Exactly one of two village stations 80 blocks apart remains");
                    BlockPos replaced = firstKept ? second : first;
                    helper.assertTrue(level.getBlockState(replaced).is(Blocks.CARTOGRAPHY_TABLE),
                            "The extra village station becomes a cartography table");
                    helper.assertValueEqual(StationAtlas.get(level.getServer()).all().stream()
                                    .filter(s -> s.pos().equals(first) || s.pos().equals(second))
                                    .count(), 1L, "atlas has one of the pair");
                    BlockPos kept = firstKept ? first : second;
                    helper.assertTrue(PostStationBlock.isUpper(level.getBlockState(kept.above())),
                            "surviving 1-block village station grew an upper half");
                })
                .thenSucceed();
    }

    /**
     * Before the processor: jigsaw can drop 5–7 station houses (tiny 3×3 always fits). After: placing the same
     * pool element seven times keeps one {@code post_station} and turns the rest into cartography tables.
     */
    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.dedup.houses", timeoutTicks = 400)
    public static void sevenStationHousesKeepOnePostStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        StructureTemplate template = level.getStructureManager().get(VillageStructures.STRUCTURE).orElse(null);
        helper.assertTrue(template != null, "Structure " + VillageStructures.STRUCTURE + " is missing");
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        int houses = 7;
        int before = 0;
        BlockPos[] beforePos = new BlockPos[houses];
        for (int i = 0; i < houses; i++) {
            BlockPos origin = first.offset(i * 4, 0, 0);
            level.getChunk(origin);
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
            beforePos[i] = origin.offset(1, 1, 1);
            if (level.getBlockState(beforePos[i]).is(ModBlocks.POST_STATION.get())) {
                before++;
            }
        }
        helper.assertValueEqual(before, houses, "before: every raw house keeps post_station");
        for (BlockPos pos : beforePos) {
            level.removeBlock(pos, false);
        }
        isolateVillageDedup(helper);

        var element = VillageStructures.stationPoolElement(level.registryAccess());
        var templates = level.getServer().getStructureManager();
        var generator = level.getChunkSource().getGenerator();
        BlockPos[] stations = new BlockPos[houses];
        for (int i = 0; i < houses; i++) {
            BlockPos origin = first.offset(i * 4, 0, 16);
            level.getChunk(origin);
            stations[i] = origin.offset(1, 1, 1);
            boolean placed = element.place(
                    templates,
                    level,
                    level.structureManager(),
                    generator,
                    origin,
                    origin,
                    net.minecraft.world.level.block.Rotation.NONE,
                    net.minecraft.world.level.levelgen.structure.BoundingBox.infinite(),
                    level.getRandom(),
                    net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.APPLY_WATERLOGGING,
                    false);
            helper.assertTrue(placed, "station house " + i + " should place");
        }
        int postStations = 0;
        int cartography = 0;
        for (BlockPos stationPos : stations) {
            var state = level.getBlockState(stationPos);
            if (state.is(ModBlocks.POST_STATION.get())) {
                postStations++;
            } else if (state.is(Blocks.CARTOGRAPHY_TABLE)) {
                cartography++;
            }
        }
        AeronauticsCouriers.LOGGER.info("[station] seven houses: before={} post_station, after={} post_station + {} cartography",
                before, postStations, cartography);
        helper.assertValueEqual(postStations, 1, "after: one post_station");
        helper.assertValueEqual(cartography, houses - 1, "after: extras are cartography tables");
        for (BlockPos stationPos : stations) {
            if (level.getBlockState(stationPos).is(ModBlocks.POST_STATION.get())) {
                helper.assertTrue(PostStationBlock.isUpper(level.getBlockState(stationPos.above())),
                        "kept pool-element station is 2-tall");
            } else {
                helper.assertTrue(!level.getBlockState(stationPos.above()).is(ModBlocks.POST_STATION.get()),
                        "extra house upper half must not stay as post_station");
            }
        }
        // Processor claims the keeper before BE finishFirstLoad; that tick must not convert it.
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    int still = 0;
                    for (BlockPos stationPos : stations) {
                        if (level.getBlockState(stationPos).is(ModBlocks.POST_STATION.get())
                                && PostStationBlock.isController(level.getBlockState(stationPos))) {
                            still++;
                            helper.assertTrue(PostStationBlock.isUpper(level.getBlockState(stationPos.above())),
                                    "keeper still 2-tall after first-load tick");
                            helper.assertTrue(
                                    level.getBlockEntity(stationPos) instanceof PostStationBlockEntity keeper
                                            && keeper.stationId() != null,
                                    "keeper registered after first-load tick");
                        }
                    }
                    helper.assertValueEqual(still, 1, "keeper survives finishFirstLoad");
                })
                .thenSucceed();
    }

    /**
     * Leftovers already in a world (village flag, no station_id) still convert on load — old chunks without
     * the processor.
     */
    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.dedup.legacy", timeoutTicks = 200)
    public static void sevenLegacyVillageStationsConvertOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos[] stations = new BlockPos[7];
        PostStationBlockEntity.placingVillage(() -> {
            for (int i = 0; i < stations.length; i++) {
                BlockPos pos = first.offset(i * 4, 0, 0);
                level.getChunk(pos);
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos, ModBlocks.POST_STATION.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof PostStationBlockEntity station) {
                    station.markVillage();
                }
                stations[i] = pos;
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    int postStations = 0;
                    int cartography = 0;
                    for (BlockPos pos : stations) {
                        var state = level.getBlockState(pos);
                        if (state.is(ModBlocks.POST_STATION.get())) {
                            postStations++;
                        } else if (state.is(Blocks.CARTOGRAPHY_TABLE)) {
                            cartography++;
                        }
                    }
                    helper.assertValueEqual(postStations, 1, "legacy village copies keep one post_station");
                    helper.assertValueEqual(cartography, stations.length - 1, "legacy copies become cartography tables");
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.towncenter", timeoutTicks = 200)
    public static void townCenterPlanterPlacesOneStationBesideBell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        BlockPos cobbleRel = new BlockPos(3, 1, 3);
        BlockPos cobble = helper.absolutePos(cobbleRel);
        BlockPos bell = cobble.above();
        helper.setBlock(cobbleRel.below(), Blocks.DIRT);
        helper.setBlock(cobbleRel, Blocks.COBBLESTONE);
        helper.setBlock(cobbleRel.above(), Blocks.BELL);
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            BlockPos groundRel = cobbleRel.relative(dir);
            helper.setBlock(groundRel, Blocks.COBBLESTONE);
            helper.setBlock(groundRel.above(), Blocks.AIR);
            helper.setBlock(groundRel.above(2), Blocks.AIR);
        }
        List<StructureTemplate.StructureBlockInfo> processed = new ArrayList<>();
        processed.add(new StructureTemplate.StructureBlockInfo(cobble, Blocks.COBBLESTONE.defaultBlockState(), null));
        processed.add(new StructureTemplate.StructureBlockInfo(bell, Blocks.BELL.defaultBlockState(), null));
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            processed.add(new StructureTemplate.StructureBlockInfo(
                    cobble.relative(dir), Blocks.COBBLESTONE.defaultBlockState(), null));
        }
        StructurePlaceSettings settings = new StructurePlaceSettings();
        List<StructureTemplate.StructureBlockInfo> first = VillageTownCenterStationProcessor.INSTANCE.finalizeProcessing(
                level, cobble, cobble, processed, processed, settings);
        int controllers = 0;
        int uppers = 0;
        BlockPos planted = null;
        for (StructureTemplate.StructureBlockInfo info : first) {
            if (info.state().is(ModBlocks.POST_STATION.get()) && PostStationBlock.isController(info.state())) {
                controllers++;
                planted = info.pos();
                helper.assertTrue(info.nbt() != null && info.nbt().getBoolean("village"),
                        "planted controller is a village station");
            } else if (info.state().is(ModBlocks.POST_STATION.get()) && PostStationBlock.isUpper(info.state())) {
                uppers++;
            }
        }
        helper.assertValueEqual(controllers, 1, "planter adds one controller");
        helper.assertValueEqual(uppers, 1, "planter adds one upper half");
        helper.assertTrue(planted != null && planted.closerThan(bell, 2.0),
                "stand sits next to the bell, got " + planted);
        List<StructureTemplate.StructureBlockInfo> second = VillageTownCenterStationProcessor.INSTANCE.finalizeProcessing(
                level, cobble, cobble, processed, processed, settings);
        int again = 0;
        for (StructureTemplate.StructureBlockInfo info : second) {
            if (info.state().is(ModBlocks.POST_STATION.get()) && PostStationBlock.isController(info.state())) {
                again++;
            }
        }
        helper.assertValueEqual(again, 0, "second pass does not plant another station");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.worldgen", timeoutTicks = 4000)
    public static void generatedVillagesKeepAtMostOnePostStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        String[] villages = {"village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"};
        BlockPos base = helper.absolutePos(new BlockPos(0, 4, 0)).offset(256, 0, 256);
        StringBuilder report = new StringBuilder();
        int generated = 0;
        int tooMany = 0;
        int missing = 0;
        for (int i = 0; i < villages.length; i++) {
            BlockPos origin = base.offset(i * 512, 0, 0);
            VillageCount count = placeVillageAndCount(helper, level, villages[i], origin);
            report.append(villages[i]).append('=').append(count.postStations)
                    .append(" post_station (pieces=").append(count.pieces)
                    .append(count.generated ? "" : ", generate failed")
                    .append("); ");
            if (count.generated) {
                generated++;
                if (count.postStations > 1) {
                    tooMany++;
                }
                if (count.postStations != 1) {
                    missing++;
                }
            }
        }
        AeronauticsCouriers.LOGGER.info("[station] Village generation counts: {}", report);
        helper.assertTrue(generated >= 1, "at least one village structure should generate: " + report);
        helper.assertTrue(tooMany == 0, "every generated village must keep at most one post_station: " + report);
        helper.assertTrue(missing == 0, "every generated village must have exactly one post_station: " + report);
        helper.succeed();
    }

    private record VillageCount(boolean generated, int pieces, int postStations) {
    }

    private static VillageCount placeVillageAndCount(GameTestHelper helper, ServerLevel level, String structureId,
            BlockPos origin) {
        var structures = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var structure = structures.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(structureId));
        helper.assertTrue(structure != null, "missing structure " + structureId);
        preparePlatform(level, origin, 48);
        var generator = level.getChunkSource().getGenerator();
        var start = structure.generate(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getServer().getStructureManager(),
                level.getSeed() + structureId.hashCode(),
                new net.minecraft.world.level.ChunkPos(origin),
                0,
                level,
                biome -> true);
        if (start == null || !start.isValid()) {
            return new VillageCount(false, 0, 0);
        }
        var box = start.getBoundingBox();
        int minCx = box.minX() >> 4;
        int maxCx = box.maxX() >> 4;
        int minCz = box.minZ() >> 4;
        int maxCz = box.maxZ() >> 4;
        var startChunk = start.getChunkPos();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                var chunk = level.getChunk(cx, cz);
                if (cx == startChunk.x && cz == startChunk.z) {
                    chunk.setStartForStructure(structure, start);
                }
                chunk.addReferenceForStructure(structure, startChunk.toLong());
            }
        }
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                var chunkPos = new net.minecraft.world.level.ChunkPos(cx, cz);
                start.placeInChunk(
                        level,
                        level.structureManager(),
                        generator,
                        level.getRandom(),
                        new net.minecraft.world.level.levelgen.structure.BoundingBox(
                                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                                chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ()),
                        chunkPos);
            }
        }
        Set<BlockPos> stations = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (var piece : start.getPieces()) {
            var pieceBox = piece.getBoundingBox();
            for (int x = pieceBox.minX(); x <= pieceBox.maxX(); x++) {
                for (int y = pieceBox.minY(); y <= pieceBox.maxY(); y++) {
                    for (int z = pieceBox.minZ(); z <= pieceBox.maxZ(); z++) {
                        BlockState state = level.getBlockState(cursor.set(x, y, z));
                        if (state.is(ModBlocks.POST_STATION.get()) && PostStationBlock.isController(state)) {
                            stations.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        for (BlockPos station : stations) {
            helper.assertTrue(PostStationBlock.isUpper(level.getBlockState(station.above())),
                    "generated station at " + station.toShortString() + " must be 2-tall");
        }
        return new VillageCount(true, start.getPieces().size(), stations.size());
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.place", timeoutTicks = 200)
    public static void placingAndBreakingEitherHalfMakesATwoHighStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        BlockPos lowerRel = new BlockPos(2, 1, 2);
        BlockPos lower = helper.absolutePos(lowerRel);
        BlockPos upper = lower.above();
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);

        helper.assertTrue(placeStation(helper, lower, Direction.NORTH), "place facing north");
        assertPair(helper, lower, Direction.NORTH);

        helper.setBlock(lowerRel, Blocks.AIR);
        helper.assertTrue(level.getBlockState(upper).isAir(), "breaking the lower half removes the upper");
        helper.assertTrue(level.getBlockEntity(lower) == null, "controller BE is gone");

        helper.assertTrue(placeStation(helper, lower, Direction.EAST), "place facing east");
        assertPair(helper, lower, Direction.EAST);
        helper.setBlock(lowerRel.above(), Blocks.AIR);
        helper.assertTrue(level.getBlockState(lower).isAir(), "breaking the upper half removes the lower");

        helper.setBlock(lowerRel.above(), Blocks.STONE);
        ServerPlayer blocked = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "no_room"));
        helper.assertTrue(ModBlocks.POST_STATION.get().getStateForPlacement(placeContext(blocked, lower)) == null,
                "cannot place when the cell above is occupied");
        helper.setBlock(lowerRel.above(), Blocks.AIR);

        for (Direction facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            helper.assertTrue(placeStation(helper, lower, facing), "place " + facing);
            assertPair(helper, lower, facing);
            helper.setBlock(lowerRel, Blocks.AIR);
        }
        helper.succeed();
    }

    private static boolean placeStation(GameTestHelper helper, BlockPos absLower, Direction front) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "station_builder"));
        player.setYRot(yRotLookingTowards(front.getOpposite()));
        player.setPos(Vec3.atCenterOf(absLower.relative(front.getOpposite(), 2)));
        ItemStack stack = new ItemStack(ModItems.POST_STATION.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPlaceContext ctx = placeContext(player, absLower);
        BlockState state = ModBlocks.POST_STATION.get().getStateForPlacement(ctx);
        if (state == null) {
            return false;
        }
        if (!level.setBlock(absLower, state, Block.UPDATE_ALL)) {
            return false;
        }
        ModBlocks.POST_STATION.get().setPlacedBy(level, absLower, state, player, stack);
        return true;
    }

    private static BlockPlaceContext placeContext(ServerPlayer player, BlockPos absLower) {
        BlockPos ground = absLower.below();
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(ground).add(0, 0.51, 0), Direction.UP, ground, false);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                player.getMainHandItem().isEmpty()
                        ? new ItemStack(ModItems.POST_STATION.get())
                        : player.getMainHandItem(),
                hit);
    }

    private static float yRotLookingTowards(Direction look) {
        return switch (look) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    private static void assertPair(GameTestHelper helper, BlockPos lower, Direction facing) {
        ServerLevel level = helper.getLevel();
        BlockState low = level.getBlockState(lower);
        BlockState high = level.getBlockState(lower.above());
        helper.assertTrue(low.is(ModBlocks.POST_STATION.get()) && PostStationBlock.isController(low),
                "lower half is the controller");
        helper.assertTrue(high.is(ModBlocks.POST_STATION.get()) && PostStationBlock.isUpper(high),
                "upper half is present");
        helper.assertValueEqual(low.getValue(PostStationBlock.FACING), facing, "lower facing");
        helper.assertValueEqual(high.getValue(PostStationBlock.FACING), facing, "upper facing");
        helper.assertValueEqual(low.getValue(PostStationBlock.HALF), DoubleBlockHalf.LOWER, "half=lower");
        helper.assertTrue(level.getBlockEntity(lower) instanceof PostStationBlockEntity, "BE on lower");
        helper.assertTrue(level.getBlockEntity(lower.above()) == null, "no BE on upper");
        helper.assertTrue(PostStationBlock.stationEntity(level, lower)
                        == PostStationBlock.stationEntity(level, lower.above()),
                "both halves resolve to the same station");
        helper.assertValueEqual(low.getLightEmission(level, lower), PostStationBlock.LIGHT_LEVEL, "lower light");
        helper.assertValueEqual(high.getLightEmission(level, lower.above()), PostStationBlock.LIGHT_LEVEL, "upper light");
    }

    private static void isolateVillageDedup(GameTestHelper helper) {
        VillageStationDedup.resetClaims();
        ServerLevel level = helper.getLevel();
        StationAtlas.get(level.getServer()).removeNear(level.dimension(), helper.absolutePos(BlockPos.ZERO), 384);
    }

    private static void preparePlatform(ServerLevel level, BlockPos origin, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                cursor.set(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
                level.getChunk(cursor);
                level.setBlock(cursor, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static ServerPlayer licensed(GameTestHelper helper) {
        ServerPlayer courier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "explorer"));
        var curios = CuriosApi.getCuriosInventory(courier).orElseThrow();
        curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(courier, Licenses.findLicense(courier).orElseThrow());
        return courier;
    }
}
