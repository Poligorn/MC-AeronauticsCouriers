package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Station id and name (GDD §6.0). Village stations defer registration by one tick so every house piece of the
 * same village can see the atlas (and each other) before claiming a slot. A later village station of the same
 * village start, or within {@code stations.village_dedup_radius} when there is no start, becomes a cartography
 * table. Operator-placed stations ({@code village=false}) are never replaced. New chunks also run
 * {@link OncePerVillageStationProcessor} so copies never save as {@code post_station}.
 */
public class PostStationBlockEntity extends BlockEntity implements Nameable {

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

    static boolean isPlacingVillage() {
        return Boolean.TRUE.equals(PLACING_VILLAGE.get());
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
            // Old 1-block worlds: grow the upper half without touching this BE.
            PostStationBlock.tryPlaceUpperHalf(serverLevel, worldPosition);
            return;
        }
        if (pendingDedup) {
            return;
        }
        pendingDedup = true;
        MinecraftServer server = serverLevel.getServer();
        BlockPos pos = worldPosition.immutable();
        // Next tick: every village house of this generation pass is in the world, and tests can markVillage().
        server.tell(new TickTask(server.getTickCount() + 1, () -> finishFirstLoad(serverLevel, pos)));
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
        if (village && VillageStationDedup.isDuplicateAtLoad(serverLevel, pos, atlas)) {
            serverLevel.setBlock(pos, VillageStationDedup.cartographyTable(), Block.UPDATE_ALL);
            return;
        }
        stationId = UUID.randomUUID();
        if (name.isEmpty()) {
            name = StationNames.pick(serverLevel.getRandom(), atlas.names());
        }
        setChanged();
        atlas.register(stationId, name, serverLevel.dimension(), pos);
        if (village) {
            VillageStationDedup.claim(serverLevel, pos);
        }
        PostStationBlock.tryPlaceUpperHalf(serverLevel, pos);
    }

    static boolean isDuplicateVillageStation(ServerLevel level, StationAtlas atlas, BlockPos pos) {
        return VillageStationDedup.isDuplicateAtLoad(level, pos, atlas);
    }

    static @Nullable BlockPos villageStart(ServerLevel level, BlockPos pos) {
        return VillageStationDedup.villageStart(level, pos);
    }

    @Override
    public Component getName() {
        if (!name.isEmpty()) {
            return Component.literal(name);
        }
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable Component getCustomName() {
        return name.isEmpty() ? null : Component.literal(name);
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
