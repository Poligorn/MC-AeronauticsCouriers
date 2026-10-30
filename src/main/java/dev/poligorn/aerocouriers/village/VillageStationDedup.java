package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

/**
 * One {@code aerocouriers:post_station} per village. Jigsaw can pick the 3×3 station house many times
 * (it always fits when larger vanilla houses fail), so uniqueness is enforced when the piece is placed
 * and again when a leftover village BE first loads.
 *
 * <p>Placement must not depend on the atlas or a wide BE scan: during jigsaw the structure start may
 * not be queryable yet, and GameTest slots sit closer than a village diameter. The first kept station
 * records its block position; later {@code post_station} blocks of that village become cartography.
 */
public final class VillageStationDedup {
    static final String[] VILLAGE_STRUCTURES = {
            "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"
    };

    private static final Set<BlockPos> CLAIMED_STATIONS = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> CLAIMED_VILLAGE_STARTS = ConcurrentHashMap.newKeySet();

    private VillageStationDedup() {
    }

    public static BlockState cartographyTable() {
        return Blocks.CARTOGRAPHY_TABLE.defaultBlockState();
    }

    public static void resetClaims() {
        CLAIMED_STATIONS.clear();
        CLAIMED_VILLAGE_STARTS.clear();
    }

    public static int villageDedupRadius() {
        return CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.STATION_VILLAGE_DEDUP_RADIUS.get()
                : 96;
    }

    /** Vanilla village jigsaw is capped at 80 from the start; two houses can sit ~160 apart. */
    static int placementFallbackRadius() {
        return Math.max(villageDedupRadius(), 176);
    }

    public static void claim(LevelReader level, BlockPos pos) {
        CLAIMED_STATIONS.add(pos.immutable());
        BlockPos start = villageStart(level, pos);
        if (start != null) {
            CLAIMED_VILLAGE_STARTS.add(start);
        }
    }

    /**
     * Worldgen: the original is the first station claimed for this village start, or the first claimed
     * block within a village-sized fallback when the start is not in the structure manager yet.
     */
    public static boolean isDuplicateAtPlacement(LevelReader level, BlockPos pos) {
        BlockPos start = villageStart(level, pos);
        if (start != null && CLAIMED_VILLAGE_STARTS.contains(start)) {
            return true;
        }
        int radius = start == null ? placementFallbackRadius() : villageDedupRadius();
        for (BlockPos claimed : CLAIMED_STATIONS) {
            if (claimed.equals(pos)) {
                continue;
            }
            if (start != null) {
                BlockPos claimedStart = villageStart(level, claimed);
                if (start.equals(claimedStart)) {
                    return true;
                }
                continue;
            }
            if (claimed.closerThan(pos, radius)) {
                return true;
            }
        }
        BoundingBox villageBox = villageBox(level, pos);
        if (villageBox != null) {
            return hasLoadedStationBlock(level, pos, start, villageBox, radius, true);
        }
        return false;
    }

    /**
     * Block-entity first load: only a registered atlas/BE neighbour counts, so several unregistered copies
     * loading on the same tick do not all turn into cartography tables.
     */
    public static boolean isDuplicateAtLoad(LevelReader level, BlockPos pos, @Nullable StationAtlas atlas) {
        BlockPos start = villageStart(level, pos);
        if (start != null && CLAIMED_VILLAGE_STARTS.contains(start)) {
            return true;
        }
        int radius = villageDedupRadius();
        for (BlockPos claimed : CLAIMED_STATIONS) {
            if (claimed.equals(pos)) {
                continue;
            }
            if (start != null) {
                BlockPos claimedStart = villageStart(level, claimed);
                if (start.equals(claimedStart)) {
                    return true;
                }
            } else if (claimed.closerThan(pos, radius)) {
                return true;
            }
        }
        if (atlas != null) {
            ServerLevel server = serverLevel(level);
            if (server != null) {
                if (start != null) {
                    for (StationAtlas.Station known : atlas.all()) {
                        if (!known.dimension().equals(server.dimension()) || known.pos().equals(pos)) {
                            continue;
                        }
                        BlockPos otherStart = villageStart(server, known.pos());
                        if (start.equals(otherStart)) {
                            return true;
                        }
                    }
                } else if (atlas.hasStationNear(server.dimension(), pos, radius)) {
                    return true;
                }
            }
        }
        return hasLoadedStationBlock(level, pos, start, villageBox(level, pos), radius, false);
    }

    static boolean hasLoadedStationBlock(
            LevelReader level,
            BlockPos self,
            @Nullable BlockPos villageStart,
            @Nullable BoundingBox villageBox,
            int radius,
            boolean anyPlacedBlock) {
        int minCx;
        int maxCx;
        int minCz;
        int maxCz;
        if (villageBox != null) {
            minCx = villageBox.minX() >> 4;
            maxCx = villageBox.maxX() >> 4;
            minCz = villageBox.minZ() >> 4;
            maxCz = villageBox.maxZ() >> 4;
        } else if (villageStart == null && !anyPlacedBlock) {
            int reach = radius;
            minCx = (self.getX() - reach) >> 4;
            maxCx = (self.getX() + reach) >> 4;
            minCz = (self.getZ() - reach) >> 4;
            maxCz = (self.getZ() + reach) >> 4;
        } else {
            return false;
        }
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.EMPTY, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    if (bePos.equals(self) || !chunk.getBlockState(bePos).is(ModBlocks.POST_STATION.get())) {
                        continue;
                    }
                    if (!anyPlacedBlock) {
                        if (!(level.getBlockEntity(bePos) instanceof PostStationBlockEntity other)
                                || other.stationId() == null) {
                            continue;
                        }
                    }
                    if (villageStart != null) {
                        BlockPos otherStart = villageStart(level, bePos);
                        if (villageStart.equals(otherStart)
                                || (villageBox != null && villageBox.isInside(bePos))) {
                            return true;
                        }
                        continue;
                    }
                    if (villageBox != null && villageBox.isInside(bePos)) {
                        return true;
                    }
                    if (bePos.closerThan(self, radius)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static @Nullable BlockPos villageStart(LevelReader level, BlockPos pos) {
        StructureStart start = villageStructureStart(level, pos);
        if (start == null) {
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        return new BlockPos(box.minX(), box.minY(), box.minZ());
    }

    static @Nullable BoundingBox villageBox(LevelReader level, BlockPos pos) {
        StructureStart start = villageStructureStart(level, pos);
        return start == null ? null : start.getBoundingBox();
    }

    static @Nullable StructureStart villageStructureStart(LevelReader level, BlockPos pos) {
        ServerLevel server = serverLevel(level);
        if (server == null) {
            return null;
        }
        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String path : VILLAGE_STRUCTURES) {
            Structure structure = structures.get(ResourceLocation.withDefaultNamespace(path));
            if (structure == null) {
                continue;
            }
            StructureStart start = server.structureManager().getStructureAt(pos, structure);
            if (start != null && start.isValid()) {
                return start;
            }
        }
        return null;
    }

    static @Nullable ServerLevel serverLevel(LevelReader level) {
        if (level instanceof ServerLevel server) {
            return server;
        }
        if (level instanceof WorldGenLevel worldGen) {
            return worldGen.getLevel();
        }
        return null;
    }
}
