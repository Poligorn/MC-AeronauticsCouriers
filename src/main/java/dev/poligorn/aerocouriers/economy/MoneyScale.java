package dev.poligorn.aerocouriers.economy;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import net.neoforged.fml.ModList;

/**
 * Spur figures stay as-is while the Numismatics backend is active. Otherwise every money figure is divided by
 * {@code economy.emerald_value_spurs} (default 8): one emerald is worth that many spurs.
 */
public final class MoneyScale {
    /** Last backend the server reported to this client; {@code null} until the first board/waybill packet. */
    private static volatile Boolean reportedNumismatics;

    private MoneyScale() {
    }

    /** Client side: the server's active backend wins over the local mod list and synced config. */
    public static void reportBackend(boolean numismatics) {
        reportedNumismatics = numismatics;
    }

    public static void clearReportedBackend() {
        reportedNumismatics = null;
    }

    public static boolean numismaticsBackend() {
        boolean loaded = ModList.get().isLoaded("numismatics");
        Boolean reported = reportedNumismatics;
        if (reported != null && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                && net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() == null) {
            return reported && loaded;
        }
        if (!CouriersServerConfig.SPEC.isLoaded()) {
            return loaded;
        }
        return EconomyBackendResolver.usesNumismaticsBackend(CouriersServerConfig.ECONOMY_BACKEND.get(), loaded);
    }

    /** 1 on the Numismatics backend, otherwise {@code emerald_value_spurs}. */
    public static long divisor() {
        if (numismaticsBackend()) {
            return 1L;
        }
        int raw = CouriersServerConfig.SPEC.isLoaded() ? CouriersServerConfig.EMERALD_VALUE_SPURS.get() : 8;
        return Math.max(1, raw);
    }

    /**
     * Spur amount → active currency. Zero stays zero. A positive amount rounds half-up and is at least 1 when the
     * divisor is greater than 1, so a cheap item line does not become free.
     */
    public static long fromSpurs(long spurs) {
        if (spurs <= 0) {
            return 0L;
        }
        long divisor = divisor();
        if (divisor <= 1L) {
            return spurs;
        }
        return Math.max(1L, Math.round(spurs / (double) divisor));
    }

    public static long fromSpurs(double spurs) {
        if (spurs <= 0.0) {
            return 0L;
        }
        long divisor = divisor();
        if (divisor <= 1L) {
            return Math.max(1L, Math.round(spurs));
        }
        return Math.max(1L, Math.round(spurs / divisor));
    }

    /** Active-currency amount → spur units stored in item value tables. */
    public static long toSpurs(long currency) {
        if (currency <= 0) {
            return 0L;
        }
        long divisor = divisor();
        if (divisor <= 1L) {
            return currency;
        }
        if (currency > Long.MAX_VALUE / divisor) {
            return Long.MAX_VALUE;
        }
        return currency * divisor;
    }
}
