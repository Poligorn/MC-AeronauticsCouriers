package dev.poligorn.aerocouriers.economy;

import java.util.UUID;

/**
 * One-time transfer of {@link InternalEconomy} balances into an external backend (Numismatics bank account).
 * Idempotent: after success the internal balance for the player is zero.
 */
public final class EconomyMigration {
    private EconomyMigration() {
    }

    /**
     * Moves any remaining internal balance to {@code delegate}. Returns spurs moved (0 if nothing to do).
     */
    public static long migratePlayer(UUID player, InternalEconomy internal, Economy delegate, TransactionLog log) {
        long pending = internal.balance(player);
        if (pending <= 0) {
            return 0;
        }
        int spurs = toSpurs(pending);
        if (spurs <= 0) {
            return 0;
        }
        delegate.deposit(player, spurs, "migrate_internal");
        if (!internal.withdraw(player, spurs, "migrate_internal")) {
            delegate.withdraw(player, spurs, "migrate_internal_rollback");
            return 0;
        }
        log.write("migrate", player, spurs, "internal_to_numismatics", delegate.balance(player));
        return spurs;
    }

    public static int toSpurs(long amount) {
        if (amount <= 0) {
            return 0;
        }
        if (amount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) amount;
    }
}
