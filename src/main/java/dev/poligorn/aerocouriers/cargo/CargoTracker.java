package dev.poligorn.aerocouriers.cargo;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * In-memory "last known position" of every loaded container (GDD §12: map marker when the chunk unloads).
 * Not persisted: rebuilt as block entities tick after a restart.
 */
public final class CargoTracker {
    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private CargoTracker() {
    }

    public static void report(UUID contractId, ServerLevel level, BlockPos plotPos, Vector3d position,
                              Vector3d velocity, boolean debug) {
        SNAPSHOTS.put(contractId, new Snapshot(contractId, level.dimension(), plotPos.immutable(), new Vector3d(position),
                new Vector3d(velocity), level.getGameTime(), debug));
    }

    public static Optional<Snapshot> get(UUID contractId) {
        return Optional.ofNullable(SNAPSHOTS.get(contractId));
    }

    public static void forget(UUID contractId) {
        SNAPSHOTS.remove(contractId);
    }

    public static Collection<Snapshot> all() {
        return Collections.unmodifiableCollection(SNAPSHOTS.values());
    }

    public static void clear() {
        SNAPSHOTS.clear();
    }

    /** @param plotPos the block position the container occupies (a Sable plot coordinate while assembled) */
    public record Snapshot(UUID contractId, ResourceKey<Level> dimension, BlockPos plotPos, Vector3dc position,
                           Vector3dc velocity, long gameTime, boolean debug) {
    }
}
