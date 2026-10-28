package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.VillageStructures;
import java.util.List;
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

    private static ServerPlayer licensed(GameTestHelper helper) {
        ServerPlayer courier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "explorer"));
        var curios = CuriosApi.getCuriosInventory(courier).orElseThrow();
        curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(courier, Licenses.findLicense(courier).orElseThrow());
        return courier;
    }
}
