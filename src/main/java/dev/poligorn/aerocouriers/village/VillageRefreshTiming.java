package dev.poligorn.aerocouriers.village;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.OptionalLong;
import net.minecraft.server.MinecraftServer;

/** When {@link StationContractSource} will next run on the server tick loop (GDD §6.2). */
public final class VillageRefreshTiming {
    private VillageRefreshTiming() {
    }

    /** Absolute overworld {@link net.minecraft.world.level.Level#getGameTime()} of the next generation tick. */
    public static OptionalLong nextRefreshGameTime(MinecraftServer server) {
        int intervalTicks = generationIntervalTicks();
        if (intervalTicks <= 0) {
            return OptionalLong.empty();
        }
        long tick = server.getTickCount();
        long ticksUntil = ticksUntilNext(intervalTicks, tick);
        long gameTime = server.overworld().getGameTime();
        return OptionalLong.of(gameTime + ticksUntil);
    }

    public static int generationIntervalTicks() {
        int minutes = CouriersServerConfig.VILLAGE_GENERATION_INTERVAL_MIN.get();
        if (minutes <= 0) {
            return 0;
        }
        return minutes * 60 * 20;
    }

    static long ticksUntilNext(int intervalTicks, long serverTickCount) {
        if (serverTickCount <= 0) {
            return intervalTicks;
        }
        long remainder = serverTickCount % intervalTicks;
        if (remainder == 0) {
            return intervalTicks;
        }
        return intervalTicks - remainder;
    }
}
