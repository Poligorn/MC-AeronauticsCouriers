package dev.poligorn.aerocouriers.economy;

import net.minecraft.server.MinecraftServer;

/**
 * Picks the economy backend. Only {@link InternalEconomy} exists so far: the Numismatics bridge waits until its
 * API is checked against the pack's version 1.1.0 (see {@code docs/research/api-verification.md}).
 */
public final class Economies {
    private static MinecraftServer cachedServer;
    private static Economy cached;

    private Economies() {
    }

    public static synchronized Economy get(MinecraftServer server) {
        if (server != cachedServer) {
            cachedServer = server;
            cached = new LoggedEconomy(InternalEconomy.get(server), TransactionLog.forServer(server));
        }
        return cached;
    }
}
