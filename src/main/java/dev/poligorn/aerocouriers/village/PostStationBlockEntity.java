package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

/**
 * Station id and name (GDD §6.0). Village stations defer registration by one tick so every house piece of the
 * same village can see the atlas (and each other) before claiming a slot. A later village station inside the
 * same village start, or within {@code stations.village_dedup_radius}, becomes a cartography table.
 * Operator-placed stations are never replaced.
 */
public class PostStationBlockEntity extends BlockEntity {
    private static final String[] VILLAGE_STRUCTURES = {
            "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"
    };

    private static final ThreadLocal<Boolean> PLACING_VILLAGE = ThreadLocal.withInitial(() -> false);

    private @Nullable UUID stationId;
    private String name = "";
    private boolean village;
    private boolean pendingDedup;

    public PostStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POST_STATION.get(), pos, state);
    }

    public @Nullable UUID stationId() {
        return stationId;
    }

    public String name() {
        return name;
    }

    public boolean village() {
        return village;
    }

    /** Test / worldgen helper: the next station placed on this thread is treated as village-generated. */
    public static void placingVillage(Runnable place) {
        PLACING_VILLAGE.set(true);
        try {
            place.run();
        } finally {
            PLACING_VILLAGE.set(false);
        }
    }

    /** Test helper: mark a just-placed station as village-generated before the deferred dedup runs. */
    public void markVillage() {
        village = true;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (PLACING_VILLAGE.get()) {
            village = true;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StationAtlas atlas = StationAtlas.get(serverLevel.getServer());
        if (stationId != null) {
            if (name.isEmpty()) {
                name = StationNames.pick(serverLevel.getRandom(), atlas.names());
                setChanged();
            }
            atlas.register(stationId, name, serverLevel.dimension(), worldPosition);
            return;
        }
        if (pendingDedup) {
            return;
        }
        pendingDedup = true;
        MinecraftServer server = serverLevel.getServer();
        BlockPos pos = worldPosition.immutable();
        server.tell(new TickTask(server.getTickCount(), () -> finishFirstLoad(serverLevel, pos)));
    }

    private void finishFirstLoad(ServerLevel serverLevel, BlockPos pos) {
        pendingDedup = false;
        if (!serverLevel.isLoaded(pos) || serverLevel.getBlockEntity(pos) != this) {
            return;
        }
        if (stationId != null) {
            return;
        }
        StationAtlas atlas = StationAtlas.get(serverLevel.getServer());
        int radius = CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.STATION_VILLAGE_DEDUP_RADIUS.get()
                : 96;
        if (village && isDuplicateVillageStation(serverLevel, atlas, pos, radius)) {
            serverLevel.setBlock(pos, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        stationId = UUID.randomUUID();
        if (name.isEmpty()) {
            name = StationNames.pick(serverLevel.getRandom(), atlas.names());
        }
        setChanged();
        atlas.register(stationId, name, serverLevel.dimension(), pos);
    }

    static boolean isDuplicateVillageStation(ServerLevel level, StationAtlas atlas, BlockPos pos, int radius) {
        if (atlas.hasStationNear(level.dimension(), pos, radius)) {
            return true;
        }
        BlockPos villageStart = villageStart(level, pos);
        if (villageStart != null) {
            for (StationAtlas.Station known : atlas.all()) {
                if (!known.dimension().equals(level.dimension())) {
                    continue;
                }
                BlockPos otherStart = villageStart(level, known.pos());
                if (villageStart.equals(otherStart)) {
                    return true;
                }
            }
        }
        int scan = Math.min(radius, 48);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -scan; dx <= scan; dx += 4) {
            for (int dy = -8; dy <= 8; dy += 4) {
                for (int dz = -scan; dz <= scan; dz += 4) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (cursor.equals(pos) || !level.isLoaded(cursor)) {
                        continue;
                    }
                    if (level.getBlockEntity(cursor) instanceof PostStationBlockEntity other
                            && other != level.getBlockEntity(pos)
                            && other.stationId != null
                            && other.worldPosition.closerThan(pos, radius)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static @Nullable BlockPos villageStart(ServerLevel level, BlockPos pos) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String path : VILLAGE_STRUCTURES) {
            Structure structure = structures.get(ResourceLocation.withDefaultNamespace(path));
            if (structure == null) {
                continue;
            }
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start != null && start.isValid()) {
                return new BlockPos(start.getBoundingBox().minX(), start.getBoundingBox().minY(),
                        start.getBoundingBox().minZ());
            }
        }
        return null;
    }

    /** Operator rename (GDD §6.0). */
    public void rename(String newName) {
        name = newName;
        setChanged();
        if (level instanceof ServerLevel serverLevel && stationId != null) {
            StationAtlas.get(serverLevel.getServer()).register(stationId, name, serverLevel.dimension(), worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stationId != null) {
            tag.putUUID("station_id", stationId);
        }
        if (!name.isEmpty()) {
            tag.putString("name", name);
        }
        if (village) {
            tag.putBoolean("village", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stationId = tag.hasUUID("station_id") ? tag.getUUID("station_id") : null;
        name = tag.getString("name");
        village = tag.getBoolean("village");
    }
}
