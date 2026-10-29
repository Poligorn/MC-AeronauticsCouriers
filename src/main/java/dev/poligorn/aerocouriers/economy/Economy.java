package dev.poligorn.aerocouriers.economy;

import java.util.UUID;

/**
 * The only way the mod moves money (GDD §14.2). Amounts are in the active currency: Numismatics spurs, or emeralds
 * when that backend is off ({@link MoneyScale}). Implementations: Numismatics bank accounts or {@link EmeraldEconomy}.
 */
public interface Economy {
    long balance(UUID player);

    /**
     * Atomically takes {@code spurs}; returns false and changes nothing if the balance is insufficient.
     *
     * @param reason short machine-readable cause for {@code transactions.log}, e.g. {@code collateral:<contract>}
     */
    boolean withdraw(UUID player, long spurs, String reason);

    void deposit(UUID player, long spurs, String reason);
}
