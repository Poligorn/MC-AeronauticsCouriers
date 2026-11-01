package dev.poligorn.aerocouriers.village;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Short-lived “this player just placed this station” window so the placement naming packet
 * does not require operator rights. Later renames stay admin-only.
 */
public final class StationNamePending {
    public static final int WINDOW_TICKS = 20 * 60 * 5;

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Claim(UUID player, long expiresAt) {
    }

    private static final Map<Key, Claim> PENDING = new ConcurrentHashMap<>();

    private StationNamePending() {
    }

    public static void remember(ServerPlayer player, BlockPos pos) {
        PENDING.put(new Key(player.level().dimension(), pos.immutable()),
                new Claim(player.getUUID(), player.level().getGameTime() + WINDOW_TICKS));
    }

    public static boolean canName(ServerPlayer player, BlockPos pos) {
        Key key = new Key(player.level().dimension(), pos.immutable());
        Claim claim = PENDING.get(key);
        if (claim == null) {
            return false;
        }
        if (player.level().getGameTime() > claim.expiresAt()) {
            PENDING.remove(key, claim);
            return false;
        }
        return claim.player().equals(player.getUUID());
    }

    public static boolean isPending(ServerLevel level, BlockPos pos) {
        Key key = new Key(level.dimension(), pos.immutable());
        Claim claim = PENDING.get(key);
        if (claim == null) {
            return false;
        }
        if (level.getGameTime() > claim.expiresAt()) {
            PENDING.remove(key, claim);
            return false;
        }
        return true;
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        PENDING.remove(new Key(level.dimension(), pos.immutable()));
    }

    /** GameTest / server-stop helper. */
    public static void clear() {
        PENDING.clear();
    }
}
