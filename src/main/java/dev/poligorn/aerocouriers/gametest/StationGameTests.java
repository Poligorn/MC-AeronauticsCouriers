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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
            helper.assertValueEqual(level.getBlockState(stationPos).getValue(PostStationBlock.FACING), Direction.WEST,
                    "the station faces the street jigsaw");
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
        helper.succeed();
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

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.stations.worldgen", timeoutTicks = 4000)
    public static void generatedVillagesKeepAtMostOnePostStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        isolateVillageDedup(helper);
        String[] villages = {"village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"};
        BlockPos base = helper.absolutePos(new BlockPos(0, 4, 0)).offset(256, 0, 256);
        StringBuilder report = new StringBuilder();
        int generated = 0;
        int tooMany = 0;
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
            }
        }
        AeronauticsCouriers.LOGGER.info("[station] Village generation counts: {}", report);
        helper.assertTrue(generated >= 1, "at least one village structure should generate: " + report);
        helper.assertTrue(tooMany == 0, "every generated village must keep at most one post_station: " + report);
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
                        if (level.getBlockState(cursor.set(x, y, z)).is(ModBlocks.POST_STATION.get())) {
                            stations.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        return new VillageCount(true, start.getPieces().size(), stations.size());
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
