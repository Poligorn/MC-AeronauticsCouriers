package dev.poligorn.aerocouriers.economy;

import java.util.UUID;

/**
 * The only way the mod moves money (GDD §14.2). Amounts are in spurs. Implementations: Numismatics bank accounts
 * (optional bridge, loaded only when the mod is present) or the mod's internal balance.
 */
public interface Economy {
    long balance(UUID player);

    /** Atomically takes {@code spurs}; returns false and changes nothing if the balance is insufficient. */
    boolean withdraw(UUID player, long spurs);

    void deposit(UUID player, long spurs);
}
