package dev.poligorn.aerocouriers.cargo;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * The only place that talks to Sable (GDD §14.2, §18). Verified against sable-neoforge 2.0.6 and
 * sable-companion-common 1.6.0 sources; see docs/research/api-verification.md.
 */
public final class SableBridge {
    private SableBridge() {
    }

    /**
     * Turns a single block into its own physics object, exactly like Sable's {@code /sable} shatter command:
     * tracking-point bounds are the block grown by one in every direction.
     *
     * @return the new sub-level id, empty if Sable produced an object without mass (nothing assembled)
     */
    public static Optional<UUID> assembleSingleBlock(ServerLevel level, BlockPos anchor) {
        BoundingBox3i bounds = new BoundingBox3i(
                anchor.getX() - 1, anchor.getY() - 1, anchor.getZ() - 1,
                anchor.getX() + 2, anchor.getY() + 2, anchor.getZ() + 2);
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, List.of(anchor), bounds);
        if (subLevel.getMassTracker().isInvalid()) {
            return Optional.empty();
        }
        return Optional.of(subLevel.getUniqueId());
    }

    /** World coordinates of the block centre; identity when the block is not inside a sub-level. */
    public static Vector3d worldCenter(Level level, BlockPos pos) {
        Vector3d local = center(pos);
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, local, new Vector3d());
    }

    /** Velocity of the block centre in blocks per second; zero when not inside a sub-level. */
    public static Vector3d velocity(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getVelocity(level, center(pos), new Vector3d());
    }

    public static @Nullable UUID containingSubLevel(Level level, BlockPos pos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    public static double distanceSquared(Level level, Vector3d a, Vector3d b) {
        return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, a, b);
    }

    private static Vector3d center(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
