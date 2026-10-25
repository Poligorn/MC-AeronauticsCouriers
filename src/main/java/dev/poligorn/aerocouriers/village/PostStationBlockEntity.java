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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Station id and name (GDD §6.0). A station that has neither (fresh from worldgen or placed by an operator) gets
 * them on its first server load and registers itself in the {@link StationAtlas}.
 *
 * <p>Village stations carry {@code village: 1b} from the structure file. The jigsaw can put several into one
 * village, so a fresh village station within {@link #VILLAGE_SPACING} of a known station turns into a cartography
 * table instead. Operator-placed stations are never replaced.
 */
public class PostStationBlockEntity extends BlockEntity {
    /** Villages are at least 8 chunks apart (vanilla structure set), and their pieces stay within ~80 blocks. */
    public static final int VILLAGE_SPACING = 128;

    private @Nullable UUID stationId;
    private String name = "";
    private boolean village;

    public PostStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POST_STATION.get(), pos, state);
    }

    public @Nullable UUID stationId() {
        return stationId;
    }

    public String name() {
        return name;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StationAtlas atlas = StationAtlas.get(serverLevel.getServer());
        if (stationId == null && village
                && atlas.hasStationNear(serverLevel.dimension(), worldPosition, VILLAGE_SPACING)) {
            MinecraftServer server = serverLevel.getServer();
            BlockPos pos = worldPosition;
            server.tell(new TickTask(server.getTickCount(), () -> {
                if (serverLevel.isLoaded(pos) && serverLevel.getBlockEntity(pos) == this) {
                    serverLevel.setBlock(pos, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }));
            return;
        }
        if (stationId == null) {
            stationId = UUID.randomUUID();
            setChanged();
        }
        if (name.isEmpty()) {
            name = StationNames.pick(serverLevel.getRandom(), atlas.names());
            setChanged();
        }
        atlas.register(stationId, name, serverLevel.dimension(), worldPosition);
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
