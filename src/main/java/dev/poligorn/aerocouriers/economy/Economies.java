package dev.poligorn.aerocouriers.economy;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.economy.EconomyBackendResolver.Choice;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * Picks the economy backend ({@code aerocouriers-server.toml} {@code [economy] backend}). Numismatics integration
 * is loaded via reflection so the mod runs without Create: Numismatics on the classpath at runtime.
 */
public final class Economies {
    private static final String NUMISMATICS_MOD_ID = "numismatics";
    private static final String NUMISMATICS_ECONOMY_CLASS =
            "dev.poligorn.aerocouriers.compat.numismatics.NumismaticsEconomy";

    private static MinecraftServer cachedServer;
    private static Economy cached;
    private static boolean cachedNumismatics;

    private Economies() {
    }

    public static synchronized Economy get(MinecraftServer server) {
        if (server != cachedServer) {
            rebuild(server);
        }
        return cached;
    }

    public static synchronized boolean usesNumismatics(MinecraftServer server) {
        if (server != cachedServer) {
            rebuild(server);
        }
        return cachedNumismatics;
    }

    private static void rebuild(MinecraftServer server) {
        cachedServer = server;
        InternalEconomy internal = InternalEconomy.get(server);
        TransactionLog log = TransactionLog.forServer(server);

        String backendConfig = CouriersServerConfig.SPEC.isLoaded()
                ? CouriersServerConfig.ECONOMY_BACKEND.get()
                : "auto";
        boolean numismaticsLoaded = ModList.get().isLoaded(NUMISMATICS_MOD_ID);
        if (EconomyBackendResolver.forcedNumismaticsMissing(backendConfig, numismaticsLoaded)) {
            AeronauticsCouriers.LOGGER.warn(
                    "economy.backend=numismatics but mod '{}' is not loaded; using internal balance",
                    NUMISMATICS_MOD_ID);
        }

        Choice choice = EconomyBackendResolver.resolve(backendConfig, numismaticsLoaded);
        Economy core;
        if (choice == Choice.NUMISMATICS) {
            Economy numismatics = loadNumismaticsEconomy(server, internal);
            core = numismatics == internal ? internal : new MigratingEconomy(numismatics, internal, log);
            cachedNumismatics = numismatics != internal;
        } else {
            core = internal;
            cachedNumismatics = false;
        }
        cached = new LoggedEconomy(core, log);
    }

    private static Economy loadNumismaticsEconomy(MinecraftServer server, InternalEconomy fallback) {
        try {
            Class<?> bridge = Class.forName(NUMISMATICS_ECONOMY_CLASS);
            Object instance = bridge.getMethod("create").invoke(null);
            if (instance instanceof Economy economy) {
                return economy;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            AeronauticsCouriers.LOGGER.error("Failed to load Numismatics economy bridge; using internal balance", e);
        }
        return fallback;
    }
}
